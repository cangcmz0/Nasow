package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * /discord ve /site: config.yml'deki "bilgi" satirlarini yazar. /discord link gibi alt komutlar DiscordSRV
 * aciksa ona (/discordsrv) iletilir; DiscordSRV bot token'i girilmeden kendini kapattiginda da /discord calisir.
 */
final class InfoCommand implements CommandExecutor, TabCompleter {
    private static final List<String> DISCORD_SUBCOMMANDS = List.of("link", "linked", "unlink");

    private final SkyCore plugin;

    InfoCommand(SkyCore plugin) {
        this.plugin = plugin;
    }

    private boolean discordSrvEnabled() {
        Plugin discordSrv = plugin.getServer().getPluginManager().getPlugin("DiscordSRV");
        return discordSrv != null && discordSrv.isEnabled();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        boolean discord = command.getName().equals("discord");
        if (discord && args.length > 0) {
            if (!discordSrvEnabled()) {
                plugin.messages().send(sender, "bilgi.discord-bagli-degil");
            } else if (sender instanceof Player player) {
                player.performCommand("discordsrv " + String.join(" ", args));
            } else {
                plugin.getServer().dispatchCommand(sender, "discordsrv " + String.join(" ", args));
            }
            return true;
        }
        plugin.messages().sendLines(sender, "bilgi." + command.getName());
        if (discord && discordSrvEnabled()) {
            plugin.messages().sendLines(sender, "bilgi.discord-hesap-baglama");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (command.getName().equals("discord") && args.length == 1 && discordSrvEnabled()) {
            for (String option : DISCORD_SUBCOMMANDS) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
        }
        return result;
    }
}
