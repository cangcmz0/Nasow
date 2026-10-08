package net.skysurvival.skycore;

import java.util.List;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /discord ve /site: config.yml'deki "bilgi" satirlarini yazar. */
final class InfoCommand implements CommandExecutor, TabCompleter {
    private final SkyCore plugin;

    InfoCommand(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        plugin.messages().sendLines(sender, "bilgi." + command.getName());
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
