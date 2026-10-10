package net.skysurvival.skycore;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Skull;
import org.bukkit.block.data.Rotatable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

/**
 * Mezar: olen oyuncunun esyalari ve XP'si oldugu yerde bir mezara (oyuncu kafasi) girer; lavda, bosta ya da
 * baskasinin eline gecmez. Sahibi sag tiklayinca hepsini geri alir. Belirli sure sadece sahibi acabilir, sonra
 * herkese acilir; sure dolunca esyalar yere duser. PvP olumlerinde (ayara gore) esyalar normal duser.
 */
final class GraveModule implements Module, CommandExecutor, TabCompleter {
    static final String ADMIN = "skycore.mezar.admin";

    static final class Grave {
        String id;
        UUID owner;
        String ownerName;
        String world;
        int x;
        int y;
        int z;
        long created;
        List<ItemStack> items = new ArrayList<>();
        int xp;
        UUID display;

        boolean at(Block block) {
            return block.getX() == x && block.getY() == y && block.getZ() == z && block.getWorld().getName().equals(world);
        }
    }

    private final SkyCore plugin;
    private final File file;
    private final NamespacedKey displayKey;
    private final Map<String, Grave> graves = new LinkedHashMap<>();
    private boolean enabled;
    private boolean pvpGraves;
    private long lockMillis;
    private long expireMillis;
    private int maxPerPlayer;
    private BukkitTask task;
    private boolean dirty;

    GraveModule(SkyCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mezarlar.yml");
        this.displayKey = new NamespacedKey(plugin, "mezar");
        load();
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("mezar.aktif", true);
        pvpGraves = plugin.getConfig().getBoolean("mezar.pvp-olumlerinde", false);
        lockMillis = Math.max(0, plugin.getConfig().getInt("mezar.kilit-dakika", 15)) * 60_000L;
        expireMillis = Math.max(1, plugin.getConfig().getInt("mezar.sure-dakika", 60)) * 60_000L;
        maxPerPlayer = Math.max(1, plugin.getConfig().getInt("mezar.oyuncu-basina-en-fazla", 5));
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L * 30, 20L * 30);
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        saveIfDirty();
    }

    // ---- Kayit ----

    private void load() {
        graves.clear();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("mezarlar");
        if (root == null) {
            return;
        }
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            try {
                Grave grave = new Grave();
                grave.id = id;
                grave.owner = UUID.fromString(section.getString("sahip", ""));
                grave.ownerName = section.getString("isim", "?");
                grave.world = section.getString("dunya", "world");
                grave.x = section.getInt("x");
                grave.y = section.getInt("y");
                grave.z = section.getInt("z");
                grave.created = section.getLong("zaman");
                grave.xp = section.getInt("xp");
                String display = section.getString("yazi");
                grave.display = display == null ? null : UUID.fromString(display);
                for (Object item : section.getList("esyalar", List.of())) {
                    if (item instanceof ItemStack stack) {
                        grave.items.add(stack);
                    }
                }
                graves.put(id, grave);
            } catch (IllegalArgumentException | NullPointerException e) {
                plugin.getLogger().warning("mezarlar.yml: bozuk mezar atlandi: " + id);
            }
        }
    }

    void saveIfDirty() {
        if (!dirty) {
            return;
        }
        YamlConfiguration yaml = new YamlConfiguration();
        for (Grave grave : graves.values()) {
            String path = "mezarlar." + grave.id;
            yaml.set(path + ".sahip", grave.owner.toString());
            yaml.set(path + ".isim", grave.ownerName);
            yaml.set(path + ".dunya", grave.world);
            yaml.set(path + ".x", grave.x);
            yaml.set(path + ".y", grave.y);
            yaml.set(path + ".z", grave.z);
            yaml.set(path + ".zaman", grave.created);
            yaml.set(path + ".xp", grave.xp);
            yaml.set(path + ".yazi", grave.display == null ? null : grave.display.toString());
            yaml.set(path + ".esyalar", grave.items);
        }
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("mezarlar.yml kaydedilemedi: " + e.getMessage());
        }
    }

    private void changed() {
        dirty = true;
        saveIfDirty(); // esyalar kaybolmasin diye hemen yazilir
    }

    // ---- Olum ----

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!enabled || event.getKeepInventory() || event.getDrops().isEmpty()) {
            return;
        }
        Player killer = player.getKiller();
        boolean pvp = (killer != null && !killer.equals(player)) || plugin.combat().inCombat(player);
        if (!pvpGraves && pvp) {
            return; // PvP ya da savastan kacma: esyalar normal duser, mezarla saklanamaz
        }
        Block block = findSpot(player.getLocation());
        if (block == null) {
            return;
        }
        Grave grave = new Grave();
        grave.id = UUID.randomUUID().toString().substring(0, 8);
        grave.owner = player.getUniqueId();
        grave.ownerName = player.getName();
        grave.world = block.getWorld().getName();
        grave.x = block.getX();
        grave.y = block.getY();
        grave.z = block.getZ();
        grave.created = System.currentTimeMillis();
        for (ItemStack item : event.getDrops()) {
            if (item != null && !item.getType().isAir()) {
                grave.items.add(item.clone());
            }
        }
        grave.xp = event.getDroppedExp();
        event.getDrops().clear();
        event.setDroppedExp(0);

        removeOldest(player.getUniqueId());
        placeBlock(block, player, grave);
        graves.put(grave.id, grave);
        changed();
        plugin.messages().send(player, "mezar.mesajlar.olustu", "x", grave.x, "y", grave.y, "z", grave.z,
                "dunya", grave.world, "kilit", lockMillis / 60_000, "sure", expireMillis / 60_000);
    }

    /** Oyuncunun en fazla mezar sayisini asarsa en eskisi yere dokulur. */
    private void removeOldest(UUID owner) {
        List<Grave> own = new ArrayList<>(graves.values().stream().filter(g -> g.owner.equals(owner)).toList());
        own.sort(Comparator.comparingLong(g -> g.created));
        while (own.size() >= maxPerPlayer) {
            spill(own.remove(0));
        }
    }

    /** Olunen yerin hemen ustunde (bosluk, sivi ya da cimen gibi) mezar konabilecek bir blok bulur. */
    Block findSpot(Location death) {
        World world = death.getWorld();
        if (world == null) {
            return null;
        }
        int x = death.getBlockX();
        int z = death.getBlockZ();
        int startY = Math.max(world.getMinHeight() + 1, Math.min(world.getMaxHeight() - 2, death.getBlockY()));
        for (int dy = 0; dy < 16 && startY + dy < world.getMaxHeight() - 1; dy++) {
            Block block = world.getBlockAt(x, startY + dy, z);
            if (replaceable(block) && graveAt(block) == null) {
                return block;
            }
        }
        // Bosta (void) ya da kapali yerde: en yakin bos yer yukari dogru
        Block top = world.getHighestBlockAt(x, z).getRelative(BlockFace.UP);
        return top.getY() < world.getMaxHeight() && graveAt(top) == null ? top : null;
    }

    private static boolean replaceable(Block block) {
        Material type = block.getType();
        return type.isAir() || block.isLiquid() || block.isReplaceable();
    }

    private void placeBlock(Block block, Player player, Grave grave) {
        block.setType(Material.PLAYER_HEAD, false);
        if (block.getBlockData() instanceof Rotatable rotatable) {
            rotatable.setRotation(facingFromYaw(player.getLocation().getYaw()).getOppositeFace());
            block.setBlockData(rotatable, false);
        }
        try {
            if (block.getState() instanceof Skull skull) {
                skull.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
                skull.update(true, false);
            }
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Mezar kafasina skin konamadi: " + e);
        }
        try {
            Location above = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 1.25, block.getZ() + 0.5);
            TextDisplay display = block.getWorld().spawn(above, TextDisplay.class, text -> {
                text.text(Messages.color(plugin.messages().raw("mezar.yazi", "oyuncu", grave.ownerName)));
                text.setBillboard(Display.Billboard.CENTER);
                text.setSeeThrough(false);
                text.getPersistentDataContainer().set(displayKey, PersistentDataType.STRING, grave.id);
            });
            grave.display = display.getUniqueId();
        } catch (RuntimeException e) {
            plugin.getLogger().fine("Mezar yazisi konamadi: " + e);
        }
    }

    private static final BlockFace[] ROTATIONS = {BlockFace.SOUTH, BlockFace.SOUTH_SOUTH_WEST, BlockFace.SOUTH_WEST,
        BlockFace.WEST_SOUTH_WEST, BlockFace.WEST, BlockFace.WEST_NORTH_WEST, BlockFace.NORTH_WEST, BlockFace.NORTH_NORTH_WEST,
        BlockFace.NORTH, BlockFace.NORTH_NORTH_EAST, BlockFace.NORTH_EAST, BlockFace.EAST_NORTH_EAST, BlockFace.EAST,
        BlockFace.EAST_SOUTH_EAST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_SOUTH_EAST};

    /** Oyuncunun baktigi yon (16 yonlu, kafa blogu icin). */
    static BlockFace facingFromYaw(float yaw) {
        int index = Math.floorMod(Math.round(yaw / 22.5f), 16);
        return ROTATIONS[index];
    }

    // ---- Acma ----

    Grave graveAt(Block block) {
        for (Grave grave : graves.values()) {
            if (grave.at(block)) {
                return grave;
            }
        }
        return null;
    }

    boolean locked(Grave grave) {
        return System.currentTimeMillis() - grave.created < lockMillis;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.PLAYER_HEAD || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Grave grave = graveAt(block);
        if (grave == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            if (grave != null) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(grave.owner) && locked(grave) && !player.hasPermission(ADMIN)) {
            long left = (lockMillis - (System.currentTimeMillis() - grave.created) + 59_999) / 60_000;
            plugin.messages().send(player, "mezar.mesajlar.kilitli", "oyuncu", grave.ownerName, "dakika", left);
            return;
        }
        collect(player, grave);
    }

    /** Esyalari oyuncuya verir (bos zirh yuvalarina giydirir), artanlari yere birakir. */
    void collect(Player player, Grave grave) {
        graves.remove(grave.id);
        for (ItemStack item : grave.items) {
            if (!equip(player, item)) {
                CrateModule.giveItem(player, item);
            }
        }
        player.giveExp(grave.xp);
        clearBlock(grave);
        changed();
        plugin.messages().send(player, "mezar.mesajlar.alindi", "oyuncu", grave.ownerName, "adet", grave.items.size());
        Messages.sound(player, "item.armor.equip_netherite", 1.0f);
    }

    /** Zirhi bos yuvasina giydirir (kask, gogusluk/elitra, pantolon, bot). */
    private static boolean equip(Player player, ItemStack item) {
        String name = item.getType().name();
        EquipmentSlot slot = name.endsWith("_HELMET") ? EquipmentSlot.HEAD
                : name.endsWith("_CHESTPLATE") || name.equals("ELYTRA") ? EquipmentSlot.CHEST
                : name.endsWith("_LEGGINGS") ? EquipmentSlot.LEGS
                : name.endsWith("_BOOTS") ? EquipmentSlot.FEET : null;
        if (slot == null) {
            return false;
        }
        ItemStack current = player.getInventory().getItem(slot);
        if (current.getType().isAir()) {
            player.getInventory().setItem(slot, item);
            return true;
        }
        return false;
    }

    private void clearBlock(Grave grave) {
        World world = plugin.getServer().getWorld(grave.world);
        if (world == null) {
            return;
        }
        Block block = world.getBlockAt(grave.x, grave.y, grave.z);
        if (block.getType() == Material.PLAYER_HEAD) {
            block.setType(Material.AIR, false);
        }
        if (grave.display != null) {
            Entity display = plugin.getServer().getEntity(grave.display);
            if (display != null) {
                display.remove();
            }
        }
    }

    /** Sure doldu: esyalar ve XP mezarin yerine dokulur. */
    private void spill(Grave grave) {
        graves.remove(grave.id);
        World world = plugin.getServer().getWorld(grave.world);
        if (world != null) {
            Location at = new Location(world, grave.x + 0.5, grave.y + 0.5, grave.z + 0.5);
            world.getChunkAt(at); // parca yukleniyor
            for (ItemStack item : grave.items) {
                world.dropItemNaturally(at, item);
            }
            if (grave.xp > 0) {
                world.spawn(at, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(grave.xp));
            }
            clearBlock(grave);
        }
        dirty = true;
    }

    private void tick() {
        long now = System.currentTimeMillis();
        boolean any = false;
        for (Grave grave : new ArrayList<>(graves.values())) {
            if (now - grave.created >= expireMillis) {
                spill(grave);
                any = true;
                Player owner = plugin.getServer().getPlayer(grave.owner);
                if (owner != null) {
                    plugin.messages().send(owner, "mezar.mesajlar.suresi-doldu", "x", grave.x, "y", grave.y, "z", grave.z);
                }
            }
        }
        if (any) {
            saveIfDirty();
        }
    }

    // ---- Mezari bozmaya karsi koruma ----

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Grave grave = event.getBlock().getType() == Material.PLAYER_HEAD ? graveAt(event.getBlock()) : null;
        if (grave == null) {
            return;
        }
        event.setCancelled(true);
        plugin.messages().send(event.getPlayer(), "mezar.mesajlar.kirilamaz");
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.PLAYER_HEAD && graveAt(block) != null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(block -> block.getType() == Material.PLAYER_HEAD && graveAt(block) != null);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> graveAt(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(block -> graveAt(block) != null)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (event.getToBlock().getType() == Material.PLAYER_HEAD && graveAt(event.getToBlock()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (event.getBlock().getType() == Material.PLAYER_HEAD && graveAt(event.getBlock()) != null) {
            event.setCancelled(true);
        }
    }

    /** Silinmis mezarlardan kalan yazilari temizler (parca yuklenince). */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent event) {
        for (Entity entity : event.getEntities()) {
            String id = entity.getPersistentDataContainer().get(displayKey, PersistentDataType.STRING);
            if (id != null && !graves.containsKey(id)) {
                entity.remove();
            }
        }
    }

    // ---- /mezar ----

    List<Grave> gravesOf(UUID owner) {
        List<Grave> list = new ArrayList<>();
        for (Grave grave : graves.values()) {
            if (grave.owner.equals(owner)) {
                list.add(grave);
            }
        }
        return list;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        List<Grave> own = gravesOf(player.getUniqueId());
        if (own.isEmpty()) {
            messages.send(player, "mezar.mesajlar.yok");
            return true;
        }
        messages.sendLines(player, "mezar.mesajlar.liste-baslik");
        long now = System.currentTimeMillis();
        for (Grave grave : own) {
            long left = Math.max(0, (expireMillis - (now - grave.created)) / 60_000);
            player.sendMessage(Messages.color(messages.raw("mezar.mesajlar.liste-satir", "x", grave.x, "y", grave.y, "z", grave.z,
                    "dunya", grave.world, "adet", grave.items.size(), "dakika", left)));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    /** Testler icin. */
    int count() {
        return graves.size();
    }
}
