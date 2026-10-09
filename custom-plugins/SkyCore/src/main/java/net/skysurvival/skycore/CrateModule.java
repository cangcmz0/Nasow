package net.skysurvival.skycore;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Kasalar: spawn adasindaki kasalara anahtarla sag tiklayinca donen bir carkla odul cikar; sol tik odulleri gosterir.
 * Anahtarlar gunluk odulden, aktiflik odulunden ve KOTH'tan gelir (ya da /skycore anahtar).
 */
final class CrateModule implements Module {
    /** Bir kasadaki tek odul. */
    record Reward(String kind, double money, ItemStack item, String keyType, int amount, double chance, String label) {}

    record CrateType(String id, String name, String keyName, List<Reward> rewards, double totalChance) {}

    /** Acilis carki ya da odul onizlemesi; envanterin sahibi. */
    static final class CrateMenu implements InventoryHolder {
        final boolean preview;
        Inventory inventory;
        Player player;
        Reward reward;
        CrateType type;
        BukkitTask task;
        boolean done;

        CrateMenu(boolean preview) {
            this.preview = preview;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }

    private static final int[] DELAYS = buildDelays();

    private final SkyCore plugin;
    private final NamespacedKey keyTag;
    private final Map<String, CrateType> types = new LinkedHashMap<>();
    private final Map<UUID, CrateMenu> spinning = new HashMap<>();
    private boolean enabled;
    private double announceChance;
    private BukkitTask particleTask;

    CrateModule(SkyCore plugin) {
        this.plugin = plugin;
        this.keyTag = new NamespacedKey(plugin, "kasa-anahtari");
    }

    /** Cark her adimda bir kayar; basta hizli, sonda yavas (toplam ~3,5 sn). */
    private static int[] buildDelays() {
        List<Integer> delays = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            delays.add(1);
        }
        for (int i = 0; i < 8; i++) {
            delays.add(2);
        }
        for (int i = 0; i < 5; i++) {
            delays.add(3);
        }
        delays.add(5);
        delays.add(6);
        delays.add(8);
        return delays.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("kasalar.aktif", true);
        announceChance = plugin.getConfig().getDouble("kasalar.duyuru-sans", 5);
        types.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("kasalar.turler");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                ConfigurationSection type = section.getConfigurationSection(id);
                if (type == null) {
                    continue;
                }
                List<Reward> rewards = new ArrayList<>();
                double total = 0;
                for (Map<?, ?> raw : type.getMapList("oduller")) {
                    Reward reward = parseReward(id, raw);
                    if (reward != null) {
                        rewards.add(reward);
                        total += reward.chance();
                    }
                }
                if (rewards.isEmpty()) {
                    plugin.getLogger().warning("Kasa '" + id + "' icin odul yok, atlandi.");
                    continue;
                }
                types.put(id, new CrateType(id, type.getString("isim", id), type.getString("anahtar", id + " anahtari"), rewards, total));
            }
        }
        if (enabled) {
            particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::particles, 20L, 20L);
        }
    }

    @Override
    public void stop() {
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
        // Donen carklar hemen sonuclanir, odul kaybolmaz.
        for (CrateMenu menu : new ArrayList<>(spinning.values())) {
            finish(menu);
            menu.player.closeInventory();
        }
        spinning.clear();
    }

    private Reward parseReward(String crate, Map<?, ?> raw) {
        double chance = number(raw.get("sans"), 10);
        int amount = (int) Math.max(1, number(raw.get("adet"), 1));
        try {
            if (raw.containsKey("para")) {
                double money = number(raw.get("para"), 0);
                return new Reward("para", money, null, null, 1, chance, null);
            }
            if (raw.containsKey("anahtar")) {
                return new Reward("anahtar", 0, null, String.valueOf(raw.get("anahtar")).toLowerCase(Locale.ROOT), amount, chance, null);
            }
            if (raw.containsKey("esya")) {
                Material material = Material.matchMaterial(String.valueOf(raw.get("esya")));
                if (material == null || !material.isItem()) {
                    throw new IllegalArgumentException("bilinmeyen esya " + raw.get("esya"));
                }
                ItemStack item = new ItemStack(material);
                if (raw.containsKey("buyu")) {
                    enchant(item, String.valueOf(raw.get("buyu")));
                }
                String label = raw.containsKey("isim") ? String.valueOf(raw.get("isim")) : null;
                if (label != null) {
                    ItemMeta meta = item.getItemMeta();
                    meta.displayName(Messages.itemText(label));
                    item.setItemMeta(meta);
                }
                return new Reward("esya", 0, item, null, amount, chance, label);
            }
            throw new IllegalArgumentException("para, esya ya da anahtar yazilmali");
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Kasa '" + crate + "' odulu atlandi (" + raw + "): " + e.getMessage());
            return null;
        }
    }

    private static double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? fallback : Double.parseDouble(String.valueOf(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** "sharpness:5,unbreaking:3" bicimindeki buyuleri ekler (buyu kitabina saklanan buyu olarak). */
    private static void enchant(ItemStack item, String spec) {
        ItemMeta meta = item.getItemMeta();
        for (String part : spec.split(",")) {
            String[] pieces = part.trim().toLowerCase(Locale.ROOT).split(":");
            Enchantment enchantment = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
                    .get(NamespacedKey.minecraft(pieces[0]));
            if (enchantment == null) {
                throw new IllegalArgumentException("bilinmeyen buyu " + pieces[0]);
            }
            int level = pieces.length > 1 ? Integer.parseInt(pieces[1]) : 1;
            if (meta instanceof EnchantmentStorageMeta book) {
                book.addStoredEnchant(enchantment, level, true);
            } else {
                meta.addEnchant(enchantment, level, true);
            }
        }
        item.setItemMeta(meta);
    }

    // ---- Anahtarlar ----

    boolean hasType(String type) {
        return types.containsKey(type);
    }

    List<String> typeIds() {
        return new ArrayList<>(types.keySet());
    }

    String typeName(String type) {
        CrateType crate = types.get(type);
        return crate == null ? type : crate.name();
    }

    ItemStack createKey(String type, int amount) {
        CrateType crate = types.get(type);
        ItemStack key = new ItemStack(Material.TRIPWIRE_HOOK, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = key.getItemMeta();
        meta.displayName(Messages.itemText(crate == null ? type : crate.keyName()));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.messages().rawList("kasalar.anahtar-aciklama", "kasa", typeName(type))) {
            lore.add(Messages.itemText(line));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, type);
        key.setItemMeta(meta);
        return key;
    }

    String keyType(ItemStack item) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING);
    }

    /** Anahtari verir; envanter doluysa ayagina birakir. Bilinmeyen tur icin false. */
    boolean giveKey(Player player, String type, int amount) {
        if (!enabled || type == null || type.isBlank() || !types.containsKey(type) || amount <= 0) {
            return false;
        }
        giveItem(player, createKey(type, amount));
        plugin.messages().send(player, "kasalar.mesajlar.anahtar-aldin", "adet", amount, "kasa", typeName(type));
        return true;
    }

    /** Ayni esyadan adet kadar verir (yigin sinirina gore boler). */
    static void giveItems(Player player, ItemStack template, int amount) {
        int max = Math.max(1, template.getMaxStackSize());
        while (amount > 0) {
            ItemStack stack = template.clone();
            stack.setAmount(Math.min(max, amount));
            amount -= stack.getAmount();
            giveItem(player, stack);
        }
    }

    static void giveItem(Player player, ItemStack item) {
        for (ItemStack left : player.getInventory().addItem(item).values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), left);
        }
    }

    /** Envanterden bu turden bir anahtar eksiltir. */
    private boolean takeKey(Player player, String type) {
        ItemStack[] contents = player.getInventory().getStorageContents();
        for (int i = 0; i < contents.length; i++) {
            if (type.equals(keyType(contents[i]))) {
                ItemStack item = contents[i];
                item.setAmount(item.getAmount() - 1);
                player.getInventory().setItem(i, item.getAmount() > 0 ? item : null);
                return true;
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (type.equals(keyType(offHand))) {
            offHand.setAmount(offHand.getAmount() - 1);
            player.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
            return true;
        }
        return false;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlaceKey(BlockPlaceEvent event) {
        if (keyType(event.getItemInHand()) != null) {
            event.setCancelled(true); // anahtar (tuzak teli kancasi) yere konamaz
        }
    }

    // ---- Kasa bloklari ----

    private String crateAt(Block block) {
        for (Map.Entry<String, Location> entry : plugin.spawn().crateLocations().entrySet()) {
            Location location = entry.getValue();
            if (location.getWorld() == block.getWorld() && location.getBlockX() == block.getX()
                    && location.getBlockY() == block.getY() && location.getBlockZ() == block.getZ()) {
                return entry.getKey();
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (!enabled || block == null || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        String id = crateAt(block);
        if (id == null) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        CrateType type = types.get(id);
        if (type == null) {
            plugin.messages().send(player, "kasalar.mesajlar.kapali");
            return;
        }
        if (event.getAction() == Action.LEFT_CLICK_BLOCK) {
            openPreview(player, type);
        } else if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            open(player, type, new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 1.2, block.getZ() + 0.5));
        }
    }

    private void open(Player player, CrateType type, Location effect) {
        if (spinning.containsKey(player.getUniqueId())) {
            return;
        }
        if (!takeKey(player, type.id())) {
            plugin.messages().send(player, "kasalar.mesajlar.anahtar-yok", "kasa", type.name());
            Messages.sound(player, "block.chest.locked", 1.0f);
            return;
        }
        Reward reward = roll(type);
        CrateMenu menu = new CrateMenu(false);
        menu.player = player;
        menu.reward = reward;
        menu.type = type;
        menu.inventory = plugin.getServer().createInventory(menu, 27, Messages.color(plugin.messages().raw("kasalar.mesajlar.acilis-baslik", "kasa", type.name())));
        ItemStack glass = filler(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 27; i++) {
            menu.inventory.setItem(i, glass);
        }
        menu.inventory.setItem(4, filler(Material.HOPPER));
        menu.inventory.setItem(22, filler(Material.END_ROD));
        // Cark: son adimda ortadaki (13.) yuvaya odul gelir.
        List<ItemStack> reel = new ArrayList<>();
        for (int i = 0; i < DELAYS.length + 9; i++) {
            reel.add(icon(roll(type), null));
        }
        reel.set(DELAYS.length + 4, icon(reward, null));
        spinning.put(player.getUniqueId(), menu);
        player.openInventory(menu.inventory);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, effect, 30, 0.3, 0.4, 0.3, 0.2);
        int[] step = {0};
        int[] wait = {0};
        menu.task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (wait[0]-- > 0) {
                return;
            }
            for (int i = 0; i < 9; i++) {
                menu.inventory.setItem(9 + i, reel.get(step[0] + i));
            }
            Messages.sound(player, "block.note_block.hat", 0.8f + step[0] * 0.03f);
            if (step[0] >= DELAYS.length) {
                finish(menu);
                return;
            }
            wait[0] = DELAYS[step[0]] - 1;
            step[0]++;
        }, 1L, 1L);
    }

    /** Odulu verir (bir kez). Envanter kapatilirsa, oyuncu cikarsa ya da eklenti kapanirsa da cagrilir. */
    private void finish(CrateMenu menu) {
        if (menu.done) {
            return;
        }
        menu.done = true;
        if (menu.task != null) {
            menu.task.cancel();
        }
        spinning.remove(menu.player.getUniqueId());
        Player player = menu.player;
        Reward reward = menu.reward;
        String label = label(reward);
        switch (reward.kind()) {
            case "para" -> plugin.economy().deposit(player, reward.money());
            case "anahtar" -> giveKey(player, reward.keyType(), reward.amount());
            default -> giveItems(player, reward.item(), reward.amount());
        }
        plugin.messages().send(player, "kasalar.mesajlar.kazandin", "odul", label, "kasa", menu.type.name());
        Messages.sound(player, "entity.player.levelup", 1.2f);
        double percent = 100.0 * reward.chance() / menu.type.totalChance();
        if (percent <= announceChance) {
            plugin.messages().broadcast("kasalar.mesajlar.duyuru", "oyuncu", player.getName(), "odul", label,
                    "kasa", menu.type.name(), "sans", String.format(Locale.ROOT, "%.1f", percent));
        }
    }

    private Reward roll(CrateType type) {
        double pick = ThreadLocalRandom.current().nextDouble() * type.totalChance();
        for (Reward reward : type.rewards()) {
            pick -= reward.chance();
            if (pick < 0) {
                return reward;
            }
        }
        return type.rewards().get(type.rewards().size() - 1);
    }

    String label(Reward reward) {
        return switch (reward.kind()) {
            case "para" -> plugin.economy().format(reward.money());
            case "anahtar" -> reward.amount() + "x " + typeName(reward.keyType()) + " &fanahtarı";
            default -> reward.amount() + "x " + (reward.label() != null ? reward.label() : Messages.item(reward.item().getType()));
        };
    }

    /** Odulun simgesi; chanceOf verilirse aciklamada sansi yazar. */
    private ItemStack icon(Reward reward, CrateType chanceOf) {
        ItemStack icon = switch (reward.kind()) {
            case "para" -> new ItemStack(Material.GOLD_INGOT);
            case "anahtar" -> createKey(reward.keyType(), reward.amount());
            default -> reward.item().clone();
        };
        if ("esya".equals(reward.kind())) {
            icon.setAmount(Math.min(reward.amount(), icon.getMaxStackSize()));
        }
        ItemMeta meta = icon.getItemMeta();
        if (!"esya".equals(reward.kind()) || reward.label() == null) {
            meta.displayName(Messages.itemText("&e" + label(reward)));
        }
        if (chanceOf != null) {
            double percent = 100.0 * reward.chance() / chanceOf.totalChance();
            meta.lore(List.of(Messages.itemText(plugin.messages().raw("kasalar.mesajlar.sans-satiri",
                    "sans", String.format(Locale.ROOT, "%.1f", percent)))));
        }
        icon.setItemMeta(meta);
        return icon;
    }

    private static ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(" "));
        item.setItemMeta(meta);
        return item;
    }

    private void openPreview(Player player, CrateType type) {
        CrateMenu menu = new CrateMenu(true);
        int rows = Math.min(6, (type.rewards().size() + 8) / 9);
        menu.inventory = plugin.getServer().createInventory(menu, rows * 9,
                Messages.color(plugin.messages().raw("kasalar.mesajlar.onizleme-baslik", "kasa", type.name())));
        for (int i = 0; i < type.rewards().size() && i < rows * 9; i++) {
            menu.inventory.setItem(i, icon(type.rewards().get(i), type));
        }
        player.openInventory(menu.inventory);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CrateMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof CrateMenu menu && !menu.preview) {
            finish(menu); // cark bitmeden kapatildiysa odul hemen verilir
        }
    }

    private void particles() {
        for (Location location : plugin.spawn().crateLocations().values()) {
            if (location.getWorld() != null && !location.getWorld().getNearbyPlayers(location, 24).isEmpty()) {
                location.getWorld().spawnParticle(Particle.ENCHANT, location.clone().add(0.5, 1.3, 0.5), 12, 0.3, 0.3, 0.3, 0.5);
            }
        }
    }
}
