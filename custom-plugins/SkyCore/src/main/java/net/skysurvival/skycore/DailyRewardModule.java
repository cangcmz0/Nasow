package net.skysurvival.skycore;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

/** /odul: gunde bir kez para. Ust uste her gun alinca seri ve odul artar; bir gun kacirilirsa sifirlanir. */
final class DailyRewardModule implements Module, CommandExecutor, TabCompleter {
    private final SkyCore plugin;
    private boolean enabled;
    private ZoneId zone = ZoneId.of("Europe/Istanbul");

    DailyRewardModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("gunluk-odul.aktif", true);
        try {
            zone = ZoneId.of(plugin.getConfig().getString("gunluk-odul.saat-dilimi", "Europe/Istanbul"));
        } catch (DateTimeException e) {
            plugin.getLogger().warning("Gecersiz saat dilimi, Europe/Istanbul kullaniliyor.");
            zone = ZoneId.of("Europe/Istanbul");
        }
    }

    @Override
    public void stop() {
    }

    LocalDate today() {
        return LocalDate.now(zone);
    }

    /** Bugun odul alinirsa ulasilacak seri (dun alindiysa devam, yoksa 1). */
    int nextStreak(UUID id, LocalDate today) {
        String last = plugin.data().lastDaily(id);
        if (last != null && LocalDate.parse(last).plusDays(1).equals(today)) {
            return plugin.data().dailyStreak(id) + 1;
        }
        return 1;
    }

    double rewardFor(int streak) {
        double base = plugin.getConfig().getDouble("gunluk-odul.taban", 100);
        double step = plugin.getConfig().getDouble("gunluk-odul.seri-artis", 50);
        double max = plugin.getConfig().getDouble("gunluk-odul.max", 1000);
        return Math.min(max, base + (streak - 1) * step);
    }

    boolean claimedToday(UUID id) {
        return today().toString().equals(plugin.data().lastDaily(id));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (!enabled) {
            messages.send(player, "genel-mesajlar.yetki-yok");
            return true;
        }
        UUID id = player.getUniqueId();
        LocalDate today = today();
        if (claimedToday(id)) {
            messages.send(player, "gunluk-odul.mesajlar.zaten");
            return true;
        }
        if (!plugin.economy().available()) {
            messages.send(player, "genel-mesajlar.ekonomi-yok");
            return true;
        }
        int streak = nextStreak(id, today);
        if (streak == 1 && plugin.data().dailyStreak(id) > 1) {
            messages.send(player, "gunluk-odul.mesajlar.seri-bozuldu");
        }
        double reward = rewardFor(streak);
        if (!plugin.economy().deposit(player, reward)) {
            messages.send(player, "genel-mesajlar.ekonomi-yok");
            return true;
        }
        plugin.data().setDaily(id, player.getName(), today.toString(), streak);
        messages.send(player, "gunluk-odul.mesajlar.alindi", "miktar", plugin.economy().format(reward), "seri", streak);
        return true;
    }

    /** Giriste: bugun almadiysa hatirlat. */
    void remind(Player player) {
        if (!enabled || claimedToday(player.getUniqueId())) {
            return;
        }
        int current = nextStreak(player.getUniqueId(), today()) - 1;
        plugin.messages().send(player, "gunluk-odul.mesajlar.hatirlatma", "seri", current);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
