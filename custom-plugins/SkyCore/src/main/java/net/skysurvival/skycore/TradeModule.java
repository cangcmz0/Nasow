package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Guvenli takas: /takas <oyuncu> ile istek, /takas kabul ile iki tarafa ayni menu acilir. Herkes kendi tarafina
 * esya koyar, karsi tarafinkini gorur; iki taraf da onaylayinca esyalar degisir. Teklif degisince onaylar sifirlanir
 * ve degisiklikten hemen sonra onay kabul edilmez (son anda esya degistirme dolandiriciligina karsi).
 */
final class TradeModule implements Module, CommandExecutor, TabCompleter {
    static final int SIZE = 54;
    static final int CONFIRM = 45;
    static final int CANCEL = 49;
    static final int PARTNER_STATUS = 53;
    private static final Set<String> ALLOWED_TOP = Set.of("PICKUP_ALL", "PICKUP_HALF", "PICKUP_ONE", "PICKUP_SOME",
            "PLACE_ALL", "PLACE_ONE", "PLACE_SOME", "SWAP_WITH_CURSOR", "MOVE_TO_OTHER_INVENTORY", "HOTBAR_SWAP");

    static final class Trade {
        final Player a;
        final Player b;
        View viewA;
        View viewB;
        boolean readyA;
        boolean readyB;
        boolean done;
        long changedAt;

        Trade(Player a, Player b) {
            this.a = a;
            this.b = b;
        }
    }

    static final class View implements InventoryHolder {
        final Trade trade;
        final Player owner;
        Inventory inventory;

        View(Trade trade, Player owner) {
            this.trade = trade;
            this.owner = owner;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private final SkyCore plugin;
    /** Hedef oyuncu -> (isteyen -> bitis zamani) */
    private final Map<UUID, Map<UUID, Long>> requests = new HashMap<>();
    private final Map<UUID, Trade> active = new HashMap<>();
    private boolean enabled;

    TradeModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("takas.aktif", true);
    }

    @Override
    public void stop() {
        for (Trade trade : new ArrayList<>(active.values())) {
            cancel(trade, "takas.mesajlar.iptal");
        }
        requests.clear();
    }

    static boolean isOwnSlot(int slot) {
        return slot >= 0 && slot < 45 && slot % 9 < 4;
    }

    static int partnerSlot(int ownSlot) {
        return ownSlot + 5;
    }

    // ---- Komut ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (!enabled) {
            messages.send(player, "genel-mesajlar.yetki-yok");
            return true;
        }
        if (args.length == 0) {
            messages.sendLines(player, "takas.mesajlar.yardim");
            return true;
        }
        String arg = args[0].toLowerCase(Locale.ROOT);
        if (arg.equals("kabul")) {
            Player requester = latestRequester(player);
            if (requester == null) {
                messages.send(player, "takas.mesajlar.istek-yok");
            } else {
                begin(requester, player);
            }
            return true;
        }
        if (arg.equals("reddet")) {
            requests.remove(player.getUniqueId());
            messages.send(player, "takas.mesajlar.reddedildi");
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[0]);
        if (target == null || target.equals(player)) {
            messages.send(player, "takas.mesajlar.oyuncu-yok");
            return true;
        }
        if (!canTrade(player, target)) {
            return true;
        }
        // Karsi taraf zaten istek gonderdiyse takas hemen baslar
        Long theirs = requests.getOrDefault(player.getUniqueId(), Map.of()).get(target.getUniqueId());
        if (theirs != null && theirs > System.currentTimeMillis()) {
            begin(target, player);
            return true;
        }
        requests.computeIfAbsent(target.getUniqueId(), id -> new HashMap<>()).put(player.getUniqueId(), System.currentTimeMillis() + 60_000);
        messages.send(player, "takas.mesajlar.istek-gonderildi", "oyuncu", target.getName());
        messages.send(target, "takas.mesajlar.istek-geldi", "oyuncu", player.getName());
        Messages.sound(target, "block.note_block.pling", 1.4f);
        return true;
    }

    private Player latestRequester(Player target) {
        Map<UUID, Long> mine = requests.getOrDefault(target.getUniqueId(), Map.of());
        long now = System.currentTimeMillis();
        Player result = null;
        long best = 0;
        for (Map.Entry<UUID, Long> entry : mine.entrySet()) {
            Player requester = plugin.getServer().getPlayer(entry.getKey());
            if (requester != null && entry.getValue() > now && entry.getValue() > best) {
                best = entry.getValue();
                result = requester;
            }
        }
        return result;
    }

    private boolean canTrade(Player a, Player b) {
        Messages messages = plugin.messages();
        if (active.containsKey(a.getUniqueId()) || active.containsKey(b.getUniqueId())) {
            messages.send(a, "takas.mesajlar.mesgul");
            return false;
        }
        if (plugin.combat().isTagged(a) || plugin.combat().isTagged(b)) {
            messages.send(a, "takas.mesajlar.savasta");
            return false;
        }
        int distance = plugin.getConfig().getInt("takas.max-mesafe", 20);
        if (distance > 0 && (a.getWorld() != b.getWorld() || a.getLocation().distanceSquared(b.getLocation()) > (double) distance * distance)) {
            messages.send(a, "takas.mesajlar.uzak", "mesafe", distance);
            return false;
        }
        return true;
    }

    // ---- Menu ----

    void begin(Player a, Player b) {
        if (!canTrade(b, a)) {
            return;
        }
        requests.remove(a.getUniqueId());
        requests.remove(b.getUniqueId());
        Trade trade = new Trade(a, b);
        trade.viewA = createView(trade, a, b);
        trade.viewB = createView(trade, b, a);
        active.put(a.getUniqueId(), trade);
        active.put(b.getUniqueId(), trade);
        a.openInventory(trade.viewA.inventory);
        b.openInventory(trade.viewB.inventory);
    }

    private View createView(Trade trade, Player owner, Player partner) {
        View view = new View(trade, owner);
        view.inventory = plugin.getServer().createInventory(view, SIZE,
                Messages.color(plugin.messages().raw("takas.mesajlar.baslik", "oyuncu", partner.getName())));
        ItemStack divider = button(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int row = 0; row < 5; row++) {
            view.inventory.setItem(row * 9 + 4, divider);
        }
        for (int slot = 45; slot < SIZE; slot++) {
            view.inventory.setItem(slot, divider);
        }
        view.inventory.setItem(CANCEL, button(Material.BARRIER, plugin.messages().raw("takas.mesajlar.iptal-dugme")));
        refreshButtons(trade);
        return view;
    }

    private static ItemStack button(Material material, String name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(name.isBlank() ? Component.text(" ") : Messages.itemText(name));
        item.setItemMeta(meta);
        return item;
    }

    private void refreshButtons(Trade trade) {
        Messages messages = plugin.messages();
        for (View view : new View[] {trade.viewA, trade.viewB}) {
            if (view == null) {
                continue;
            }
            boolean mine = view.owner.equals(trade.a) ? trade.readyA : trade.readyB;
            boolean theirs = view.owner.equals(trade.a) ? trade.readyB : trade.readyA;
            view.inventory.setItem(CONFIRM, button(mine ? Material.LIME_DYE : Material.GRAY_DYE,
                    messages.raw(mine ? "takas.mesajlar.onaylandi" : "takas.mesajlar.onayla")));
            view.inventory.setItem(PARTNER_STATUS, button(theirs ? Material.LIME_DYE : Material.GRAY_DYE,
                    messages.raw(theirs ? "takas.mesajlar.karsi-onayladi" : "takas.mesajlar.karsi-bekleniyor")));
        }
    }

    /** Bir taraf teklifini degistirdi: onaylar sifirlanir, bir tick sonra karsi tarafa yansir. */
    private void changed(Trade trade) {
        trade.readyA = false;
        trade.readyB = false;
        trade.changedAt = System.currentTimeMillis();
        plugin.getServer().getScheduler().runTask(plugin, () -> sync(trade));
    }

    void sync(Trade trade) {
        if (trade.done) {
            return;
        }
        for (int slot = 0; slot < 45; slot++) {
            if (isOwnSlot(slot)) {
                ItemStack fromA = trade.viewA.inventory.getItem(slot);
                ItemStack fromB = trade.viewB.inventory.getItem(slot);
                trade.viewB.inventory.setItem(partnerSlot(slot), fromA == null ? null : fromA.clone());
                trade.viewA.inventory.setItem(partnerSlot(slot), fromB == null ? null : fromB.clone());
            }
        }
        refreshButtons(trade);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view) || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Trade trade = view.trade;
        if (trade.done) {
            event.setCancelled(true);
            return;
        }
        int raw = event.getRawSlot();
        String action = event.getAction().name();
        if (raw < SIZE) {
            if (raw == CONFIRM) {
                event.setCancelled(true);
                toggleReady(trade, player);
            } else if (raw == CANCEL) {
                event.setCancelled(true);
                cancel(trade, "takas.mesajlar.iptal");
            } else if (isOwnSlot(raw) && ALLOWED_TOP.contains(action)) {
                changed(trade);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        if (action.equals("MOVE_TO_OTHER_INVENTORY")) {
            event.setCancelled(true);
            ItemStack item = event.getCurrentItem();
            if (item != null && !item.getType().isAir()) {
                ItemStack left = addToOwnSlots(view.inventory, item.clone());
                event.setCurrentItem(left);
                changed(trade);
            }
        } else if (action.equals("COLLECT_TO_CURSOR")) {
            event.setCancelled(true); // ust taraftan esya cekebilir
        }
    }

    /** Shift ile alttan konan esyayi kendi tarafindaki yuvalara yerlestirir; sigmayani dondurur. */
    static ItemStack addToOwnSlots(Inventory inventory, ItemStack item) {
        for (int slot = 0; slot < 45 && item.getAmount() > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (isOwnSlot(slot) && current != null && current.isSimilar(item)) {
                int move = Math.min(item.getAmount(), current.getMaxStackSize() - current.getAmount());
                if (move > 0) {
                    current.setAmount(current.getAmount() + move);
                    inventory.setItem(slot, current);
                    item.setAmount(item.getAmount() - move);
                }
            }
        }
        for (int slot = 0; slot < 45 && item.getAmount() > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (isOwnSlot(slot) && (current == null || current.getType().isAir())) {
                inventory.setItem(slot, item.clone());
                item.setAmount(0);
            }
        }
        return item.getAmount() > 0 ? item : null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof View view)) {
            return;
        }
        for (int raw : event.getRawSlots()) {
            if (raw < SIZE && !isOwnSlot(raw)) {
                event.setCancelled(true);
                return;
            }
        }
        if (view.trade.done) {
            event.setCancelled(true);
            return;
        }
        changed(view.trade);
    }

    private void toggleReady(Trade trade, Player player) {
        if (System.currentTimeMillis() - trade.changedAt < 1000) {
            plugin.messages().actionBar(player, "takas.mesajlar.degisti-bekle");
            return;
        }
        if (player.equals(trade.a)) {
            trade.readyA = !trade.readyA;
        } else {
            trade.readyB = !trade.readyB;
        }
        refreshButtons(trade);
        Messages.sound(player, "ui.button.click", 1.0f);
        if (trade.readyA && trade.readyB) {
            complete(trade);
        }
    }

    private static List<ItemStack> takeOffer(View view) {
        List<ItemStack> items = new ArrayList<>();
        for (int slot = 0; slot < 45; slot++) {
            if (isOwnSlot(slot)) {
                ItemStack item = view.inventory.getItem(slot);
                if (item != null && !item.getType().isAir()) {
                    items.add(item);
                }
                view.inventory.setItem(slot, null);
            }
        }
        return items;
    }

    private void finish(Trade trade) {
        trade.done = true;
        active.remove(trade.a.getUniqueId());
        active.remove(trade.b.getUniqueId());
        trade.a.closeInventory();
        trade.b.closeInventory();
    }

    void complete(Trade trade) {
        List<ItemStack> fromA = takeOffer(trade.viewA);
        List<ItemStack> fromB = takeOffer(trade.viewB);
        finish(trade);
        fromB.forEach(item -> CrateModule.giveItem(trade.a, item));
        fromA.forEach(item -> CrateModule.giveItem(trade.b, item));
        Messages messages = plugin.messages();
        messages.send(trade.a, "takas.mesajlar.tamamlandi", "oyuncu", trade.b.getName());
        messages.send(trade.b, "takas.mesajlar.tamamlandi", "oyuncu", trade.a.getName());
        Messages.sound(trade.a, "entity.villager.yes", 1.0f);
        Messages.sound(trade.b, "entity.villager.yes", 1.0f);
        plugin.getLogger().info("Takas: " + trade.a.getName() + " " + describe(fromA) + " <-> " + trade.b.getName() + " " + describe(fromB));
    }

    private static String describe(List<ItemStack> items) {
        List<String> parts = new ArrayList<>();
        for (ItemStack item : items) {
            parts.add(item.getAmount() + "x" + item.getType().getKey().getKey());
        }
        return parts.toString();
    }

    /** Takas iptal: herkes kendi koydugunu geri alir. */
    void cancel(Trade trade, String messagePath) {
        if (trade.done) {
            return;
        }
        List<ItemStack> backA = takeOffer(trade.viewA);
        List<ItemStack> backB = takeOffer(trade.viewB);
        finish(trade);
        backA.forEach(item -> CrateModule.giveItem(trade.a, item));
        backB.forEach(item -> CrateModule.giveItem(trade.b, item));
        plugin.messages().send(trade.a, messagePath);
        plugin.messages().send(trade.b, messagePath);
    }

    /** Takastayken hasar alan oyuncunun takasi iptal olur (savas sirasinda esya saklanamasin). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && active.containsKey(player.getUniqueId())) {
            cancel(active.get(player.getUniqueId()), "takas.mesajlar.iptal-hasar");
        }
    }

    /** Takastayken olen oyuncunun teklifi envanteri gibi yere duser (olum sirasinda esya saklama hilesi). */
    @EventHandler(priority = EventPriority.LOWEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        Trade trade = active.get(player.getUniqueId());
        if (trade == null || trade.done) {
            return;
        }
        List<ItemStack> offer = takeOffer(player.equals(trade.a) ? trade.viewA : trade.viewB);
        if (event.getKeepInventory()) {
            offer.forEach(item -> CrateModule.giveItem(player, item));
        } else {
            event.getDrops().addAll(offer);
        }
        cancel(trade, "takas.mesajlar.iptal");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Trade trade = active.get(event.getPlayer().getUniqueId());
        if (trade != null) {
            cancel(trade, "takas.mesajlar.iptal");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof View view && !view.trade.done) {
            cancel(view.trade, "takas.mesajlar.iptal");
        }
    }

    /** Testler icin. */
    Trade tradeOf(Player player) {
        return active.get(player.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("kabul", "reddet")) {
                if (option.startsWith(prefix)) {
                    result.add(option);
                }
            }
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (!online.equals(sender) && online.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    result.add(online.getName());
                }
            }
        }
        return result;
    }
}
