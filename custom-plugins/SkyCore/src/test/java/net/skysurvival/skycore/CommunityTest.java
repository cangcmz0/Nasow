package net.skysurvival.skycore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.vexsoftware.votifier.model.Vote;
import com.vexsoftware.votifier.model.VotifierEvent;
import java.util.List;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

/** Siralama tablolari ve oy odulleri. */
class CommunityTest extends TestBase {

    private void vote(String name, String site, String ip) {
        server.getPluginManager().callEvent(new VotifierEvent(new Vote(site, name, ip, "0")));
    }

    private int keys(PlayerMock player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && plugin.crates().keyType(item) != null) {
                count += item.getAmount();
            }
        }
        return count;
    }

    // ---- Siralama ----

    @Test
    void richestBoardIsSortedAndHidesStaff() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        PlayerMock admin = server.addPlayer("Admin");
        balances.put(ali.getUniqueId(), 500.0);
        balances.put(veli.getUniqueId(), 9000.0);
        balances.put(admin.getUniqueId(), 1_000_000.0);
        admin.addAttachment(plugin, "skycore.siralama.gizli", true);
        plugin.leaderboards().refresh();

        List<DataStore.Score> top = plugin.leaderboards().top("para");
        assertEquals(2, top.size(), "yetkili gorunmez");
        assertEquals("Veli", top.get(0).name());
        assertEquals("Ali", top.get(1).name());
        assertEquals(2, plugin.leaderboards().rankOf(ali.getUniqueId(), "para"));
        assertEquals(0, plugin.leaderboards().rankOf(admin.getUniqueId(), "para"));
        assertEquals("§e#1 §fVeli §8» §a9000₺", plugin.leaderboards().line("para", 1), "hologram satiri");
        assertEquals("§8#3 -", plugin.leaderboards().line("para", 3), "bos sira");
    }

    @Test
    void offlinePlayerKeepsLastKnownBalance() {
        PlayerMock ali = server.addPlayer("Ali");
        balances.put(ali.getUniqueId(), 4200.0);
        ali.disconnect(); // cikista kaydedilir
        balances.put(ali.getUniqueId(), 0.0); // cevrimdisiyken okunmaz
        plugin.leaderboards().refresh();
        assertEquals(4200.0, plugin.leaderboards().top("para").get(0).value(), 0.001);
    }

    @Test
    void kothQuestAndCommandBoards() {
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        plugin.data().addKothWin(veli.getUniqueId(), "Veli");
        plugin.data().addKothWin(veli.getUniqueId(), "Veli");
        plugin.data().addKothWin(ali.getUniqueId(), "Ali");
        plugin.data().addQuestDone(ali.getUniqueId());
        plugin.leaderboards().refresh();
        assertEquals("Veli", plugin.leaderboards().top("koth").get(0).name());
        assertEquals(1, plugin.leaderboards().top("gorev").size(), "sifir olanlar listede yok");

        messages(ali);
        assertTrue(ali.performCommand("siralama koth"));
        List<String> lines = messages(ali);
        assertTrue(saw(lines, "#1 Veli » 2 zafer"), lines.toString());
        assertTrue(saw(lines, "Senin sıran: #2"), lines.toString());
        assertTrue(ali.performCommand("siralama yok"));
        assertTrue(saw(messages(ali), "Böyle bir sıralama yok"));
    }

    // ---- Oy ----

    @Test
    void onlineVoterGetsRewardOnce() {
        PlayerMock ali = server.addPlayer("Ali");
        assertTrue(plugin.votes().hooked(), "sahte NuVotifier olayina baglandi");
        vote("ali", "Minecraft-MP", "1.2.3.4"); // buyuk/kucuk harf fark etmez
        assertEquals(250.0, balance(ali), 0.001);
        assertEquals(1, keys(ali));
        assertEquals(1, plugin.data().votes(ali.getUniqueId(), plugin.leaderboards().currentMonth()));

        vote("Ali", "Minecraft-MP", "1.2.3.4"); // site ayni oyu iki kez yollarsa
        assertEquals(250.0, balance(ali), 0.001, "ayni siteden bekleme suresi icinde ikinci odul yok");
        vote("Ali", "TopG", "1.2.3.4"); // baska site sayilir
        assertEquals(500.0, balance(ali), 0.001);
        assertEquals(2, plugin.data().totalVotes(ali.getUniqueId()));

        plugin.leaderboards().refresh();
        assertEquals("Ali", plugin.leaderboards().top("oy").get(0).name());
    }

    @Test
    void unknownNamesGetNothing() {
        server.addPlayer("Ali");
        assertFalse(plugin.votes().vote("HicGirmemis", "Site", "1.1.1.1"));
        assertFalse(plugin.votes().vote("", "Site", "1.1.1.1"));
        assertFalse(plugin.votes().vote("a".repeat(40), "Site", "1.1.1.1"));
        assertEquals(0, plugin.data().votePartyCount());
    }

    @Test
    void offlineVoteIsDeliveredOnJoin() {
        PlayerMock ali = server.addPlayer("Ali");
        ali.disconnect();
        vote("Ali", "Site", "1.2.3.4");
        assertEquals(0.0, balance(ali), 0.001, "cevrimdisiyken para verilmez");
        assertEquals(1, plugin.data().pendingVotes(ali.getUniqueId()));
        ali.reconnect();
        assertEquals(250.0, balance(ali), 0.001, "girince odul gelir");
        assertEquals(1, keys(ali));
        assertEquals(0, plugin.data().pendingVotes(ali.getUniqueId()));
        ali.disconnect();
        ali.reconnect();
        assertEquals(250.0, balance(ali), 0.001, "bekleyen odul bir kez verilir");
    }

    @Test
    void sameIpCannotFarmVotesWithAlts() {
        PlayerMock a = server.addPlayer("Alt1");
        PlayerMock b = server.addPlayer("Alt2");
        PlayerMock c = server.addPlayer("Alt3");
        vote("Alt1", "Site", "5.5.5.5");
        vote("Alt2", "Site", "5.5.5.5");
        vote("Alt3", "Site", "5.5.5.5");
        assertEquals(250.0, balance(a), 0.001);
        assertEquals(250.0, balance(b), 0.001);
        assertEquals(0.0, balance(c), 0.001, "ayni IP'den ucuncu hesap odul almaz");
    }

    @Test
    void votePartyRewardsEveryone() {
        plugin.getConfig().set("oy.parti.hedef", 2);
        PlayerMock ali = server.addPlayer("Ali");
        PlayerMock veli = server.addPlayer("Veli");
        PlayerMock ayse = server.addPlayer("Ayse");
        vote("Ali", "Site", "1.1.1.1");
        assertEquals("1/2", plugin.votes().partyProgress());
        vote("Veli", "Site", "2.2.2.2");
        assertEquals("0/2", plugin.votes().partyProgress(), "parti sonrasi sayac sifirlanir");
        assertEquals(1, keys(ayse), "oy vermeyen de parti anahtari alir");
        assertEquals(2, keys(ali), "oy anahtari + parti anahtari");
        assertEquals(2, keys(veli));
    }

    @Test
    void voteCommandListsClickableSites() {
        plugin.getConfig().set("oy.siteler", List.of(java.util.Map.of("isim", "Minecraft-MP", "adres", "https://minecraft-mp.com/server/1/vote/")));
        PlayerMock ali = server.addPlayer("Ali");
        messages(ali);
        assertTrue(ali.performCommand("oy"));
        net.kyori.adventure.text.Component line = null;
        net.kyori.adventure.text.Component message;
        while ((message = ali.nextComponentMessage()) != null) {
            if (net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(message).contains("minecraft-mp.com")) {
                line = message;
            }
        }
        assertTrue(line != null, "site listede");
        assertTrue(hasClick(line, "https://minecraft-mp.com/server/1/vote/"), "baglanti tiklanabilir");
    }

    // ---- Discord ----

    @Test
    void discordCommandWorksWithoutDiscordSrv() {
        PlayerMock ali = server.addPlayer("Ali");
        messages(ali);
        assertTrue(ali.performCommand("discord"));
        net.kyori.adventure.text.Component invite = ali.nextComponentMessage();
        assertTrue(invite != null && hasClickContaining(invite, "discord.gg/BURAYA-DAVET-KODUNU-YAZ"), "davet tiklanabilir");
        assertTrue(ali.performCommand("discord link"));
        assertTrue(saw(messages(ali), "Discord bağlantısı henüz ayarlanmadı"));
        assertFalse(plugin.integrations().sendToDiscord("deneme"), "DiscordSRV yokken sessizce gecer");
        org.mockbukkit.mockbukkit.MockBukkit.createMockPlugin("DiscordSRV");
        assertFalse(plugin.integrations().sendToDiscord("deneme"), "API'si olmayan DiscordSRV hata firlatmaz");
        plugin.announceToDiscord("koth-basladi", "sure", 120);
    }

    private static boolean hasClickContaining(net.kyori.adventure.text.Component component, String part) {
        net.kyori.adventure.text.event.ClickEvent click = component.clickEvent();
        if (click != null && click.value().contains(part)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasClickContaining(child, part));
    }

    private static boolean hasClick(net.kyori.adventure.text.Component component, String url) {
        net.kyori.adventure.text.event.ClickEvent click = component.clickEvent();
        if (click != null && click.value().equals(url)) {
            return true;
        }
        return component.children().stream().anyMatch(child -> hasClick(child, url));
    }
}
