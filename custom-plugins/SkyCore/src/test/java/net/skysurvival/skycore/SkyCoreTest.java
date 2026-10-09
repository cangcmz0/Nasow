package net.skysurvival.skycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.BlockFace;
import org.bukkit.damage.DamageSource;
import org.bukkit.damage.DamageType;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.ServicePriority;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** MockBukkit ile sahte sunucuda SkyCore ozelliklerinin testi. */
@SuppressWarnings("deprecation")
class SkyCoreTest extends TestBase {
    @Test
    void joinShowsDailyReminder() {
        PlayerMock ali = server.addPlayer("Ali");
        assertTrue(saw(messages(ali), "Bugünkü ödülünü almadın"), "giris hatirlatmasi");
    }

    @Test
    void combatBlocksTeleportAndKillsOnLogout() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        hit(ali, veli);
        assertTrue(saw(messages(ali), "Savaşa girdin"), "saldiran savasa girer");
        assertTrue(saw(messages(veli), "Savaşa girdin"), "vurulan savasa girer");

        PlayerCommandPreprocessEvent spawn = new PlayerCommandPreprocessEvent(veli, "/spawn");
        server.getPluginManager().callEvent(spawn);
        assertTrue(spawn.isCancelled(), "/spawn engellenir");
        PlayerCommandPreprocessEvent namespaced = new PlayerCommandPreprocessEvent(veli, "/essentials:home ev");
        server.getPluginManager().callEvent(namespaced);
        assertTrue(namespaced.isCancelled(), "/essentials:home engellenir");
        PlayerCommandPreprocessEvent msg = new PlayerCommandPreprocessEvent(veli, "/msg Ali selam");
        server.getPluginManager().callEvent(msg);
        assertFalse(msg.isCancelled(), "/msg serbest");

        veli.disconnect();
        assertTrue(veli.isDead() || veli.getHealth() == 0.0, "savastan kacan olur");
    }

    @Test
    void bypassPermissionIsNeverTagged() {
        PlayerMock admin = server.addPlayer("Admin");
        admin.addAttachment(plugin, CombatModule.BYPASS, true);
        PlayerMock veli = server.addPlayer("Veli");
        messages(admin);
        hit(admin, veli);
        assertFalse(saw(messages(admin), "Savaşa girdin"));
        PlayerCommandPreprocessEvent spawn = new PlayerCommandPreprocessEvent(admin, "/spawn");
        server.getPluginManager().callEvent(spawn);
        assertFalse(spawn.isCancelled());
    }

    @Test
    void chatGameRewardsFirstCorrectAnswer() {
        PlayerMock ali = server.addPlayer("Ali");
        server.addPlayer("Veli");
        assertTrue(plugin.chatGames().startGame(true));
        String answer = plugin.chatGames().currentAnswer();
        assertNotNull(answer);
        messages(ali);
        ali.chat("yanlis cevap 123");
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(2);
        assertNotNull(plugin.chatGames().currentAnswer(), "yanlis cevap oyunu bitirmez");

        ali.chat(answer.toUpperCase(java.util.Locale.forLanguageTag("tr")));
        server.getScheduler().waitAsyncEventsFinished();
        server.getScheduler().performTicks(2);
        assertNull(plugin.chatGames().currentAnswer(), "dogru cevap oyunu bitirir");
        assertEquals(250.0, balances.get(ali.getUniqueId()), 0.001);
        assertTrue(saw(messages(ali), "Ali kazandı"));
    }

    @Test
    void chatGameNormalizesTurkishLetters() {
        assertEquals(ChatGameModule.normalize("kilic"), ChatGameModule.normalize("KILIÇ"));
        assertEquals(ChatGameModule.normalize("zumrut"), ChatGameModule.normalize(" Zümrüt "));
        assertEquals(ChatGameModule.normalize("isik"), ChatGameModule.normalize("IŞIK"));
    }

    @Test
    void banknoteWithdrawAndRedeem() {
        PlayerMock ali = server.addPlayer("Ali");
        balances.put(ali.getUniqueId(), 1000.0);
        assertTrue(ali.performCommand("banknot 250"));
        assertEquals(750.0, balances.get(ali.getUniqueId()), 0.001);
        ItemStack note = null;
        for (ItemStack item : ali.getInventory().getContents()) {
            if (item != null && item.getType() == Material.PAPER) {
                note = item;
            }
        }
        assertNotNull(note, "banknot envantere gelir");
        assertEquals(250.0, noteValue(note), 0.001);

        ali.getInventory().setItemInMainHand(note);
        PlayerInteractEvent use = new PlayerInteractEvent(ali, Action.RIGHT_CLICK_AIR, note, null, BlockFace.SELF, EquipmentSlot.HAND);
        server.getPluginManager().callEvent(use);
        assertEquals(1000.0, balances.get(ali.getUniqueId()), 0.001);
        ItemStack hand = ali.getInventory().getItemInMainHand();
        assertTrue(hand == null || hand.getType() == Material.AIR || hand.getAmount() == 0, "banknot harcanir");

        assertTrue(ali.performCommand("banknot 5000"));
        assertEquals(1000.0, balances.get(ali.getUniqueId()), 0.001, "yetersiz bakiye");
        assertTrue(ali.performCommand("banknot -5"));
        assertEquals(1000.0, balances.get(ali.getUniqueId()), 0.001, "negatif miktar");
    }

    private double noteValue(ItemStack item) {
        BanknoteModule module = new BanknoteModule(plugin);
        Double value = module.noteValue(item);
        return value == null ? -1 : value;
    }

    @Test
    void parseAmountFormats() {
        assertEquals(1000, BanknoteModule.parseAmount("1000"), 0.001);
        assertEquals(1000, BanknoteModule.parseAmount("1.000"), 0.001);
        assertEquals(2.5, BanknoteModule.parseAmount("2,5"), 0.001);
        assertEquals(1.5, BanknoteModule.parseAmount("1.5"), 0.001);
        assertEquals(5000, BanknoteModule.parseAmount("5k"), 0.001);
        assertEquals(1_000_000, BanknoteModule.parseAmount("1m"), 0.001);
        assertEquals(-1, BanknoteModule.parseAmount("abc"), 0.001);
        assertEquals(-1, BanknoteModule.parseAmount("Infinity"), 0.001);
    }

    @Test
    void bountyPaysKiller() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        PlayerMock avci = server.addPlayer("Avci");
        veli.setAddress(new InetSocketAddress("10.0.0.1", 50000));
        avci.setAddress(new InetSocketAddress("10.0.0.2", 50000));
        balances.put(ali.getUniqueId(), 1000.0);
        assertTrue(ali.performCommand("kelle koy Veli 600"));
        assertEquals(400.0, balances.get(ali.getUniqueId()), 0.001);
        assertEquals(600.0, plugin.data().bounty(veli.getUniqueId()), 0.001);
        assertTrue(ali.performCommand("kelle koy Ali 100"));
        assertEquals(400.0, balances.get(ali.getUniqueId()), 0.001, "kendine konamaz");

        veli.setKiller(avci);
        server.getPluginManager().callEvent(death(veli, new ArrayList<>()));
        assertEquals(600.0, balances.get(avci.getUniqueId()), 0.001, "olduren odulu alir");
        assertEquals(0.0, plugin.data().bounty(veli.getUniqueId()), 0.001);
    }

    @Test
    void bountySameIpGetsNothing() {
        PlayerMock veli = server.addPlayer("Veli");
        PlayerMock avci = server.addPlayer("Avci");
        veli.setAddress(new InetSocketAddress("10.0.0.7", 50000));
        avci.setAddress(new InetSocketAddress("10.0.0.7", 50001));
        plugin.data().setBounty(veli.getUniqueId(), "Veli", 500);
        veli.setKiller(avci);
        server.getPluginManager().callEvent(death(veli, new ArrayList<>()));
        assertEquals(0.0, balances.getOrDefault(avci.getUniqueId(), 0.0), 0.001);
        assertEquals(500.0, plugin.data().bounty(veli.getUniqueId()), 0.001);
    }

    @Test
    void headDropsOnPvpDeathAndCoordinatesAreSent() {
        PlayerMock veli = server.addPlayer("Veli");
        PlayerMock avci = server.addPlayer("Avci");
        messages(veli);
        veli.setKiller(avci);
        List<ItemStack> drops = new ArrayList<>();
        server.getPluginManager().callEvent(death(veli, drops));
        assertTrue(drops.stream().anyMatch(i -> i.getType() == Material.PLAYER_HEAD), "kafa duser");
        assertTrue(saw(messages(veli), "Öldüğün yer"));
    }

    @Test
    void dailyRewardStreak() {
        PlayerMock ali = server.addPlayer("Ali");
        assertTrue(ali.performCommand("odul"));
        assertEquals(100.0, balances.get(ali.getUniqueId()), 0.001);
        assertTrue(ali.performCommand("odul"));
        assertEquals(100.0, balances.get(ali.getUniqueId()), 0.001, "gunde bir kez");

        // Dun alinmis gibi yap: seri 2 olur, odul 150.
        String yesterday = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).minusDays(1).toString();
        plugin.data().setDaily(ali.getUniqueId(), "Ali", yesterday, 1);
        assertTrue(ali.performCommand("odul"));
        assertEquals(250.0, balances.get(ali.getUniqueId()), 0.001);
        assertEquals(2, plugin.data().dailyStreak(ali.getUniqueId()));

        // Bir gun kacirildi: seri sifirlanir.
        String twoDaysAgo = java.time.LocalDate.now(java.time.ZoneId.of("Europe/Istanbul")).minusDays(2).toString();
        plugin.data().setDaily(ali.getUniqueId(), "Ali", twoDaysAgo, 5);
        assertTrue(ali.performCommand("odul"));
        assertEquals(350.0, balances.get(ali.getUniqueId()), 0.001);
        assertEquals(1, plugin.data().dailyStreak(ali.getUniqueId()));
    }

    @Test
    void playtimeRewardForActivePlayersOnly() {
        plugin.getConfig().set("aktiflik-odulu.dakika", 2);
        PlayerMock ali = server.addPlayer("Ali");
        PlaytimeModule playtime = new PlaytimeModule(plugin);
        playtime.tick();
        ali.teleport(ali.getLocation().add(5, 0, 0));
        playtime.tick();
        assertEquals(500.0, balances.getOrDefault(ali.getUniqueId(), 0.0), 0.001, "2 aktif dakikada odul");
    }

    @Test
    void announcerAndAdminCommands() {
        PlayerMock ali = server.addPlayer("Ali");
        ali.setOp(true);
        messages(ali);
        plugin.announcer().announceNext();
        assertTrue(saw(messages(ali), "İPUCU"));
        assertTrue(ali.performCommand("skycore reload"));
        assertTrue(saw(messages(ali), "yeniden yüklendi"));
        assertTrue(ali.performCommand("discord"));
        assertTrue(saw(messages(ali), "DISCORD"));
    }

    @Test
    void reloadKeepsWorking() {
        plugin.reload();
        plugin.reload();
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        hit(ali, veli);
        PlayerCommandPreprocessEvent spawn = new PlayerCommandPreprocessEvent(veli, "/spawn");
        server.getPluginManager().callEvent(spawn);
        assertTrue(spawn.isCancelled());
    }
}
