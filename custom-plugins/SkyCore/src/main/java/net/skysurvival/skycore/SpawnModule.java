package net.skysurvival.skycore;

import com.destroystokyo.paper.profile.ProfileProperty;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.block.sign.SignSide;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.block.LeavesDecayEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPlaceEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/**
 * Gokyuzundeki spawn adasi ve KOTH etkinlik arenasi ("The Hill", CC BY-SA 4.0): /skycore kurulum ile kurulur;
 * spawn, warp ve hologramlari ayarlar.
 * Adayi korur (yalnizca yetkililer degistirebilir, arena disinda hasar/aclik yok), portallari calistirir
 * ve adadan atlayan oyuncuyu yere yaklasinca yavaslatip dusme hasarindan korur.
 */
final class SpawnModule implements Module {
    static final String EDIT = "skycore.spawn.duzenle";

    /** Adada elde tutulurken sag tiklanabilen esyalar (yemek yemek, ok atmak vb. engellenmesin). */
    private static final Set<Material> USABLE = Set.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.BOW, Material.CROSSBOW, Material.TRIDENT, Material.SHIELD, Material.ENDER_PEARL, Material.SNOWBALL,
            Material.EGG, Material.FIREWORK_ROCKET, Material.MILK_BUCKET, Material.WRITTEN_BOOK, Material.WRITABLE_BOOK,
            Material.MAP, Material.FILLED_MAP, Material.COMPASS, Material.RECOVERY_COMPASS, Material.CLOCK,
            Material.SPYGLASS, Material.GOAT_HORN, Material.TRIPWIRE_HOOK, Material.PAPER);
    private static final Set<String> HOSTILE_SPAWNS = Set.of("NATURAL", "JOCKEY", "MOUNT", "PATROL", "RAID",
            "REINFORCEMENTS", "TRAP", "VILLAGE_INVASION", "SILVERFISH_BLOCK", "ENDER_PEARL", "LIGHTNING", "NETHER_PORTAL");
    private static final Set<String> ALWAYS_DAMAGE = Set.of("VOID", "KILL", "SUICIDE", "WORLD_BORDER");

    record AbsPortal(String name, World world, Box area, String command) {}

    private final SkyCore plugin;
    private IslandLayout layout;

    // Kurulan ada (kurulmadiysa world == null)
    private World world;
    private int ox;
    private int oy;
    private int oz;
    private Box area;
    private Box spawnZone;
    private Box arena;
    private Box koth;
    private final Map<String, AbsPortal> portals = new LinkedHashMap<>();

    // KOTH arenasi (kurulmadiysa arenaWorld == null)
    private ArenaLayout arenaLayout;
    private World arenaWorld;
    private int ax;
    private int ay;
    private int az;
    private Box arenaArea;
    private Box arenaZone;
    private Box arenaHill;

    private boolean protect;
    private boolean noDamage;
    private boolean noHunger;
    private boolean noMobs;
    private boolean parachute;
    private int parachuteHeight;
    private boolean portalEffects;

    private final Map<UUID, String> insidePortal = new HashMap<>();
    private final Map<UUID, Long> portalCooldown = new HashMap<>();
    /** Adadan atlayan oyuncular -> baslangic zamani. */
    private final Map<UUID, Long> falling = new HashMap<>();
    /** Oyuncunun adada en son goruldugu zaman (sadece adadan dusenler parasut alir). */
    private final Map<UUID, Long> lastOnIsland = new HashMap<>();
    private final Set<UUID> slowed = new java.util.HashSet<>();

    private BukkitTask tickTask;
    private BukkitTask particleTask;
    private BukkitTask buildTask;

    SpawnModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        if (layout == null) {
            try {
                layout = IslandLayout.load(plugin);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("Spawn adasi bilgileri okunamadi: " + e.getMessage());
            }
        }
        if (arenaLayout == null) {
            try {
                arenaLayout = ArenaLayout.load(plugin);
            } catch (IOException | RuntimeException e) {
                plugin.getLogger().severe("KOTH arenasi bilgileri okunamadi: " + e.getMessage());
            }
        }
        protect = plugin.getConfig().getBoolean("spawn-adasi.koruma.aktif", true);
        noDamage = plugin.getConfig().getBoolean("spawn-adasi.koruma.hasar-yok", true);
        noHunger = plugin.getConfig().getBoolean("spawn-adasi.koruma.aclik-yok", true);
        noMobs = plugin.getConfig().getBoolean("spawn-adasi.koruma.yaratik-dogmasin", true);
        parachute = plugin.getConfig().getBoolean("spawn-adasi.parasut.aktif", true);
        parachuteHeight = Math.max(5, plugin.getConfig().getInt("spawn-adasi.parasut.yerden-yukseklik", 25));
        portalEffects = plugin.getConfig().getBoolean("spawn-adasi.portal-efekti", true);
        loadOrigin();
        tickTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
        particleTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::particles, 10L, 10L);
    }

    @Override
    public void stop() {
        if (tickTask != null) {
            tickTask.cancel();
            tickTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
        }
    }

    /** Eklenti kapanirken: yarim kalan kurulumu durdurur. */
    void shutdown() {
        stop();
        if (buildTask != null) {
            buildTask.cancel();
            buildTask = null;
        }
    }

    private void loadOrigin() {
        world = null;
        portals.clear();
        loadArenaOrigin();
        DataStore.Origin origin = plugin.data().islandOrigin();
        if (origin == null || layout == null) {
            return;
        }
        world = plugin.getServer().getWorld(origin.world());
        if (world == null) {
            plugin.getLogger().warning("Spawn adasinin kuruldugu dunya bulunamadi: " + origin.world());
            return;
        }
        ox = origin.x();
        oy = origin.y();
        oz = origin.z();
        area = layout.bounds.shift(ox, oy, oz).grow(3, 3);
        // Yaratik dogmamasi icin ustte genis bir alan (phantomlar oyuncunun 20-35 blok ustunde dogar)
        spawnZone = new Box(area.minX() - 8, area.minY() - 8, area.minZ() - 8, area.maxX() + 8, area.maxY() + 64, area.maxZ() + 8);
        arena = layout.arena.shift(ox, oy, oz);
        koth = layout.koth.shift(ox, oy, oz);
        for (Map.Entry<String, IslandLayout.Portal> entry : layout.portals.entrySet()) {
            IslandLayout.Portal portal = entry.getValue();
            portals.put(entry.getKey(), new AbsPortal(entry.getKey(), world, portal.area().shift(ox, oy, oz), portal.command()));
        }
    }

    private void loadArenaOrigin() {
        arenaWorld = null;
        DataStore.Origin origin = plugin.data().origin("koth-arenasi");
        if (origin == null || arenaLayout == null) {
            return;
        }
        arenaWorld = plugin.getServer().getWorld(origin.world());
        if (arenaWorld == null) {
            plugin.getLogger().warning("KOTH arenasinin kuruldugu dunya bulunamadi: " + origin.world());
            return;
        }
        ax = origin.x();
        ay = origin.y();
        az = origin.z();
        arenaArea = arenaLayout.bounds.shift(ax, ay, az).grow(3, 3);
        arenaZone = new Box(arenaArea.minX() - 8, arenaArea.minY() - 8, arenaArea.minZ() - 8,
                arenaArea.maxX() + 8, arenaArea.maxY() + 64, arenaArea.maxZ() + 8);
        arenaHill = arenaLayout.hill.shift(ax, ay, az);
        portals.put("koth-arenasi", new AbsPortal("koth-arenasi", arenaWorld, arenaLayout.portal.shift(ax, ay, az), "koth katil"));
    }

    // ---- Diger modullerin kullandigi bilgiler ----

    boolean arenaBuilt() {
        return arenaWorld != null;
    }

    /** Ada ya da KOTH arenasi icinde mi (koruma, parasut ve portallar icin). */
    boolean inZone(Location location) {
        return onIsland(location) || inKothArena(location);
    }

    boolean inKothArena(Location location) {
        return arenaWorld != null && location.getWorld() == arenaWorld && arenaArea.contains(location);
    }

    /** KOTH etkinliginin yapildigi dunya (arena varsa arena, yoksa ada). */
    World kothWorld() {
        return arenaWorld != null ? arenaWorld : world;
    }

    /** /koth katil: arenada rastgele bir us; arena yoksa adadaki arena; hicbiri yoksa null. */
    Location kothJoinLocation() {
        if (arenaWorld != null && !arenaLayout.spawns.isEmpty()) {
            IslandLayout.Point point = arenaLayout.spawns.get(ThreadLocalRandom.current().nextInt(arenaLayout.spawns.size()));
            return point.toLocation(arenaWorld, ax, ay, az);
        }
        IslandLayout.Point warp = world == null ? null : layout.warps.get("arena");
        return warp == null ? null : warp.toLocation(world, ox, oy, oz);
    }

    boolean built() {
        return world != null;
    }

    World world() {
        return world;
    }

    boolean onIsland(Location location) {
        return world != null && location.getWorld() == world && area.contains(location);
    }

    boolean inArena(Location location) {
        return world != null && location.getWorld() == world && arena.contains(location);
    }

    /** KOTH alani (gercek koordinat): arenadaki tepe, arena yoksa adadaki altin platform; hicbiri yoksa null. */
    Box kothArea() {
        if (arenaWorld != null) {
            return arenaHill;
        }
        return world == null ? null : koth;
    }

    Location spawnLocation() {
        return world == null || layout == null ? null : layout.spawn.toLocation(world, ox, oy, oz);
    }

    /** Kasa adi -> kasanin blok konumu. */
    Map<String, Location> crateLocations() {
        Map<String, Location> result = new LinkedHashMap<>();
        if (world != null) {
            layout.crates.forEach((name, point) -> result.put(name, point.toLocation(world, ox, oy, oz)));
        }
        return result;
    }

    // ---- Kurulum ----

    void showInstallInfo(CommandSender sender) {
        String worldName = plugin.getConfig().getString("spawn-adasi.dunya", "world");
        plugin.messages().sendLines(sender, "spawn-adasi.mesajlar.kurulum-bilgi", "dunya", worldName,
                "x", plugin.getConfig().getInt("spawn-adasi.konum.x", 0),
                "y", plugin.getConfig().getInt("spawn-adasi.konum.y", 200),
                "z", plugin.getConfig().getInt("spawn-adasi.konum.z", 0),
                "durum", built() ? plugin.messages().raw("spawn-adasi.mesajlar.durum-kurulu", "x", ox, "y", oy, "z", oz)
                        : plugin.messages().raw("spawn-adasi.mesajlar.durum-yok"),
                "ax", plugin.getConfig().getInt("koth-arenasi.konum.x", 0),
                "ay", plugin.getConfig().getInt("koth-arenasi.konum.y", 200),
                "az", plugin.getConfig().getInt("koth-arenasi.konum.z", 220),
                "arena-durum", arenaBuilt() ? plugin.messages().raw("spawn-adasi.mesajlar.durum-kurulu", "x", ax, "y", ay, "z", az)
                        : plugin.messages().raw("spawn-adasi.mesajlar.durum-yok"));
    }

    /** Kurulacak yapi: blueprint, dunya ve merkez noktasi. */
    private record Job(String name, Blueprint blueprint, World world, int x, int y, int z) {
        DataStore.Origin origin() {
            return new DataStore.Origin(world.getName(), x, y, z);
        }
    }

    /** Ayardaki yapiyi hazirlar; sorun varsa gonderene yazar ve null dondurur. */
    private Job prepare(CommandSender sender, String section, String resource, String name, int defaultZ) {
        Messages messages = plugin.messages();
        String worldName = plugin.getConfig().getString(section + ".dunya", "world");
        World target = plugin.getServer().getWorld(worldName);
        if (target == null) {
            messages.send(sender, "spawn-adasi.mesajlar.dunya-yok", "dunya", worldName);
            return null;
        }
        Blueprint blueprint;
        try {
            blueprint = Blueprint.load(plugin, resource);
        } catch (IOException | RuntimeException e) {
            messages.send(sender, "spawn-adasi.mesajlar.hata", "hata", e.getMessage());
            return null;
        }
        int x0 = plugin.getConfig().getInt(section + ".konum.x", 0);
        int y0 = plugin.getConfig().getInt(section + ".konum.y", 200);
        int z0 = plugin.getConfig().getInt(section + ".konum.z", defaultZ);
        if (y0 + blueprint.bounds.minY() <= target.getMinHeight() || y0 + blueprint.bounds.maxY() >= target.getMaxHeight()) {
            messages.send(sender, "spawn-adasi.mesajlar.yukseklik", "yapi", name,
                    "min", target.getMinHeight() - blueprint.bounds.minY() + 1, "max", target.getMaxHeight() - blueprint.bounds.maxY() - 1);
            return null;
        }
        return new Job(name, blueprint, target, x0, y0, z0);
    }

    void install(CommandSender sender) {
        Messages messages = plugin.messages();
        if (buildTask != null) {
            messages.send(sender, "spawn-adasi.mesajlar.kurulum-suruyor");
            return;
        }
        if (layout == null) {
            messages.send(sender, "spawn-adasi.mesajlar.hata", "hata", "spawn/ada.yml okunamadi");
            return;
        }
        Job island = prepare(sender, "spawn-adasi", "spawn/ada.bp.gz", messages.raw("spawn-adasi.mesajlar.yapi-ada"), 0);
        if (island == null) {
            return;
        }
        Job arena = null;
        if (arenaLayout != null && plugin.getConfig().getBoolean("koth-arenasi.aktif", true)) {
            arena = prepare(sender, "koth-arenasi", "koth/arena.bp.gz", messages.raw("spawn-adasi.mesajlar.yapi-arena"), 220);
            if (arena == null) {
                return;
            }
        }
        List<Job> jobs = new ArrayList<>();
        jobs.add(island);
        if (arena != null) {
            jobs.add(arena);
        }
        int blocks = jobs.stream().mapToInt(job -> job.blueprint().solidCount()).sum();
        messages.send(sender, "spawn-adasi.mesajlar.kurulum-basladi", "blok", blocks);
        Job arenaJob = arena;
        build(sender, jobs, 0, () -> finishInstall(sender, island.origin(), arenaJob == null ? null : arenaJob.origin()));
    }

    /** Yapilari sirayla, her tick'te belli sayida blok koyarak kurar. */
    private void build(CommandSender sender, List<Job> jobs, int index, Runnable done) {
        if (index >= jobs.size()) {
            done.run();
            return;
        }
        Job job = jobs.get(index);
        Blueprint blueprint = job.blueprint();
        Messages messages = plugin.messages();
        BlockData[] palette = new BlockData[blueprint.palette.length];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = parseBlock(blueprint.palette[i]);
        }
        BlockData air = Material.AIR.createBlockData();
        int perTick = Math.max(1000, plugin.getConfig().getInt("spawn-adasi.tick-basina-blok", 40000));
        int total = blueprint.size();
        int[] cursor = {0};
        int[] lastPercent = {-1};
        buildTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int end = Math.min(total, cursor[0] + perTick);
            for (int i = cursor[0]; i < end; i++) {
                int cell = blueprint.cell(i);
                BlockData data = cell < 0 ? air : palette[cell];
                Block block = job.world().getBlockAt(job.x() + blueprint.x(i), job.y() + blueprint.y(i), job.z() + blueprint.z(i));
                if (!block.getBlockData().equals(data)) {
                    block.setBlockData(data, false);
                }
            }
            cursor[0] = end;
            int percent = (int) (100L * end / total);
            if (sender instanceof Player player && player.isOnline() && percent / 5 != lastPercent[0] / 5) {
                lastPercent[0] = percent;
                messages.actionBar(player, "spawn-adasi.mesajlar.kurulum-ilerleme", "yapi", job.name(), "yuzde", percent);
            }
            if (end >= total) {
                buildTask.cancel();
                buildTask = null;
                build(sender, jobs, index + 1, done);
            }
        }, 1L, 1L);
    }

    /** Blok yazisini BlockData'ya cevirir; yeni surumde ozellik degistiyse sadece blok turunu kullanir. */
    private BlockData parseBlock(String id) {
        try {
            return Bukkit.createBlockData(id);
        } catch (RuntimeException e) {
            int bracket = id.indexOf('[');
            if (bracket > 0) {
                try {
                    return Bukkit.createBlockData(id.substring(0, bracket));
                } catch (RuntimeException ignored) {
                    // asagida tas kullanilir
                }
            }
            plugin.getLogger().warning("Bilinmeyen blok, yerine tas konuldu: " + id);
            return Material.STONE.createBlockData();
        }
    }

    private void finishInstall(CommandSender sender, DataStore.Origin origin, DataStore.Origin arenaOrigin) {
        plugin.data().setIslandOrigin(origin);
        if (arenaOrigin != null) {
            plugin.data().setOrigin("koth-arenasi", arenaOrigin);
        }
        plugin.data().save();
        loadOrigin();
        Integrations integrations = plugin.integrations();
        Location spawn = spawnLocation();
        world.setSpawnLocation(spawn);
        boolean essentialsSpawn = integrations.setSpawn("default", spawn) & integrations.setSpawn("newbies", spawn);
        int warps = 0;
        for (Map.Entry<String, IslandLayout.Point> warp : layout.warps.entrySet()) {
            if (integrations.setWarp(warp.getKey(), warp.getValue().toLocation(world, ox, oy, oz))) {
                warps++;
            }
        }
        int holograms = 0;
        for (Map.Entry<String, IslandLayout.Hologram> hologram : layout.holograms.entrySet()) {
            Location location = hologram.getValue().position().toLocation(world, ox, oy, oz);
            if (integrations.createHologram(hologram.getKey(), location, hologram.getValue().lines())) {
                holograms++;
            }
        }
        Messages messages = plugin.messages();
        messages.send(sender, "spawn-adasi.mesajlar.kurulum-bitti", "warp", warps + "/" + layout.warps.size(),
                "hologram", holograms + "/" + layout.holograms.size(),
                "spawn", essentialsSpawn ? "EssentialsX" : messages.raw("spawn-adasi.mesajlar.sadece-dunya-spawni"));
        if (arenaOrigin != null && arenaWorld != null) {
            finishArena(sender);
        }
        if (sender instanceof Player player && player.isOnline()) {
            player.teleport(spawn);
            Messages.sound(player, "ui.toast.challenge_complete", 1.0f);
        }
        plugin.getLogger().info("Spawn adasi kuruldu: " + origin + (arenaOrigin != null ? ", KOTH arenasi: " + arenaOrigin : ""));
    }

    /** Arenanin warp'i, hologrami, yapimci kafalari ve tabelalari. */
    private void finishArena(CommandSender sender) {
        Integrations integrations = plugin.integrations();
        boolean warp = integrations.setWarp("koth", arenaLayout.lobby.toLocation(arenaWorld, ax, ay, az));
        int holograms = 0;
        for (Map.Entry<String, IslandLayout.Hologram> hologram : arenaLayout.holograms.entrySet()) {
            if (integrations.createHologram(hologram.getKey(), hologram.getValue().position().toLocation(arenaWorld, ax, ay, az),
                    hologram.getValue().lines())) {
                holograms++;
            }
        }
        int heads = 0;
        for (ArenaLayout.Head head : arenaLayout.heads) {
            try {
                if (arenaWorld.getBlockAt(ax + head.x(), ay + head.y(), az + head.z()).getState() instanceof Skull skull) {
                    skull.setProfile(ResolvableProfile.resolvableProfile()
                            .uuid(java.util.UUID.fromString(head.uuid()))
                            .name(head.name())
                            .addProperty(new ProfileProperty("textures", head.texture(), head.signature().isEmpty() ? null : head.signature()))
                            .build());
                    skull.update(true, false);
                    heads++;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Yapimci kafasi konamadi (" + head.name() + "): " + e);
            }
        }
        int signs = 0;
        for (ArenaLayout.SignText text : arenaLayout.signs) {
            try {
                Block block = arenaWorld.getBlockAt(ax + text.x(), ay + text.y(), az + text.z());
                block.setBlockData(parseBlock(text.block()), false);
                if (block.getState() instanceof Sign sign) {
                    SignSide side = sign.getSide(Side.FRONT);
                    for (int i = 0; i < 4 && i < text.lines().size(); i++) {
                        side.line(i, Messages.color(text.lines().get(i)));
                    }
                    sign.setWaxed(true);
                    sign.update(true, false);
                    signs++;
                }
            } catch (RuntimeException e) {
                plugin.getLogger().warning("Tabela yazilamadi: " + e);
            }
        }
        plugin.messages().send(sender, "spawn-adasi.mesajlar.arena-bitti", "warp", warp ? "/warp koth" : "-",
                "hologram", holograms, "kafa", heads + "/" + arenaLayout.heads.size(), "tabela", signs + "/" + arenaLayout.signs.size());
    }

    // ---- Koruma ----

    private boolean guarded(Location location) {
        return protect && inZone(location);
    }

    private boolean guarded(Block block) {
        if (!protect) {
            return false;
        }
        if (world != null && block.getWorld() == world && area.contains(block.getX(), block.getY(), block.getZ())) {
            return true;
        }
        return arenaWorld != null && block.getWorld() == arenaWorld && arenaArea.contains(block.getX(), block.getY(), block.getZ());
    }

    private boolean deny(Player player, Location location) {
        if (!guarded(location) || player.hasPermission(EDIT)) {
            return false;
        }
        plugin.messages().actionBar(player, "spawn-adasi.mesajlar.korumali");
        return true;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (deny(event.getPlayer(), event.getBlock().getLocation())) {
            event.setCancelled(true);
        }
    }

    @SuppressWarnings("deprecation") // Material#isInteractable: hala dogru calisiyor, yerine gecen API yok
    @EventHandler(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        Player player = event.getPlayer();
        if (block == null || !guarded(block) || player.hasPermission(EDIT)) {
            return;
        }
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true); // tarla ezme, basinc plakasi
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        if (block.getType().isInteractable()) {
            event.setUseInteractedBlock(Event.Result.DENY);
        }
        ItemStack item = event.getItem();
        if (item != null && !item.getType().isEdible() && !USABLE.contains(item.getType())) {
            event.setUseItemInHand(Event.Result.DENY); // kemik tozu, capa, cakmak, yumurta vb.
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFertilize(BlockFertilizeEvent event) {
        if (guarded(event.getBlock()) && (event.getPlayer() == null || !event.getPlayer().hasPermission(EDIT))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityPlace(EntityPlaceEvent event) {
        Player player = event.getPlayer();
        if (guarded(event.getBlock()) && (player == null || !player.hasPermission(EDIT))) {
            event.setCancelled(true); // tekne, vagonet, zirh askisi
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingPlace(HangingPlaceEvent event) {
        Player player = event.getPlayer();
        if (guarded(event.getBlock()) && (player == null || !player.hasPermission(EDIT))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        if (guarded(event.getEntity().getLocation()) && !(remover instanceof Player player && player.hasPermission(EDIT))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onArmorStand(PlayerArmorStandManipulateEvent event) {
        if (deny(event.getPlayer(), event.getRightClicked().getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (protect && world != null) {
            event.blockList().removeIf(this::guarded);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (protect && world != null) {
            event.blockList().removeIf(this::guarded);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (guarded(event.getBlock()) && !(event.getEntity() instanceof Player player && player.hasPermission(EDIT))) {
            event.setCancelled(true); // enderman, dusen kum, ezilen tarla
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) {
        if (guarded(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) {
        if (guarded(event.getBlock()) && !(event.getPlayer() != null && event.getPlayer().hasPermission(EDIT))) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSpread(BlockSpreadEvent event) {
        if (guarded(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) {
        if (guarded(event.getBlock()) || guarded(event.getToBlock())) {
            event.setCancelled(true); // fiskiye suyu tasmasin
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onForm(BlockFormEvent event) {
        if (guarded(event.getBlock())) {
            event.setCancelled(true); // yuksekte kar yagar, fiskiye donmasin
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onFade(BlockFadeEvent event) {
        if (guarded(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLeavesDecay(LeavesDecayEvent event) {
        if (guarded(event.getBlock())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        Location location = event.getLocation();
        if (!noMobs || !HOSTILE_SPAWNS.contains(event.getSpawnReason().name())) {
            return;
        }
        boolean nearIsland = world != null && location.getWorld() == world && spawnZone.contains(location);
        boolean nearArena = arenaWorld != null && location.getWorld() == arenaWorld && arenaZone.contains(location);
        if (nearIsland || nearArena) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL && falling.remove(victim.getUniqueId()) != null) {
            event.setCancelled(true); // adadan atladi
            landed(victim);
            return;
        }
        if (!protect || !onIsland(victim.getLocation()) || inArena(victim.getLocation())
                || ALWAYS_DAMAGE.contains(event.getCause().name())) {
            return;
        }
        if (noDamage) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!protect || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatModule.attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        boolean victimOnIsland = onIsland(victim.getLocation());
        boolean attackerOnIsland = onIsland(attacker.getLocation());
        if (!victimOnIsland && !attackerOnIsland) {
            return;
        }
        // Adada PvP sadece arenada ve iki oyuncu da arenadayken
        if (!inArena(victim.getLocation()) || !inArena(attacker.getLocation())) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "spawn-adasi.mesajlar.pvp-kapali");
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        if (noHunger && protect && event.getEntity() instanceof Player player && onIsland(player.getLocation())
                && !inArena(player.getLocation()) && event.getFoodLevel() < player.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    // ---- Portallar ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (portals.isEmpty() || (from.getBlockX() == to.getBlockX() && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ())) {
            return;
        }
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        AbsPortal portal = null;
        if (inZone(to)) {
            for (AbsPortal candidate : portals.values()) {
                if (candidate.world() == to.getWorld() && candidate.area().contains(to)) {
                    portal = candidate;
                    break;
                }
            }
        }
        if (portal == null) {
            insidePortal.remove(id);
            return;
        }
        if (portal.name().equals(insidePortal.put(id, portal.name()))) {
            return; // zaten icindeydi
        }
        long now = System.currentTimeMillis();
        Long until = portalCooldown.get(id);
        if ((until != null && until > now) || portal.command().isBlank()) {
            return;
        }
        portalCooldown.put(id, now + 3000);
        AbsPortal entered = portal;
        // Hareket olayinin icinde isinlamamak icin bir tick sonra
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) {
                Messages.sound(player, "block.beacon.activate", 1.6f);
                player.performCommand(entered.command());
            }
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        insidePortal.remove(id);
        portalCooldown.remove(id);
        falling.remove(id);
        lastOnIsland.remove(id);
        if (slowed.remove(id)) {
            event.getPlayer().removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }

    private void particles() {
        if (!portalEffects) {
            return;
        }
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (AbsPortal portal : portals.values()) {
            Box box = portal.area();
            World portalWorld = portal.world();
            Location center = box.center(portalWorld);
            if (portalWorld.getNearbyPlayers(center, 40).isEmpty()) {
                continue;
            }
            boolean wild = !"market".equals(portal.command());
            int count = wild ? 30 : 6;
            for (int i = 0; i < count; i++) {
                double x = box.minX() + random.nextDouble() * box.sizeX();
                double y = box.minY() + random.nextDouble() * (wild ? box.sizeY() : 0.4);
                double z = box.minZ() + random.nextDouble() * box.sizeZ();
                portalWorld.spawnParticle(wild ? Particle.PORTAL : Particle.HAPPY_VILLAGER, x, y, z, 1, 0, 0, 0, 0);
            }
            if (wild) {
                portalWorld.spawnParticle(Particle.END_ROD, center, 2, box.sizeX() / 3.0, box.sizeY() / 3.0, 0.2, 0.01);
            }
        }
    }

    // ---- Parasut: adadan atlayanlar ----

    private void tick() {
        if (!parachute || (world == null && arenaWorld == null)) {
            falling.clear();
            return;
        }
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            Location at = player.getLocation();
            World here = at.getWorld();
            if (here != world && here != arenaWorld) {
                continue;
            }
            if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR
                    || player.isFlying() || player.isGliding()) {
                stopFalling(player);
                continue;
            }
            if (inZone(at)) {
                lastOnIsland.put(id, now);
            }
            Long since = falling.get(id);
            if (since == null) {
                // Adadan ya da arenadan yeni ayrilmis (son 5 sn), disarida ve dusuyor
                Long seen = lastOnIsland.get(id);
                if (seen != null && now - seen < 5000 && !inZone(at) && player.getFallDistance() > 2 && !standing(player)) {
                    falling.put(id, now);
                    plugin.messages().actionBar(player, "spawn-adasi.mesajlar.parasut");
                    Messages.sound(player, "entity.phantom.flap", 1.2f);
                }
                continue;
            }
            if (standing(player) || player.isInWater() || now - since > 60_000) {
                if (falling.remove(id) != null) {
                    landed(player);
                }
                continue;
            }
            here.spawnParticle(Particle.CLOUD, at.clone().add(0, 0.2, 0), 3, 0.3, 0.1, 0.3, 0.01);
            int ground = here.getHighestBlockYAt(at.getBlockX(), at.getBlockZ(), HeightMap.MOTION_BLOCKING);
            if (!slowed.contains(id) && at.getY() - ground <= parachuteHeight) {
                slowed.add(id);
                player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0, true, false, true));
                Vector velocity = player.getVelocity();
                player.setVelocity(velocity.setY(Math.max(velocity.getY(), -0.5)));
                plugin.messages().actionBar(player, "spawn-adasi.mesajlar.parasut-acildi");
            }
        }
    }

    private static boolean standing(Player player) {
        Location feet = player.getLocation();
        return feet.clone().subtract(0, 0.2, 0).getBlock().getType().isSolid();
    }

    private void stopFalling(Player player) {
        falling.remove(player.getUniqueId());
        if (slowed.remove(player.getUniqueId())) {
            player.removePotionEffect(PotionEffectType.SLOW_FALLING);
        }
    }

    private void landed(Player player) {
        stopFalling(player);
        plugin.messages().actionBar(player, "spawn-adasi.mesajlar.inis");
        Messages.sound(player, "entity.player.levelup", 1.4f);
    }
}
