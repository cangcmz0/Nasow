package net.skysurvival.skycore;

import java.util.List;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/**
 * PlaceholderAPI yer tutuculari (TAB, DecentHolograms vb. icin):
 * %skycore_klan%, %skycore_klan_etiket%, %skycore_klan_uye%, %skycore_klan_rutbe%, %skycore_gunluk_seri%,
 * %skycore_kelle%, %skycore_koth_galibiyet%, %skycore_koth_son%.
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
                "%skycore_gunluk_seri%", "%skycore_kelle%", "%skycore_koth_galibiyet%", "%skycore_koth_son%");
    }

    @Override
    public String onRequest(OfflinePlayer player, String params) {
        Messages messages = plugin.messages();
        if (params.equals("koth_son")) {
            String last = plugin.data().lastKothWinner();
            return last.isEmpty() ? "-" : last;
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
            default -> null;
        };
    }
}
