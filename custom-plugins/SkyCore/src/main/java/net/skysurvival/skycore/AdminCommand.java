package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

/** /skycore reload | oyun | duyuru */
final class AdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "oyun", "duyuru");

    private final SkyCore plugin;

    AdminCommand(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                plugin.reload();
                plugin.messages().send(sender, "genel-mesajlar.yeniden-yuklendi");
            }
            case "oyun" -> {
                if (!plugin.chatGames().startGame(true)) {
                    sender.sendMessage(Messages.color("&cŞu an zaten bir oyun var ya da sohbet oyunları kapalı/kelime listesi boş."));
                }
            }
            case "duyuru" -> plugin.announcer().announceNext();
            default -> plugin.messages().sendLines(sender, "genel-mesajlar.admin-yardim");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String option : SUBCOMMANDS) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
        }
        return result;
    }
}
