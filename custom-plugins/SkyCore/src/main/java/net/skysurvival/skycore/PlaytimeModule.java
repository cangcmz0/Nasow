package net.skysurvival.skycore;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** AFK olmadan oynanan her X dakika icin para odulu. 5 dakikadan uzun kipirdamayan oyuncu sayilmaz. */
final class PlaytimeModule implements Module {
    private static final long AFK_MILLIS = 5 * 60 * 1000L;

    private final SkyCore plugin;
    private final Map<UUID, Location> lastLocation = new HashMap<>();
    private final Map<UUID, Long> lastActive = new HashMap<>();
    private BukkitTask task;

    PlaytimeModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        if (plugin.getConfig().getBoolean("aktiflik-odulu.aktif", true)) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L * 60, 20L * 60);
        }
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private static boolean moved(Location before, Location now) {
        if (before == null || before.getWorld() != now.getWorld()) {
            return true;
        }
        return before.distanceSquared(now) > 1.0
                || Math.abs(before.getYaw() - now.getYaw()) > 5
                || Math.abs(before.getPitch() - now.getPitch()) > 5;
    }

    /** Dakikada bir calisir. */
    void tick() {
        int target = Math.max(1, plugin.getConfig().getInt("aktiflik-odulu.dakika", 60));
        double reward = plugin.getConfig().getDouble("aktiflik-odulu.odul", 500);
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            UUID id = player.getUniqueId();
            if (!plugin.auth().isLoggedIn(player)) {
                continue;
            }
            Location location = player.getLocation();
            if (moved(lastLocation.get(id), location)) {
                lastActive.put(id, now);
            }
            lastLocation.put(id, location);
            if (now - lastActive.getOrDefault(id, now) > AFK_MILLIS) {
                continue; // AFK
            }
            int minutes = plugin.data().activeMinutes(id) + 1;
            if (minutes >= target && plugin.economy().deposit(player, reward)) {
                minutes = 0;
                plugin.messages().send(player, "aktiflik-odulu.mesajlar.verildi", "dakika", target,
                        "odul", plugin.economy().format(reward));
            }
            plugin.data().setActiveMinutes(id, minutes);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastLocation.remove(event.getPlayer().getUniqueId());
        lastActive.remove(event.getPlayer().getUniqueId());
    }
}
