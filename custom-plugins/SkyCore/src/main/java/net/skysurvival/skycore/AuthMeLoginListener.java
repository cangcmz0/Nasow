package net.skysurvival.skycore;

import fr.xephi.authme.events.LoginEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/** Sadece AuthMe kuruluyken kaydedilir: sifre girilince karsilama ve gunluk odul hatirlatmasi yapilir. */
final class AuthMeLoginListener implements Listener {
    private final SkyCore plugin;

    AuthMeLoginListener(SkyCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onLogin(LoginEvent event) {
        if (plugin.getServer().isPrimaryThread()) {
            plugin.playerReady(event.getPlayer());
        } else {
            plugin.getServer().getScheduler().runTask(plugin, () -> plugin.playerReady(event.getPlayer()));
        }
    }
}
