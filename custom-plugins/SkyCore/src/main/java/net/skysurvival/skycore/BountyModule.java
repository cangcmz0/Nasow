package net.skysurvival.skycore;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;

/** Kelle avi: /kelle koy <oyuncu> <miktar>. Odullu oyuncuyu olduren parayi alir. */
final class BountyModule implements Module, CommandExecutor, TabCompleter {
    private final SkyCore plugin;
    private boolean enabled;

    BountyModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("kelle-avi.aktif", true);
    }

    @Override
    public void stop() {
    }

    private OfflinePlayer find(String name) {
        Player online = plugin.getServer().getPlayerExact(name);
        return online != null ? online : plugin.getServer().getOfflinePlayerIfCached(name);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!enabled) {
            messages.send(sender, "genel-mesajlar.yetki-yok");
            return true;
        }
        if (args.length == 0) {
            messages.sendLines(sender, "kelle-avi.mesajlar.kullanim");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("liste") || sub.equals("top")) {
            List<DataStore.Bounty> top = plugin.data().topBounties(10);
            if (top.isEmpty()) {
                messages.send(sender, "kelle-avi.mesajlar.liste-bos");
                return true;
            }
            sender.sendMessage(Messages.color(messages.raw("kelle-avi.mesajlar.liste-baslik")));
            int rank = 1;
            for (DataStore.Bounty bounty : top) {
                sender.sendMessage(Messages.color(messages.raw("kelle-avi.mesajlar.liste-satir",
                        "sira", rank++, "oyuncu", bounty.name(), "miktar", plugin.economy().format(bounty.amount()))));
            }
            return true;
        }
        if (sub.equals("koy") || sub.equals("ekle")) {
            return place(sender, args);
        }
        OfflinePlayer target = find(args[0]);
        if (target == null) {
            messages.send(sender, "kelle-avi.mesajlar.bulunamadi");
            return true;
        }
        messages.send(sender, "kelle-avi.mesajlar.bilgi", "oyuncu", target.getName(),
                "miktar", plugin.economy().format(plugin.data().bounty(target.getUniqueId())));
        return true;
    }

    private boolean place(CommandSender sender, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (args.length != 3) {
            messages.sendLines(sender, "kelle-avi.mesajlar.kullanim");
            return true;
        }
        if (!plugin.economy().available()) {
            messages.send(player, "genel-mesajlar.ekonomi-yok");
            return true;
        }
        OfflinePlayer target = find(args[1]);
        if (target == null || target.getName() == null) {
            messages.send(player, "kelle-avi.mesajlar.bulunamadi");
            return true;
        }
        if (target.getUniqueId().equals(player.getUniqueId())) {
            messages.send(player, "kelle-avi.mesajlar.kendine");
            return true;
        }
        double min = plugin.getConfig().getDouble("kelle-avi.min-miktar", 100);
        double amount = BanknoteModule.parseAmount(args[2]);
        if (amount < min) {
            messages.send(player, "kelle-avi.mesajlar.min", "min", plugin.economy().format(min));
            return true;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            messages.send(player, "kelle-avi.mesajlar.yetersiz");
            return true;
        }
        double total = plugin.data().bounty(target.getUniqueId()) + amount;
        plugin.data().setBounty(target.getUniqueId(), target.getName(), total);
        messages.broadcast("kelle-avi.mesajlar.konuldu", "koyan", player.getName(), "hedef", target.getName(),
                "miktar", plugin.economy().format(amount), "toplam", plugin.economy().format(total));
        return true;
    }

    private static boolean sameAddress(Player a, Player b) {
        InetSocketAddress first = a.getAddress();
        InetSocketAddress second = b.getAddress();
        return first != null && second != null && first.getAddress() != null
                && first.getAddress().equals(second.getAddress());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        if (!enabled) {
            return;
        }
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) {
            return;
        }
        double amount = plugin.data().bounty(victim.getUniqueId());
        if (amount <= 0) {
            return;
        }
        if (plugin.getConfig().getBoolean("kelle-avi.ayni-ip-odul-yok", true) && sameAddress(killer, victim)) {
            plugin.messages().send(killer, "kelle-avi.mesajlar.ayni-ip");
            return;
        }
        if (!plugin.economy().deposit(killer, amount)) {
            return; // ekonomi yoksa odul silinmez, sonra alinabilir
        }
        plugin.data().removeBounty(victim.getUniqueId());
        plugin.messages().broadcast("kelle-avi.mesajlar.alindi", "hedef", victim.getName(), "avci", killer.getName(),
                "miktar", plugin.economy().format(amount));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> result = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("koy", "liste")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    result.add(option);
                }
            }
            addPlayers(result, args[0]);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("koy")) {
            addPlayers(result, args[1]);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("koy")) {
            result.addAll(List.of("500", "1000", "5000"));
        }
        return result;
    }

    private void addPlayers(List<String> result, String prefix) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix.toLowerCase(Locale.ROOT))) {
                result.add(player.getName());
            }
        }
    }
}
