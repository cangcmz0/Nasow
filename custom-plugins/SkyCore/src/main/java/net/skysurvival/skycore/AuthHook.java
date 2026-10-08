package net.skysurvival.skycore;

import org.bukkit.entity.Player;

/** AuthMe kuruluysa oyuncunun sifreyle giris yapip yapmadigini soyler; yoksa herkes giris yapmis sayilir. */
interface AuthHook {
    boolean isLoggedIn(Player player);

    boolean isAuthMe();

    static AuthHook create(SkyCore plugin) {
        if (plugin.getServer().getPluginManager().isPluginEnabled("AuthMe")) {
            try {
                return new AuthMeHook();
            } catch (Throwable error) {
                plugin.getLogger().warning("AuthMe'ye baglanilamadi, giris kontrolu kapali: " + error);
            }
        }
        return new AuthHook() {
            @Override
            public boolean isLoggedIn(Player player) {
                return true;
            }

            @Override
            public boolean isAuthMe() {
                return false;
            }
        };
    }
}
