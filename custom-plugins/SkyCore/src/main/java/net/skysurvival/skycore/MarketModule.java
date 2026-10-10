package net.skysurvival.skycore;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;

/**
 * Sunucu marketi: /market ile kategorili menu, sol tik al, sag tik sat. /sat ile elindekini ya da hepsini sat.
 * Fiyatlar plugins/SkyCore/market.yml dosyasinda.
 */
final class MarketModule implements Module, CommandExecutor, TabCompleter {
    record Offer(Material material, double buy, double sell) {}

    record Category(String id, String name, Material icon, int slot, List<Offer> offers) {}

    /** Market menusu; kategori null ise ana menu. */
    static final class MarketMenu implements InventoryHolder {
        final Category category;
        final int page;
        Inventory inventory;

        MarketMenu(Category category, int page) {
            this.category = category;
            this.page = page;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int PAGE_SIZE = 45;

    private final SkyCore plugin;
    private final Map<String, Category> categories = new LinkedHashMap<>();
    private final Map<Material, Offer> offers = new LinkedHashMap<>();
    private boolean enabled;

    MarketModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("market.aktif", true);
        File file = new File(plugin.getDataFolder(), "market.yml");
        if (!file.exists()) {
            plugin.saveResource("market.yml", false);
        }
        load(YamlConfiguration.loadConfiguration(file));
    }

    @Override
    public void stop() {
    }

    void load(YamlConfiguration yaml) {
        categories.clear();
        offers.clear();
        ConfigurationSection root = yaml.getConfigurationSection("kategoriler");
        if (root == null) {
            return;
        }
        int nextSlot = 10;
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }
            List<Offer> list = new ArrayList<>();
            for (String line : section.getStringList("esyalar")) {
                Offer offer = parseOffer(id, line);
                if (offer != null && !offers.containsKey(offer.material())) {
                    list.add(offer);
                    offers.put(offer.material(), offer);
                }
            }
            Material icon = Material.matchMaterial(section.getString("simge", "chest"));
            int slot = section.getInt("slot", nextSlot);
            nextSlot = slot + 1;
            categories.put(id, new Category(id, section.getString("isim", id), icon == null ? Material.CHEST : icon,
                    Math.max(0, Math.min(26, slot)), list));
        }
    }

    /** "oak_log 8 2" -> esya, alis, satis. Satis alistan yuksekse yariya indirilir (al-sat ile para basilmasin). */
    Offer parseOffer(String category, String line) {
        String[] parts = line.trim().split("\\s+");
        Material material = parts.length == 3 ? Material.matchMaterial(parts[0]) : null;
        if (material == null || !material.isItem() || material.isAir()) {
            plugin.getLogger().warning("market.yml (" + category + "): gecersiz satir atlandi: " + line);
            return null;
        }
        try {
            double buy = Double.parseDouble(parts[1]);
            double sell = Double.parseDouble(parts[2]);
            if (!Double.isFinite(buy) || !Double.isFinite(sell)) {
                throw new NumberFormatException("sayi degil");
            }
            buy = Math.max(0, buy);
            sell = Math.max(0, sell);
            if (buy > 0 && sell >= buy) {
                plugin.getLogger().warning("market.yml: " + parts[0] + " satis fiyati alistan yuksek, yariya indirildi.");
                sell = buy / 2;
            }
            if (material == Material.EMERALD && sell > 0) {
                plugin.getLogger().warning("market.yml: zumrut satilabiliyor. Koylu takasiyla (cubuk -> zumrut) bedava para"
                        + " basilabilir; satis fiyatini 0 yapmaniz onerilir.");
            }
            return new Offer(material, buy, sell);
        } catch (NumberFormatException e) {
            plugin.getLogger().warning("market.yml (" + category + "): fiyat okunamadi: " + line);
            return null;
        }
    }

    Offer offer(Material material) {
        return offers.get(material);
    }

    // ---- Komutlar ----

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
        if (!plugin.economy().available()) {
            messages.send(player, "genel-mesajlar.ekonomi-yok");
            return true;
        }
        if (command.getName().equals("sat")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("hepsi")) {
                sellAll(player);
            } else if (args.length == 0) {
                sellHand(player);
            } else {
                messages.send(player, "market.mesajlar.sat-kullanim");
            }
            return true;
        }
        if (args.length > 0 && categories.containsKey(args[0].toLowerCase(Locale.ROOT))) {
            openCategory(player, categories.get(args[0].toLowerCase(Locale.ROOT)), 0);
        } else {
            openMain(player);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            List<String> options = command.getName().equals("sat") ? List.of("hepsi") : new ArrayList<>(categories.keySet());
            for (String option : options) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
        }
        return result;
    }

    // ---- Menuler ----

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.itemText(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String line : lore) {
                lines.add(Messages.itemText(line));
            }
            meta.lore(lines);
        }
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack balanceButton(Player player) {
        return button(Material.GOLD_INGOT, plugin.messages().raw("market.mesajlar.bakiye",
                "bakiye", plugin.economy().format(balance(player))), null);
    }

    private double balance(Player player) {
        return plugin.economy().balance(player);
    }

    void openMain(Player player) {
        Messages messages = plugin.messages();
        MarketMenu menu = new MarketMenu(null, 0);
        menu.inventory = plugin.getServer().createInventory(menu, 27, Messages.color(messages.raw("market.mesajlar.ana-baslik")));
        ItemStack glass = button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < 27; i++) {
            menu.inventory.setItem(i, glass);
        }
        for (Category category : categories.values()) {
            menu.inventory.setItem(category.slot(), button(category.icon(), category.name(),
                    messages.rawList("market.mesajlar.kategori-aciklama", "adet", category.offers().size())));
        }
        menu.inventory.setItem(22, balanceButton(player));
        player.openInventory(menu.inventory);
    }

    void openCategory(Player player, Category category, int page) {
        Messages messages = plugin.messages();
        int pages = Math.max(1, (category.offers().size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int current = Math.max(0, Math.min(page, pages - 1));
        MarketMenu menu = new MarketMenu(category, current);
        menu.inventory = plugin.getServer().createInventory(menu, 54, Messages.color(messages.raw("market.mesajlar.kategori-baslik",
                "kategori", category.name(), "sayfa", current + 1, "toplam", pages)));
        List<Offer> list = category.offers();
        for (int i = 0; i < PAGE_SIZE && current * PAGE_SIZE + i < list.size(); i++) {
            menu.inventory.setItem(i, offerIcon(list.get(current * PAGE_SIZE + i)));
        }
        ItemStack glass = button(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 45; i < 54; i++) {
            menu.inventory.setItem(i, glass);
        }
        menu.inventory.setItem(45, button(Material.ARROW, messages.raw("market.mesajlar.geri"), null));
        if (current > 0) {
            menu.inventory.setItem(48, button(Material.PAPER, messages.raw("market.mesajlar.onceki"), null));
        }
        menu.inventory.setItem(49, balanceButton(player));
        if (current < pages - 1) {
            menu.inventory.setItem(50, button(Material.PAPER, messages.raw("market.mesajlar.sonraki"), null));
        }
        menu.inventory.setItem(53, button(Material.BARRIER, messages.raw("market.mesajlar.kapat"), null));
        player.openInventory(menu.inventory);
    }

    private ItemStack offerIcon(Offer offer) {
        Messages messages = plugin.messages();
        ItemStack item = new ItemStack(offer.material());
        int stack = item.getMaxStackSize();
        List<String> lore = new ArrayList<>();
        lore.add(offer.buy() > 0
                ? messages.raw("market.mesajlar.aciklama-al", "fiyat", plugin.economy().format(offer.buy()),
                        "fiyat-yigin", plugin.economy().format(offer.buy() * stack), "yigin", stack)
                : messages.raw("market.mesajlar.aciklama-alinamaz"));
        lore.add(offer.sell() > 0
                ? messages.raw("market.mesajlar.aciklama-sat", "fiyat", plugin.economy().format(offer.sell()))
                : messages.raw("market.mesajlar.aciklama-satilamaz"));
        lore.addAll(messages.rawList("market.mesajlar.aciklama-tiklar", "yigin", stack));
        ItemMeta meta = item.getItemMeta();
        List<Component> lines = new ArrayList<>();
        for (String line : lore) {
            lines.add(Messages.itemText(line));
        }
        meta.lore(lines);
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MarketMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }
        int slot = event.getRawSlot();
        if (menu.category == null) {
            for (Category category : categories.values()) {
                if (category.slot() == slot) {
                    Messages.sound(player, "ui.button.click", 1.0f);
                    openCategory(player, category, 0);
                    return;
                }
            }
            return;
        }
        switch (slot) {
            case 45 -> openMain(player);
            case 48 -> openCategory(player, menu.category, menu.page - 1);
            case 50 -> openCategory(player, menu.category, menu.page + 1);
            case 53 -> player.closeInventory();
            default -> {
                int index = menu.page * PAGE_SIZE + slot;
                if (slot >= PAGE_SIZE || index >= menu.category.offers().size()) {
                    return;
                }
                Offer offer = menu.category.offers().get(index);
                ClickType click = event.getClick();
                int stack = new ItemStack(offer.material()).getMaxStackSize();
                if (click == ClickType.LEFT) {
                    buy(player, offer, 1);
                } else if (click == ClickType.SHIFT_LEFT) {
                    buy(player, offer, stack);
                } else if (click == ClickType.RIGHT) {
                    sell(player, offer, 1);
                } else if (click == ClickType.SHIFT_RIGHT) {
                    sell(player, offer, Integer.MAX_VALUE);
                } else {
                    return;
                }
                event.getView().getTopInventory().setItem(49, balanceButton(player));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MarketMenu) {
            event.setCancelled(true);
        }
    }

    // ---- Alim satim ----

    /** Envanterde bu esyadan kac tane daha yer var. */
    static int space(PlayerInventory inventory, Material material) {
        ItemStack sample = new ItemStack(material);
        int max = sample.getMaxStackSize();
        int free = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item == null || item.getType().isAir()) {
                free += max;
            } else if (item.isSimilar(sample)) {
                free += Math.max(0, max - item.getAmount());
            }
        }
        return free;
    }

    /** Satilabilir (adi/buyusu/hasari olmayan) esya sayisi. */
    static int count(PlayerInventory inventory, Material material) {
        ItemStack sample = new ItemStack(material);
        int total = 0;
        for (ItemStack item : inventory.getStorageContents()) {
            if (item != null && item.isSimilar(sample)) {
                total += item.getAmount();
            }
        }
        return total;
    }

    static void remove(PlayerInventory inventory, Material material, int amount) {
        ItemStack sample = new ItemStack(material);
        ItemStack[] contents = inventory.getStorageContents();
        for (int i = 0; i < contents.length && amount > 0; i++) {
            ItemStack item = contents[i];
            if (item != null && item.isSimilar(sample)) {
                int take = Math.min(amount, item.getAmount());
                amount -= take;
                if (take >= item.getAmount()) {
                    contents[i] = null;
                } else {
                    item.setAmount(item.getAmount() - take);
                }
            }
        }
        inventory.setStorageContents(contents);
    }

    boolean buy(Player player, Offer offer, int amount) {
        Messages messages = plugin.messages();
        if (offer.buy() <= 0) {
            messages.send(player, "market.mesajlar.alinamaz");
            return false;
        }
        int fits = Math.min(amount, space(player.getInventory(), offer.material()));
        if (fits <= 0) {
            messages.send(player, "market.mesajlar.yer-yok");
            Messages.sound(player, "entity.villager.no", 1.0f);
            return false;
        }
        double price = round(offer.buy() * fits);
        if (!plugin.economy().withdraw(player, price)) {
            messages.send(player, "market.mesajlar.para-yok", "fiyat", plugin.economy().format(price));
            Messages.sound(player, "entity.villager.no", 1.0f);
            return false;
        }
        CrateModule.giveItems(player, new ItemStack(offer.material()), fits);
        messages.send(player, "market.mesajlar.aldin", "adet", fits, "esya", Messages.item(offer.material()),
                "fiyat", plugin.economy().format(price));
        Messages.sound(player, "entity.experience_orb.pickup", 1.2f);
        return true;
    }

    boolean sell(Player player, Offer offer, int amount) {
        Messages messages = plugin.messages();
        if (offer.sell() <= 0) {
            messages.send(player, "market.mesajlar.satilamaz");
            return false;
        }
        int have = Math.min(amount, count(player.getInventory(), offer.material()));
        if (have <= 0) {
            messages.send(player, "market.mesajlar.esya-yok", "esya", Messages.item(offer.material()));
            return false;
        }
        double price = round(offer.sell() * have);
        remove(player.getInventory(), offer.material(), have);
        if (!plugin.economy().deposit(player, price)) {
            CrateModule.giveItems(player, new ItemStack(offer.material()), have); // ekonomi yoksa geri ver
            messages.send(player, "genel-mesajlar.ekonomi-yok");
            return false;
        }
        messages.send(player, "market.mesajlar.sattin", "adet", have, "esya", Messages.item(offer.material()),
                "fiyat", plugin.economy().format(price));
        Messages.sound(player, "entity.experience_orb.pickup", 0.9f);
        return true;
    }

    private void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        Offer offer = hand.getType().isAir() ? null : offers.get(hand.getType());
        if (offer == null || offer.sell() <= 0 || !hand.isSimilar(new ItemStack(hand.getType()))) {
            plugin.messages().send(player, "market.mesajlar.satilamaz");
            return;
        }
        double price = round(offer.sell() * hand.getAmount());
        int amount = hand.getAmount();
        player.getInventory().setItemInMainHand(null);
        if (!plugin.economy().deposit(player, price)) {
            player.getInventory().setItemInMainHand(hand);
            plugin.messages().send(player, "genel-mesajlar.ekonomi-yok");
            return;
        }
        plugin.messages().send(player, "market.mesajlar.sattin", "adet", amount, "esya", Messages.item(offer.material()),
                "fiyat", plugin.economy().format(price));
        Messages.sound(player, "entity.experience_orb.pickup", 0.9f);
    }

    private void sellAll(Player player) {
        PlayerInventory inventory = player.getInventory();
        double total = 0;
        int items = 0;
        for (Offer offer : offers.values()) {
            if (offer.sell() <= 0) {
                continue;
            }
            int have = count(inventory, offer.material());
            if (have > 0) {
                remove(inventory, offer.material(), have);
                total += offer.sell() * have;
                items += have;
            }
        }
        if (items == 0) {
            plugin.messages().send(player, "market.mesajlar.sat-hicbiri");
            return;
        }
        total = round(total);
        plugin.economy().deposit(player, total);
        plugin.messages().send(player, "market.mesajlar.sat-hepsi", "adet", items, "fiyat", plugin.economy().format(total));
        Messages.sound(player, "entity.experience_orb.pickup", 0.9f);
    }

    private static double round(double value) {
        return Math.round(value * 100) / 100.0;
    }
}
