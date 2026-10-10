package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /skycore reload | oyun | duyuru | kurulum [onayla] | anahtar <oyuncu|herkes> <tur> [adet] | koth <baslat|bitir> */
final class AdminCommand implements CommandExecutor, TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("reload", "oyun", "duyuru", "kurulum", "hologramlar", "anahtar", "koth");

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
            case "kurulum" -> {
                if (args.length > 1 && args[1].equalsIgnoreCase("onayla")) {
                    plugin.spawn().install(sender);
                } else {
                    plugin.spawn().showInstallInfo(sender);
                }
            }
            case "hologramlar" -> {
                int count = plugin.spawn().recreateHolograms();
                if (count < 0) {
                    plugin.messages().send(sender, "spawn-adasi.mesajlar.hologram-yok");
                } else {
                    plugin.messages().send(sender, "spawn-adasi.mesajlar.hologramlar-yenilendi", "adet", count);
                }
            }
            case "anahtar" -> giveKeys(sender, args);
            case "koth" -> {
                String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
                if (action.equals("baslat")) {
                    plugin.koth().begin(sender);
                } else if (action.equals("bitir")) {
                    if (plugin.koth().running()) {
                        plugin.koth().end(null, true);
                    } else {
                        plugin.messages().send(sender, "koth.mesajlar.yok");
                    }
                } else {
                    sender.sendMessage(Messages.color("&cKullanım: &f/skycore koth <baslat|bitir>"));
                }
            }
            default -> plugin.messages().sendLines(sender, "genel-mesajlar.admin-yardim");
        }
        return true;
    }

    private void giveKeys(CommandSender sender, String[] args) {
        CrateModule crates = plugin.crates();
        if (args.length < 3 || !crates.hasType(args[2].toLowerCase(Locale.ROOT))) {
            sender.sendMessage(Messages.color("&cKullanım: &f/skycore anahtar <oyuncu|herkes> <"
                    + String.join("|", crates.typeIds()) + "> [adet]"));
            return;
        }
        String type = args[2].toLowerCase(Locale.ROOT);
        int amount = 1;
        if (args.length > 3) {
            try {
                amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
            } catch (NumberFormatException e) {
                sender.sendMessage(Messages.color("&cAdet bir sayı olmalı."));
                return;
            }
        }
        List<Player> targets = new ArrayList<>();
        if (args[1].equalsIgnoreCase("herkes")) {
            targets.addAll(plugin.getServer().getOnlinePlayers());
        } else {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                plugin.messages().send(sender, "kelle-avi.mesajlar.bulunamadi");
                return;
            }
            targets.add(target);
        }
        for (Player target : targets) {
            crates.giveKey(target, type, amount);
        }
        sender.sendMessage(Messages.color("&a" + targets.size() + " oyuncuya " + amount + "x " + crates.typeName(type) + " &aanahtarı verildi."));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(SUBCOMMANDS);
        } else if (args.length == 2) {
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "kurulum" -> options.add("onayla");
                case "koth" -> options.addAll(List.of("baslat", "bitir"));
                case "anahtar" -> {
                    options.add("herkes");
                    plugin.getServer().getOnlinePlayers().forEach(player -> options.add(player.getName()));
                }
                default -> {
                }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("anahtar")) {
            options.addAll(plugin.crates().typeIds());
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) {
                result.add(option);
            }
        }
        return result;
    }
}
