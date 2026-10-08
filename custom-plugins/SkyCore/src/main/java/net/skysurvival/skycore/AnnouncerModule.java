package net.skysurvival.skycore;

import java.util.List;
import org.bukkit.scheduler.BukkitTask;

/** Ayarlardaki duyurulari belirli araliklarla sirayla gonderir. */
final class AnnouncerModule implements Module {
    private final SkyCore plugin;
    private int index;
    private BukkitTask task;

    AnnouncerModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        if (plugin.getConfig().getBoolean("duyurular.aktif", true)) {
            long interval = Math.max(1, plugin.getConfig().getInt("duyurular.aralik-dakika", 5)) * 60L * 20L;
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::announceNext, interval, interval);
        }
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    void announceNext() {
        List<String> messages = plugin.getConfig().getStringList("duyurular.mesajlar");
        if (messages.isEmpty() || plugin.getServer().getOnlinePlayers().isEmpty()) {
            return;
        }
        index = index % messages.size();
        plugin.messages().broadcastText(messages.get(index++));
    }
}
