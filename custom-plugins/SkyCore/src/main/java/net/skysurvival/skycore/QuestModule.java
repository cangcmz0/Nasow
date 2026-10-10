package net.skysurvival.skycore;

import java.io.File;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.FurnaceExtractEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Gunluk gorevler: her oyuncuya her gun havuzdan rastgele birkac gorev (kaz, oldur, balik tut, hasat et, pisir,
 * hayvan uret). Bitirince para (ve istege bagli kasa anahtari), hepsi bitince bonus. Oyuncunun kendi koydugu
 * bloklari kirip tekrar koyarak gorev yapilamaz (koyulan bloklar parca verisinde isaretlenir).
 */
final class QuestModule implements Module, CommandExecutor, TabCompleter {
    enum Type { KAZ, OLDUR, BALIK, HASAT, PISIR, URE }

    record Quest(String id, String name, Type type, Set<Material> materials, Set<EntityType> entities, boolean anyEnemy,
                 int target, double reward, String key) {
        boolean matches(Material material) {
            return materials.contains(material);
        }
    }

    private final SkyCore plugin;
    private final NamespacedKey placedKey;
    private final Map<String, Quest> pool = new LinkedHashMap<>();
    private final Set<Material> tracked = EnumSet.noneOf(Material.class);
    private boolean enabled;
    private int perDay;
    private double bonusMoney;
    private String bonusKey;
    private ZoneId zone = ZoneId.of("Europe/Istanbul");

    QuestModule(SkyCore plugin) {
        this.plugin = plugin;
        this.placedKey = new NamespacedKey(plugin, "koyulan-bloklar");
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("gorevler.aktif", true);
        File file = new File(plugin.getDataFolder(), "gorevler.yml");
        if (!file.exists()) {
            plugin.saveResource("gorevler.yml", false);
        }
        load(YamlConfiguration.loadConfiguration(file));
        try {
            zone = ZoneId.of(plugin.getConfig().getString("gunluk-odul.saat-dilimi", "Europe/Istanbul"));
        } catch (RuntimeException e) {
            zone = ZoneId.of("Europe/Istanbul");
        }
    }

    @Override
    public void stop() {
    }

    void load(YamlConfiguration yaml) {
        pool.clear();
        tracked.clear();
        perDay = Math.max(1, yaml.getInt("gunluk-gorev-sayisi", 3));
        bonusMoney = yaml.getDouble("bonus.para", 1000);
        bonusKey = yaml.getString("bonus.anahtar", "");
        ConfigurationSection section = yaml.getConfigurationSection("gorevler");
        if (section == null) {
            return;
        }
        for (String id : section.getKeys(false)) {
            ConfigurationSection quest = section.getConfigurationSection(id);
            if (quest == null) {
                continue;
            }
            Type type;
            try {
                type = Type.valueOf(quest.getString("tur", "").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("gorevler.yml: '" + id + "' gecersiz tur: " + quest.getString("tur"));
                continue;
            }
            Set<Material> materials = EnumSet.noneOf(Material.class);
            Set<EntityType> entities = EnumSet.noneOf(EntityType.class);
            boolean anyEnemy = false;
            for (String target : quest.getStringList("hedef")) {
                String name = target.trim().toLowerCase(Locale.ROOT);
                if (type == Type.OLDUR) {
                    if (name.equals("dusman")) {
                        anyEnemy = true;
                        continue;
                    }
                    try {
                        entities.add(EntityType.valueOf(name.toUpperCase(Locale.ROOT)));
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("gorevler.yml: '" + id + "' bilinmeyen yaratik: " + target);
                    }
                } else if (name.startsWith("#")) {
                    Tag<Material> tag = Bukkit.getTag(Tag.REGISTRY_BLOCKS, NamespacedKey.minecraft(name.substring(1)), Material.class);
                    if (tag == null) {
                        tag = Bukkit.getTag(Tag.REGISTRY_ITEMS, NamespacedKey.minecraft(name.substring(1)), Material.class);
                    }
                    if (tag != null) {
                        materials.addAll(tag.getValues());
                    } else {
                        plugin.getLogger().warning("gorevler.yml: '" + id + "' bilinmeyen etiket: " + target);
                    }
                } else {
                    Material material = Material.matchMaterial(name);
                    if (material != null) {
                        materials.add(material);
                    } else {
                        plugin.getLogger().warning("gorevler.yml: '" + id + "' bilinmeyen esya/blok: " + target);
                    }
                }
            }
            int amount = Math.max(1, quest.getInt("adet", 1));
            pool.put(id, new Quest(id, quest.getString("isim", id), type, materials, entities, anyEnemy, amount,
                    quest.getDouble("odul", 0), quest.getString("anahtar", "")));
            if (type == Type.KAZ) {
                tracked.addAll(materials);
            }
        }
    }

    LocalDate today() {
        return LocalDate.now(zone);
    }

    /** Oyuncunun bugunku gorevleri (gun degistiyse yenileri verilir). */
    List<Quest> questsOf(Player player) {
        UUID id = player.getUniqueId();
        String date = today().toString();
        if (!date.equals(plugin.data().questDate(id))) {
            List<String> ids = new ArrayList<>(pool.keySet());
            Collections.shuffle(ids, new Random(today().toEpochDay() * 31 + id.hashCode()));
            plugin.data().setQuests(id, player.getName(), date, new ArrayList<>(ids.subList(0, Math.min(perDay, ids.size()))));
        }
        List<Quest> result = new ArrayList<>();
        for (String questId : plugin.data().quests(id)) {
            Quest quest = pool.get(questId);
            if (quest != null) {
                result.add(quest);
            }
        }
        return result;
    }

    int doneCount(Player player) {
        int done = 0;
        for (Quest quest : questsOf(player)) {
            if (plugin.data().questProgress(player.getUniqueId(), quest.id()) >= quest.target()) {
                done++;
            }
        }
        return done;
    }

    /** Giriste: bugunku gorevler bitmediyse hatirlatir. */
    void remind(Player player) {
        if (enabled && !pool.isEmpty() && doneCount(player) < questsOf(player).size()) {
            plugin.messages().send(player, "gorevler.mesajlar.hatirlatma", "tamam", doneCount(player), "toplam", questsOf(player).size());
        }
    }

    /** Placeholder icin: "2/3". Baska is parcaciklarindan da cagrilabildigi icin kayda yazmaz. */
    String summary(Player player) {
        UUID id = player.getUniqueId();
        if (!today().toString().equals(plugin.data().questDate(id))) {
            return "0/" + Math.min(perDay, pool.size());
        }
        int done = 0;
        List<String> ids = plugin.data().quests(id);
        for (String questId : ids) {
            Quest quest = pool.get(questId);
            if (quest != null && plugin.data().questProgress(id, questId) >= quest.target()) {
                done++;
            }
        }
        return done + "/" + ids.size();
    }

    void progress(Player player, Type type, Material material, EntityType entity, boolean enemy, int amount) {
        if (!enabled || amount <= 0 || !plugin.auth().isLoggedIn(player)) {
            return;
        }
        UUID id = player.getUniqueId();
        Messages messages = plugin.messages();
        for (Quest quest : questsOf(player)) {
            if (quest.type() != type) {
                continue;
            }
            boolean match = switch (type) {
                case OLDUR -> quest.entities().contains(entity) || (quest.anyEnemy() && enemy);
                case BALIK, URE -> true;
                default -> quest.matches(material);
            };
            int before = plugin.data().questProgress(id, quest.id());
            if (!match || before >= quest.target()) {
                continue;
            }
            int after = Math.min(quest.target(), before + amount);
            plugin.data().setQuestProgress(id, quest.id(), after);
            if (after < quest.target()) {
                messages.actionBar(player, "gorevler.mesajlar.ilerleme", "gorev", quest.name(), "ilerleme", after, "hedef", quest.target());
                continue;
            }
            plugin.data().addQuestDone(id);
            if (quest.reward() > 0) {
                plugin.economy().deposit(player, quest.reward());
            }
            plugin.crates().giveKey(player, quest.key(), 1);
            messages.send(player, "gorevler.mesajlar.tamamlandi", "gorev", quest.name(), "odul", plugin.economy().format(quest.reward()));
            messages.title(player, "gorevler.mesajlar.baslik", "gorevler.mesajlar.alt-baslik", "gorev", quest.name());
            Messages.sound(player, "ui.toast.challenge_complete", 1.2f);
            if (doneCount(player) >= questsOf(player).size() && !plugin.data().questBonus(id)) {
                plugin.data().setQuestBonus(id);
                if (bonusMoney > 0) {
                    plugin.economy().deposit(player, bonusMoney);
                }
                plugin.crates().giveKey(player, bonusKey, 1);
                messages.broadcast("gorevler.mesajlar.hepsi-bitti", "oyuncu", player.getName(), "odul", plugin.economy().format(bonusMoney));
            }
        }
    }

    // ---- Oyuncunun koydugu bloklar (parca verisinde) ----

    private static long pack(Block block) {
        return ((long) (block.getY() + 4096) << 8) | ((long) (block.getZ() & 15) << 4) | (block.getX() & 15);
    }

    private void markPlaced(Block block) {
        PersistentDataContainer data = block.getChunk().getPersistentDataContainer();
        long[] old = data.getOrDefault(placedKey, PersistentDataType.LONG_ARRAY, new long[0]);
        long packed = pack(block);
        for (long value : old) {
            if (value == packed) {
                return;
            }
        }
        int keep = Math.min(old.length, 8191); // parca basina en fazla 8192 kayit
        long[] next = new long[keep + 1];
        System.arraycopy(old, old.length - keep, next, 0, keep);
        next[keep] = packed;
        data.set(placedKey, PersistentDataType.LONG_ARRAY, next);
    }

    /** Blok oyuncu tarafindan konmus muydu? Konmussa kaydi siler. */
    boolean consumePlaced(Block block) {
        Chunk chunk = block.getChunk();
        PersistentDataContainer data = chunk.getPersistentDataContainer();
        long[] old = data.get(placedKey, PersistentDataType.LONG_ARRAY);
        if (old == null) {
            return false;
        }
        long packed = pack(block);
        for (int i = 0; i < old.length; i++) {
            if (old[i] == packed) {
                long[] next = new long[old.length - 1];
                System.arraycopy(old, 0, next, 0, i);
                System.arraycopy(old, i + 1, next, i, old.length - i - 1);
                if (next.length == 0) {
                    data.remove(placedKey);
                } else {
                    data.set(placedKey, PersistentDataType.LONG_ARRAY, next);
                }
                return true;
            }
        }
        return false;
    }

    /** Pistonla itilen/cekilen konmus bloklarin isareti de tasinir (pistonla "dogal blok" yapma hilesi). */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        movePlaced(event.getBlocks(), event.getDirection());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        movePlaced(event.getBlocks(), event.getDirection().getOppositeFace()); // cekilen bloklar pistona dogru gider
    }

    private void movePlaced(List<Block> blocks, BlockFace direction) {
        List<Block> moved = new ArrayList<>();
        for (Block block : blocks) {
            if (consumePlaced(block)) {
                moved.add(block.getRelative(direction));
            }
        }
        moved.forEach(this::markPlaced);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (tracked.contains(event.getBlock().getType())) {
            markPlaced(event.getBlock());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        Material type = block.getType();
        if (Tag.CROPS.isTagged(type) || type == Material.NETHER_WART || type == Material.COCOA) {
            if (block.getBlockData() instanceof Ageable crop && crop.getAge() >= crop.getMaximumAge()) {
                progress(event.getPlayer(), Type.HASAT, type, null, false, 1);
            }
            return;
        }
        if (!tracked.contains(type)) {
            return;
        }
        if (consumePlaced(block)) {
            return; // oyuncunun koydugu blok sayilmaz
        }
        // Ipeksi dokunusla kazilan maden tekrar konup kazilabilecegi icin sayilmaz
        if (type.name().endsWith("_ORE") && event.getPlayer().getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
            return;
        }
        progress(event.getPlayer(), Type.KAZ, type, null, false, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && !(event.getEntity() instanceof Player)) {
            progress(killer, Type.OLDUR, null, event.getEntityType(), event.getEntity() instanceof Enemy, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            progress(event.getPlayer(), Type.BALIK, null, null, false, 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSmelt(FurnaceExtractEvent event) {
        progress(event.getPlayer(), Type.PISIR, event.getItemType(), null, false, event.getItemAmount());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreed(EntityBreedEvent event) {
        if (event.getBreeder() instanceof Player player) {
            progress(player, Type.URE, null, null, false, 1);
        }
    }

    // ---- /gorev ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (!enabled || pool.isEmpty()) {
            messages.send(player, "genel-mesajlar.yetki-yok");
            return true;
        }
        Duration left = Duration.between(LocalDateTime.now(zone), today().plusDays(1).atStartOfDay());
        messages.sendLines(player, "gorevler.mesajlar.baslik-liste", "saat", left.toHours(), "dakika", left.toMinutesPart());
        for (Quest quest : questsOf(player)) {
            int value = plugin.data().questProgress(player.getUniqueId(), quest.id());
            boolean done = value >= quest.target();
            player.sendMessage(Messages.color(messages.raw(done ? "gorevler.mesajlar.satir-tamam" : "gorevler.mesajlar.satir",
                    "gorev", quest.name(), "aciklama", describe(quest), "ilerleme", value, "hedef", quest.target(),
                    "cubuk", bar(value, quest.target()), "odul", plugin.economy().format(quest.reward()))));
        }
        messages.sendLines(player, "gorevler.mesajlar.alt-bilgi", "bonus", plugin.economy().format(bonusMoney),
                "anahtar", bonusKey.isBlank() ? "" : "+ " + plugin.crates().typeName(bonusKey));
        return true;
    }

    String describe(Quest quest) {
        return plugin.messages().raw("gorevler.turler." + quest.type().name().toLowerCase(Locale.ROOT), "adet", quest.target());
    }

    static String bar(int value, int target) {
        int filled = (int) Math.round(10.0 * value / target);
        return "&a" + "■".repeat(filled) + "&7" + "■".repeat(10 - filled);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
