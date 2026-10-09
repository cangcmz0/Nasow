package net.skysurvival.skycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Spawn adasi, vahsi doga, market, klan, kasa ve KOTH testleri. */
@SuppressWarnings("deprecation")
class FeaturesTest extends TestBase {
    private static final int OY = 70; // MockBukkit dunyasi 128 blok yuksekliginde
    private World world;

    @BeforeEach
    void createWorld() {
        world = server.getWorld("world") != null ? server.getWorld("world") : server.addSimpleWorld("world");
        plugin.getConfig().set("spawn-adasi.konum.y", OY);
    }

    /** Adayi blok koymadan "kurulmus" sayar (koruma, kasa ve KOTH testleri icin). */
    private void pretendBuilt() {
        plugin.data().setIslandOrigin(new DataStore.Origin("world", 0, OY, 0));
        plugin.spawn().stop();
        plugin.spawn().start();
    }

    private Location at(double x, double y, double z) {
        return new Location(world, x, OY + y, z);
    }

    private int keys(PlayerMock player, String type) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && type.equals(plugin.crates().keyType(item))) {
                count += item.getAmount();
            }
        }
        return count;
    }

    // ---- Spawn adasi ----

    @Test
    void layoutMatchesBlueprint() throws IOException {
        IslandLayout layout = IslandLayout.load(plugin);
        Blueprint blueprint = Blueprint.load(plugin);
        assertEquals(layout.bounds, blueprint.bounds);
        assertTrue(blueprint.solidCount() > 100_000);
        assertEquals(5, layout.warps.size());
        assertEquals(2, layout.portals.size());
        assertEquals(3, layout.crates.size());
        assertEquals(10, layout.holograms.size());
        assertEquals("minecraft:sea_lantern", blueprint.blockAt(0, 5, 0));
        for (IslandLayout.Point crate : layout.crates.values()) {
            assertTrue(blueprint.blockAt((int) crate.x(), (int) crate.y(), (int) crate.z()).startsWith("minecraft:ender_chest"));
        }
        // Spawn ve warp noktalarinda ayak altinda blok, ayak ve kafa hizasinda bosluk olmali
        Map<String, IslandLayout.Point> points = new java.util.LinkedHashMap<>(layout.warps);
        points.put("spawn", layout.spawn);
        for (Map.Entry<String, IslandLayout.Point> entry : points.entrySet()) {
            IslandLayout.Point p = entry.getValue();
            int x = (int) Math.floor(p.x());
            int y = (int) Math.floor(p.y());
            int z = (int) Math.floor(p.z());
            assertNotNull(blueprint.blockAt(x, y - 1, z), entry.getKey() + " zemini");
            assertNull(blueprint.blockAt(x, y, z), entry.getKey() + " ayak");
            assertNull(blueprint.blockAt(x, y + 1, z), entry.getKey() + " kafa");
        }
        // Portal ici bos, KOTH arenanin icinde
        Box portal = layout.portals.get("vahsi-doga").area();
        assertNull(blueprint.blockAt(0, portal.minY(), portal.minZ() + 1));
        assertTrue(layout.arena.contains(layout.koth.minX(), layout.koth.minY(), layout.koth.minZ()));
    }

    @Test
    void installBuildsIslandAndSetsSpawn() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        messages(admin);
        assertTrue(admin.performCommand("skycore kurulum"));
        assertTrue(saw(messages(admin), "kurulum onayla"));
        assertFalse(plugin.spawn().built());
        assertTrue(admin.performCommand("skycore kurulum onayla"));
        server.getScheduler().performTicks(30);
        assertTrue(plugin.spawn().built(), "kurulum biter");
        assertTrue(saw(messages(admin), "Spawn adası kuruldu"));
        assertEquals(Material.SEA_LANTERN, world.getBlockAt(0, OY + 5, 0).getType(), "fiskiye tepesi");
        assertEquals(Material.ENDER_CHEST, world.getBlockAt(-28, OY + 2, 0).getType(), "kasa");
        Location spawn = plugin.spawn().spawnLocation();
        assertEquals(spawn.getBlockX(), world.getSpawnLocation().getBlockX());
        assertEquals(spawn.getBlockY(), world.getSpawnLocation().getBlockY());
        assertEquals(spawn.getBlockZ(), admin.getLocation().getBlockZ(), "yetkili spawna isinlanir");
        assertEquals("world", plugin.data().islandOrigin().world());
    }

    @Test
    void islandIsProtected() {
        pretendBuilt();
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock admin = server.addPlayer("Admin");
        admin.setOp(true);
        Block onIsland = world.getBlockAt(5, OY, 5);
        BlockBreakEvent breakIt = new BlockBreakEvent(onIsland, ali);
        server.getPluginManager().callEvent(breakIt);
        assertTrue(breakIt.isCancelled(), "oyuncu adada blok kiramaz");
        BlockBreakEvent adminBreak = new BlockBreakEvent(onIsland, admin);
        server.getPluginManager().callEvent(adminBreak);
        assertFalse(adminBreak.isCancelled(), "yetkili kirabilir");
        BlockBreakEvent faraway = new BlockBreakEvent(world.getBlockAt(500, 64, 500), ali);
        server.getPluginManager().callEvent(faraway);
        assertFalse(faraway.isCancelled(), "ada disi serbest");

        ali.teleport(at(0.5, 1, 7.5));
        EntityDamageEvent fall = new EntityDamageEvent(ali, EntityDamageEvent.DamageCause.FALL,
                DamageSource.builder(DamageType.FALL).build(), 5);
        server.getPluginManager().callEvent(fall);
        assertTrue(fall.isCancelled(), "adada hasar yok");
    }

    private EntityDamageByEntityEvent pvp(PlayerMock attacker, PlayerMock victim) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2.0);
        server.getPluginManager().callEvent(event);
        return event;
    }

    @Test
    void pvpOnlyInArena() {
        pretendBuilt();
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        ali.teleport(at(0.5, 1, 7.5));
        veli.teleport(at(1.5, 1, 7.5));
        assertTrue(pvp(ali, veli).isCancelled(), "meydanda PvP yok");
        ali.teleport(at(0.5, -3, 30.5));
        veli.teleport(at(1.5, -3, 30.5));
        assertFalse(pvp(ali, veli).isCancelled(), "arenada PvP var");
        ali.teleport(new Location(world, 800, 70, 800));
        veli.teleport(new Location(world, 801, 70, 800));
        assertFalse(pvp(ali, veli).isCancelled(), "ada disinda PvP var");
    }

    @Test
    void portalRunsCommandOnEnter() {
        // MockBukkit arka planda parca yukleyemedigi icin /vahsi'nin calistigini dunya hatasindan anlariz
        plugin.getConfig().set("vahsi-doga.dunya", "olmayan_dunya");
        pretendBuilt();
        PlayerMock ali = server.addPlayer("Ali");
        ali.teleport(at(0.5, 1, -19.5));
        messages(ali);
        PlayerMoveEvent move = new PlayerMoveEvent(ali, at(0.5, 1, -21.5), at(0.5, 1, -23.5));
        server.getPluginManager().callEvent(move);
        server.getScheduler().performTicks(2);
        assertTrue(saw(messages(ali), "'olmayan_dunya' adında bir dünya yok"), "portal /vahsi calistirir");
    }

    @Test
    void wildPointsStayInRing() {
        plugin.getConfig().set("vahsi-doga.min-mesafe", 100);
        plugin.getConfig().set("vahsi-doga.max-mesafe", 300);
        WildModule wild = new WildModule(plugin);
        for (int i = 0; i < 2000; i++) {
            int[] point = wild.randomPoint();
            int distance = Math.max(Math.abs(point[0]), Math.abs(point[1]));
            assertTrue(distance >= 100 && distance <= 300, "mesafe " + distance);
        }
    }

    // ---- Market ----

    @Test
    void marketBuyAndSell() {
        PlayerMock ali = server.addPlayer("Ali");
        balances.put(ali.getUniqueId(), 100.0);
        MarketModule market = plugin.market();
        MarketModule.Offer log = market.offer(Material.OAK_LOG);
        assertNotNull(log);
        assertTrue(market.buy(ali, log, 1));
        assertEquals(92.0, balances.get(ali.getUniqueId()), 0.001);
        assertTrue(ali.getInventory().contains(Material.OAK_LOG, 1));
        assertFalse(market.buy(ali, market.offer(Material.DIAMOND), 1), "para yetmez");
        assertEquals(92.0, balances.get(ali.getUniqueId()), 0.001);

        ali.getInventory().addItem(new ItemStack(Material.IRON_INGOT, 10));
        ItemStack named = new ItemStack(Material.IRON_INGOT, 5);
        ItemMeta meta = named.getItemMeta();
        meta.displayName(Component.text("Hatira"));
        named.setItemMeta(meta);
        ali.getInventory().addItem(named);
        assertTrue(market.sell(ali, market.offer(Material.IRON_INGOT), Integer.MAX_VALUE));
        assertEquals(162.0, balances.get(ali.getUniqueId()), 0.001, "10 demir x 7");
        assertTrue(ali.getInventory().containsAtLeast(named, 5), "isimli esya satilmaz");

        ali.getInventory().addItem(new ItemStack(Material.COAL, 5), new ItemStack(Material.WHEAT, 10));
        assertTrue(ali.performCommand("sat hepsi"));
        assertEquals(162.0 + 10 + 8 + 1.5, balances.get(ali.getUniqueId()), 0.001, "komur + bugday + mese kutugu");
        assertFalse(ali.getInventory().contains(Material.COAL));
    }

    @Test
    void marketPreventsArbitrage() {
        MarketModule.Offer offer = plugin.market().parseOffer("test", "oak_log 8 9");
        assertEquals(4.0, offer.sell(), 0.001);
        assertNull(plugin.market().parseOffer("test", "yok_boyle_esya 1 0"));
        assertNull(plugin.market().parseOffer("test", "dirt bir iki"));
    }

    @Test
    void marketMenuClicks() {
        PlayerMock ali = server.addPlayer("Ali");
        balances.put(ali.getUniqueId(), 1000.0);
        assertTrue(ali.performCommand("market"));
        assertInstanceOf(MarketModule.MarketMenu.class, ali.getOpenInventory().getTopInventory().getHolder());
        click(ali, 11, ClickType.LEFT); // agaclar
        MarketModule.MarketMenu menu = (MarketModule.MarketMenu) ali.getOpenInventory().getTopInventory().getHolder();
        assertEquals("agaclar", menu.category.id());
        click(ali, 0, ClickType.SHIFT_LEFT); // 64 mese kutugu
        assertTrue(ali.getInventory().contains(Material.OAK_LOG, 64));
        assertEquals(1000.0 - 64 * 8, balances.get(ali.getUniqueId()), 0.001);
        click(ali, 0, ClickType.RIGHT); // 1 sat
        assertEquals(1000.0 - 64 * 8 + 1.5, balances.get(ali.getUniqueId()), 0.001);
    }

    private void click(PlayerMock player, int slot, ClickType click) {
        InventoryClickEvent event = new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot, click,
                InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(event);
        assertTrue(event.isCancelled(), "menudeki esya alinamaz");
    }

    // ---- Klan ----

    @Test
    void clanLifecycle() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        balances.put(ali.getUniqueId(), 6000.0);
        assertTrue(ali.performCommand("klan kur a"));
        assertEquals(0, plugin.clans().clanCount(), "kisa isim");
        assertTrue(ali.performCommand("klan kur Kurtlar"));
        assertEquals(1000.0, balances.get(ali.getUniqueId()), 0.001);
        assertEquals("Kurtlar", plugin.clans().clanName(ali.getUniqueId()));
        balances.put(veli.getUniqueId(), 6000.0);
        assertTrue(veli.performCommand("klan kur kurtlar"));
        assertEquals(1, plugin.clans().clanCount(), "ayni isim alinamaz");

        assertTrue(ali.performCommand("klan davet Veli"));
        assertTrue(veli.performCommand("klan kabul"));
        assertTrue(plugin.clans().sameClan(ali.getUniqueId(), veli.getUniqueId()));
        assertTrue(pvp(ali, veli).isCancelled(), "dost atesi yok");

        assertTrue(veli.performCommand("klan banka yatir 500"));
        assertEquals(500.0, plugin.clans().clanOf(ali.getUniqueId()).bank, 0.001);
        assertTrue(veli.performCommand("klan banka cek 500"));
        assertEquals(500.0, plugin.clans().clanOf(ali.getUniqueId()).bank, 0.001, "sadece lider ceker");

        plugin.clans().save();
        ClanModule reloaded = new ClanModule(plugin);
        assertEquals(1, reloaded.clanCount());
        assertTrue(reloaded.sameClan(ali.getUniqueId(), veli.getUniqueId()), "kayit dosyasindan geri gelir");

        assertTrue(veli.performCommand("klan ayril"));
        assertNull(plugin.clans().clanName(veli.getUniqueId()));
        assertFalse(pvp(ali, veli).isCancelled());
        assertTrue(ali.performCommand("klan sil onayla"));
        assertEquals(0, plugin.clans().clanCount());
        assertEquals(1500.0, balances.get(ali.getUniqueId()), 0.001, "klan kasasi lidere doner");
    }

    // ---- Kasalar ----

    @Test
    void keysRoundTripAndDailyGivesKey() {
        PlayerMock ali = server.addPlayer("Ali");
        ItemStack key = plugin.crates().createKey("nadir", 2);
        assertEquals("nadir", plugin.crates().keyType(key));
        assertNull(plugin.crates().keyType(new ItemStack(Material.TRIPWIRE_HOOK)), "sahte anahtar");
        assertTrue(ali.performCommand("odul"));
        assertEquals(1, keys(ali, "gunluk"), "gunluk odul anahtar verir");
    }

    @Test
    void crateOpensWithKey() {
        pretendBuilt();
        PlayerMock ali = server.addPlayer("Ali");
        Location crate = plugin.spawn().crateLocations().get("gunluk");
        Block block = crate.getBlock();
        messages(ali);
        PlayerInteractEvent noKey = new PlayerInteractEvent(ali, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(noKey);
        assertTrue(saw(messages(ali), "anahtarı gerekli"));

        plugin.crates().giveKey(ali, "gunluk", 2);
        double before = balances.getOrDefault(ali.getUniqueId(), 0.0);
        int itemsBefore = countItems(ali);
        messages(ali);
        PlayerInteractEvent open = new PlayerInteractEvent(ali, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(open);
        assertEquals(1, keys(ali, "gunluk"), "bir anahtar harcanir");
        assertInstanceOf(CrateModule.CrateMenu.class, ali.getOpenInventory().getTopInventory().getHolder());
        server.getScheduler().performTicks(120);
        assertTrue(saw(messages(ali), "kazandın"), "cark durunca odul");
        assertTrue(balances.getOrDefault(ali.getUniqueId(), 0.0) > before || countItems(ali) > itemsBefore - 1, "odul verildi");

        // Cark bitmeden kapatilirsa odul hemen verilir
        server.getPluginManager().callEvent(new PlayerInteractEvent(ali, Action.RIGHT_CLICK_BLOCK, null, block, BlockFace.UP, EquipmentSlot.HAND));
        messages(ali);
        ali.closeInventory();
        assertTrue(saw(messages(ali), "kazandın"));
        assertEquals(0, keys(ali, "gunluk"));
    }

    private static int countItems(PlayerMock player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    // ---- KOTH ----

    @Test
    void kothWinnerGetsRewards() {
        plugin.getConfig().set("koth.tutma-saniye", 10);
        pretendBuilt();
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        ali.teleport(at(0.5, -3, 33.5));
        veli.teleport(at(-0.5, -3, 33.5));
        assertTrue(plugin.koth().begin(server.getConsoleSender()));
        server.getScheduler().performTicks(20 * 12);
        assertTrue(plugin.koth().running(), "iki farkli oyuncu varken tepe cekismeli");
        veli.teleport(at(0.5, 1, 7.5));
        server.getScheduler().performTicks(20 * 12);
        assertFalse(plugin.koth().running(), "tek kalan kazanir");
        assertEquals(5000.0, balances.get(ali.getUniqueId()), 0.001);
        assertEquals(1, keys(ali, "efsane"));
        assertEquals(1, plugin.data().kothWins(ali.getUniqueId()));
        assertEquals("Ali", plugin.data().lastKothWinner());
    }

    @Test
    void oldConfigGetsNewSections() throws Exception {
        // Eski surumun config.yml'i: yeni bolumler yok, oyuncunun degistirdigi deger var
        java.io.File file = new java.io.File(plugin.getDataFolder(), "config.yml");
        java.nio.file.Files.writeString(file.toPath(), "onek: \"&c[ESKI] \"\nsavas:\n  sure-saniye: 30\n");
        plugin.reload();
        assertEquals(30, plugin.getConfig().getInt("savas.sure-saniye"), "eski deger korunur");
        assertEquals("&c[ESKI] ", plugin.getConfig().getString("onek"));
        assertTrue(plugin.crates().hasType("efsane"), "yeni bolum eklenir");
        String saved = java.nio.file.Files.readString(file.toPath());
        assertTrue(saved.contains("koth:") && saved.contains("sure-saniye: 30"));
    }

    @Test
    void kothNeedsIsland() {
        assertFalse(plugin.koth().begin(server.getConsoleSender()));
        assertFalse(plugin.koth().running());
    }
}
