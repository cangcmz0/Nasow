package net.skysurvival.skycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Mezar, toplama kolayliklari, gunluk gorevler, yeni oyuncu korumasi ve takas testleri. */
@SuppressWarnings("deprecation")
class SurvivalTest extends TestBase {
    private World world;

    @BeforeEach
    void createWorld() {
        world = server.getWorld("world") != null ? server.getWorld("world") : server.addSimpleWorld("world");
    }

    private EntityDamageByEntityEvent pvp(PlayerMock attacker, PlayerMock victim) {
        DamageSource source = DamageSource.builder(DamageType.PLAYER_ATTACK).withCausingEntity(attacker).withDirectEntity(attacker).build();
        EntityDamageByEntityEvent event = new EntityDamageByEntityEvent(attacker, victim, EntityDamageEvent.DamageCause.ENTITY_ATTACK, source, 2.0);
        server.getPluginManager().callEvent(event);
        return event;
    }

    private void rightClick(PlayerMock player, Block block) {
        server.getPluginManager().callEvent(new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK,
                player.getInventory().getItemInMainHand(), block, BlockFace.UP, EquipmentSlot.HAND));
    }

    // ---- Mezar ----

    @Test
    void graveKeepsItemsUntilOwnerOpensIt() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        ali.teleport(new Location(world, 10.5, 40, 10.5));
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 5), new ItemStack(Material.IRON_HELMET)));
        var event = death(ali, drops);
        server.getPluginManager().callEvent(event);
        assertTrue(event.getDrops().isEmpty(), "esyalar yere dusmez");
        assertEquals(1, plugin.graves().count());
        Block grave = world.getBlockAt(10, 40, 10);
        assertEquals(Material.PLAYER_HEAD, grave.getType(), "mezar konur");

        messages(veli);
        rightClick(veli, grave);
        assertTrue(saw(messages(veli), "Ali oyuncusuna ait"), "baskasi acamaz");
        assertEquals(Material.PLAYER_HEAD, grave.getType());
        BlockBreakEvent breakIt = new BlockBreakEvent(grave, veli);
        server.getPluginManager().callEvent(breakIt);
        assertTrue(breakIt.isCancelled(), "mezar kirilamaz");

        rightClick(ali, grave);
        assertTrue(ali.getInventory().contains(Material.DIAMOND, 5), "esyalar geri gelir");
        assertEquals(Material.IRON_HELMET, ali.getInventory().getHelmet().getType(), "zirh giydirilir");
        assertEquals(Material.AIR, grave.getType());
        assertEquals(0, plugin.graves().count());
    }

    @Test
    void pvpDeathDropsNormally() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        ali.setKiller(veli);
        List<ItemStack> drops = new ArrayList<>(List.of(new ItemStack(Material.DIAMOND, 5)));
        var event = death(ali, drops);
        server.getPluginManager().callEvent(event);
        assertFalse(event.getDrops().isEmpty());
        assertEquals(0, plugin.graves().count());
    }

    // ---- Toplama ----

    @Test
    void sneakingWithAxeFellsNaturalTree() {
        PlayerMock ali = server.addPlayer("Ali");
        for (int y = 40; y < 45; y++) {
            world.getBlockAt(0, y, 0).setType(Material.OAK_LOG);
        }
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                Block leaf = world.getBlockAt(x, 45, z);
                leaf.setType(Material.OAK_LEAVES);
                Leaves data = (Leaves) leaf.getBlockData();
                data.setPersistent(false);
                leaf.setBlockData(data);
            }
        }
        ali.getInventory().setItemInMainHand(new ItemStack(Material.IRON_AXE));
        ali.setSneaking(true);
        Block bottom = world.getBlockAt(0, 40, 0);
        BlockBreakEvent event = new BlockBreakEvent(bottom, ali);
        server.getPluginManager().callEvent(event);
        assertEquals(Material.AIR, world.getBlockAt(0, 44, 0).getType(), "ustteki kutukler de kirilir");
        assertTrue(((org.bukkit.inventory.meta.Damageable) ali.getInventory().getItemInMainHand().getItemMeta()).getDamage() >= 4,
                "balta her kutuk icin asinir");

        // Oyuncu yapisi (dogal yaprak yok): sadece kirilan blok gider
        for (int y = 40; y < 45; y++) {
            world.getBlockAt(5, y, 0).setType(Material.OAK_LOG);
        }
        server.getPluginManager().callEvent(new BlockBreakEvent(world.getBlockAt(5, 40, 0), ali));
        assertEquals(Material.OAK_LOG, world.getBlockAt(5, 44, 0).getType(), "kutuk duvar devrilmez");
    }

    @Test
    void rightClickHarvestReplants() {
        PlayerMock ali = server.addPlayer("Ali");
        Block crop = world.getBlockAt(3, 41, 3);
        world.getBlockAt(3, 40, 3).setType(Material.FARMLAND);
        crop.setType(Material.WHEAT);
        Ageable data = (Ageable) crop.getBlockData();
        data.setAge(data.getMaximumAge());
        crop.setBlockData(data);
        rightClick(ali, crop);
        assertEquals(Material.WHEAT, crop.getType(), "ekin yerinde kalir");
        assertEquals(0, ((Ageable) crop.getBlockData()).getAge(), "yeniden ekilir");
    }

    // ---- Gorevler ----

    @Test
    void miningQuestIgnoresPlacedBlocks() {
        PlayerMock ali = server.addPlayer("Ali");
        String today = plugin.quests().today().toString();
        plugin.data().setQuests(ali.getUniqueId(), "Ali", today, List.of("komur-avcisi"));
        Block ore = world.getBlockAt(1, 30, 1);
        ore.setType(Material.COAL_ORE);
        server.getPluginManager().callEvent(new BlockBreakEvent(ore, ali));
        assertEquals(1, plugin.data().questProgress(ali.getUniqueId(), "komur-avcisi"), "dogal maden sayilir");

        Block placed = world.getBlockAt(2, 30, 1);
        placed.setType(Material.COAL_ORE);
        server.getPluginManager().callEvent(new BlockPlaceEvent(placed, placed.getState(), world.getBlockAt(2, 29, 1),
                new ItemStack(Material.COAL_ORE), ali, true, EquipmentSlot.HAND));
        server.getPluginManager().callEvent(new BlockBreakEvent(placed, ali));
        assertEquals(1, plugin.data().questProgress(ali.getUniqueId(), "komur-avcisi"), "konan blok sayilmaz");

        for (int i = 0; i < 23; i++) {
            Block next = world.getBlockAt(10 + i, 30, 5);
            next.setType(Material.COAL_ORE);
            server.getPluginManager().callEvent(new BlockBreakEvent(next, ali));
        }
        assertEquals(24, plugin.data().questProgress(ali.getUniqueId(), "komur-avcisi"));
        assertEquals(250.0 + 1000.0, balances.getOrDefault(ali.getUniqueId(), 0.0), 0.001, "gorev odulu + tek gorev oldugu icin bonus");
        assertEquals("1/1", plugin.quests().summary(ali));
    }

    @Test
    void dailyQuestsAreAssigned() {
        PlayerMock ali = server.addPlayer("Ali");
        assertEquals(3, plugin.quests().questsOf(ali).size());
        assertTrue(ali.performCommand("gorev"));
        assertTrue(saw(messages(ali), "GÜNLÜK GÖREVLER"));
    }

    // ---- Yeni oyuncu korumasi ----

    @Test
    void newPlayersAreProtectedFromPvp() {
        plugin.getConfig().set("yeni-oyuncu-korumasi.aktif", true);
        plugin.newbies().start();
        PlayerMock yeni = server.addPlayer("Yeni");
        PlayerMock eski = server.addPlayer("Eski");
        eski.setStatistic(Statistic.PLAY_ONE_MINUTE, 1200 * 120);
        assertTrue(plugin.newbies().isProtected(yeni));
        assertFalse(plugin.newbies().isProtected(eski));
        assertTrue(pvp(eski, yeni).isCancelled(), "yeni oyuncuya vurulamaz");
        assertTrue(pvp(yeni, eski).isCancelled(), "yeni oyuncu vuramaz");
        assertTrue(yeni.performCommand("koruma kapat onayla"));
        assertFalse(plugin.newbies().isProtected(yeni));
        assertFalse(pvp(eski, yeni).isCancelled());
    }

    // ---- Takas ----

    private void click(PlayerMock player, int slot) {
        server.getPluginManager().callEvent(new InventoryClickEvent(player.getOpenInventory(), InventoryType.SlotType.CONTAINER, slot,
                ClickType.LEFT, InventoryAction.PICKUP_ALL));
    }

    @Test
    void tradeSwapsItemsWhenBothConfirm() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        assertTrue(ali.performCommand("takas Veli"));
        assertTrue(veli.performCommand("takas kabul"));
        TradeModule.Trade trade = plugin.trades().tradeOf(ali);
        assertInstanceOf(TradeModule.View.class, ali.getOpenInventory().getTopInventory().getHolder());
        trade.viewA.inventory.setItem(0, new ItemStack(Material.DIAMOND, 3));
        trade.viewB.inventory.setItem(10, new ItemStack(Material.EMERALD, 7));
        plugin.trades().sync(trade);
        assertEquals(Material.DIAMOND, trade.viewB.inventory.getItem(5).getType(), "karsi taraf teklifi gorur");
        click(ali, TradeModule.CONFIRM);
        click(veli, TradeModule.CONFIRM);
        assertNull(plugin.trades().tradeOf(ali), "takas bitti");
        assertTrue(ali.getInventory().contains(Material.EMERALD, 7));
        assertTrue(veli.getInventory().contains(Material.DIAMOND, 3));
        assertFalse(ali.getInventory().contains(Material.DIAMOND), "kopyalanmaz");
    }

    @Test
    void closingTradeReturnsItems() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        assertTrue(ali.performCommand("takas Veli"));
        assertTrue(veli.performCommand("takas kabul"));
        TradeModule.Trade trade = plugin.trades().tradeOf(ali);
        trade.viewA.inventory.setItem(0, new ItemStack(Material.GOLD_INGOT, 4));
        plugin.trades().sync(trade);
        // Karsi tarafin kopyasini almaya calismak engellenir
        InventoryClickEvent steal = new InventoryClickEvent(veli.getOpenInventory(), InventoryType.SlotType.CONTAINER, 5,
                ClickType.LEFT, InventoryAction.PICKUP_ALL);
        server.getPluginManager().callEvent(steal);
        assertTrue(steal.isCancelled(), "karsi tarafin esyasi alinamaz");
        ali.closeInventory();
        assertNull(plugin.trades().tradeOf(veli));
        assertTrue(ali.getInventory().contains(Material.GOLD_INGOT, 4), "esya sahibine doner");
        assertFalse(veli.getInventory().contains(Material.GOLD_INGOT));
    }
}
