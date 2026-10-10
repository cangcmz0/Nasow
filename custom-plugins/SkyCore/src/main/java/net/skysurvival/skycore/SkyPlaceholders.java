package net.skysurvival.skycore;

import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI yer tutuculari (TAB, DecentHolograms vb. icin):
 * %skycore_klan%, %skycore_klan_etiket%, %skycore_klan_uye%, %skycore_klan_rutbe%, %skycore_gunluk_seri%,
 * %skycore_kelle%, %skycore_koth_galibiyet%, %skycore_koth_son%, %skycore_gorev% (2/3), %skycore_gorev_toplam%,
 * %skycore_koruma% (yeni oyuncu korumasinin kalan suresi).
 * Siralama: %skycore_top_<tablo>_<1-10>% (hazir satir), %skycore_top_<tablo>_<sira>_isim%, ..._deger,
 * %skycore_sira_<tablo>% (oyuncunun sirasi); tablolar: para, sure, gorev, koth, oy.
 * Oy: %skycore_oy_ay%, %skycore_oy_toplam%, %skycore_oy_parti% (12/30).
 * Bu sinif yalnizca PlaceholderAPI kuruluysa yuklenir.
 */
final class SkyPlaceholders extends PlaceholderExpansion {
    private final SkyCore plugin;

    private SkyPlaceholders(SkyCore plugin) {
        this.plugin = plugin;
    }

    static void register(SkyCore plugin) {
        if (new SkyPlaceholders(plugin).register()) {
            plugin.getLogger().info("PlaceholderAPI yer tutuculari eklendi (%skycore_...%).");
        }
    }

    @Override
    public String getIdentifier() {
        return "skycore";
    }

    @Override
    public String getAuthor() {
        return "Sky Survival";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true; // /papi reload sonrasi da kalsin
    }

    @Override
    public List<String> getPlaceholders() {
        return List.of("%skycore_klan%", "%skycore_klan_etiket%", "%skycore_klan_uye%", "%skycore_klan_rutbe%",
                "%skycore_gunluk_seri%", "%skycore_kelle%", "%skycore_koth_galibiyet%", "%skycore_koth_son%",
                "%skycore_gorev%", "%skycore_gorev_toplam%", "%skycore_koruma%", "%skycore_top_para_1%",
                "%skycore_top_para_1_isim%", "%skycore_top_para_1_deger%", "%skycore_sira_para%", "%skycore_oy_ay%",
                "%skycore_oy_toplam%", "%skycore_oy_parti%");
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Messages messages = plugin.messages();
        if (params.equals("koth_son")) {
            String last = plugin.data().lastKothWinner();
            return last.isEmpty() ? "-" : last;
        }
        if (params.equals("oy_parti")) {
            return plugin.votes().partyProgress();
        }
        if (params.startsWith("top_")) {
            return top(params.substring(4));
        }
        if (player == null) {
            return "";
        }
        ClanModule.Clan clan = plugin.clans().clanOf(player.getUniqueId());
        return switch (params) {
            case "klan" -> clan == null ? messages.raw("klan.yok-yazisi") : clan.name;
            case "klan_etiket" -> plugin.clans().tag(player.getUniqueId());
            case "klan_uye" -> clan == null ? "0" : String.valueOf(clan.members.size());
            case "klan_rutbe" -> clan == null ? "" : messages.raw(clan.leader.equals(player.getUniqueId()) ? "klan.rutbe-lider" : "klan.rutbe-uye");
            case "gunluk_seri" -> String.valueOf(plugin.data().dailyStreak(player.getUniqueId()));
            case "kelle" -> plugin.economy().format(plugin.data().bounty(player.getUniqueId()));
            case "koth_galibiyet" -> String.valueOf(plugin.data().kothWins(player.getUniqueId()));
            case "gorev" -> player.getPlayer() == null ? "" : plugin.quests().summary(player.getPlayer());
            case "gorev_toplam" -> String.valueOf(plugin.data().questsDone(player.getUniqueId()));
            case "koruma" -> player.getPlayer() == null || !plugin.newbies().isProtected(player.getPlayer()) ? ""
                    : plugin.newbies().minutesLeft(player.getPlayer()) + " dk";
            case "oy_ay" -> String.valueOf(plugin.data().votes(player.getUniqueId(), plugin.leaderboards().currentMonth()));
            case "oy_toplam" -> String.valueOf(plugin.data().totalVotes(player.getUniqueId()));
            default -> params.startsWith("sira_") && LeaderboardModule.isBoard(params.substring(5))
                    ? rank(plugin.leaderboards().rankOf(player.getUniqueId(), params.substring(5))) : null;
        };
    }

    private static String rank(int rank) {
        return rank > 0 ? "#" + rank : "-";
    }

    /** para_1 -> hazir satir, para_1_isim -> isim, para_1_deger -> bicimli deger. */
    private String top(String params) {
        String[] parts = params.split("_");
        if (parts.length < 2 || !LeaderboardModule.isBoard(parts[0])) {
            return null;
        }
        int rank;
        try {
            rank = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        LeaderboardModule boards = plugin.leaderboards();
        if (parts.length == 2) {
            return boards.line(parts[0], rank);
        }
        List<DataStore.Score> list = boards.top(parts[0]);
        boolean exists = rank >= 1 && rank <= list.size();
        return switch (parts[2]) {
            case "isim" -> exists ? list.get(rank - 1).name() : "-";
            case "deger" -> exists ? boards.format(parts[0], list.get(rank - 1).value()) : "";
            default -> null;
        };
    }
}
