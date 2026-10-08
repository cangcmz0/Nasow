package net.skysurvival.skycore;

import fr.xephi.authme.api.v3.AuthMeApi;
import org.bukkit.entity.Player;

/** Sadece AuthMe kuruluyken yuklenir (AuthMe siniflarina burada dokunulur). */
final class AuthMeHook implements AuthHook {
    private final AuthMeApi api = AuthMeApi.getInstance();

    @Override
    public boolean isLoggedIn(Player player) {
        return api.isAuthenticated(player);
    }

    @Override
    public boolean isAuthMe() {
        return true;
    }
}
