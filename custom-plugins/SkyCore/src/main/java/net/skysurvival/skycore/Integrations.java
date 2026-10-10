package net.skysurvival.skycore;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.plugin.Plugin;

/**
 * EssentialsX (warp, spawn), DecentHolograms ve DiscordSRV'ye yansima (reflection) ile baglanir; boylece bu eklentiler
 * olmadan da SkyCore derlenir ve calisir. Basarisiz islemler false dondurur ve konsola yazilir.
 */
final class Integrations {
    private final SkyCore plugin;

    Integrations(SkyCore plugin) {
        this.plugin = plugin;
    }

    private Plugin enabled(String name) {
        Plugin other = plugin.getServer().getPluginManager().getPlugin(name);
        return other != null && other.isEnabled() ? other : null;
    }

    private void warn(String what, Throwable error) {
        Throwable cause = error instanceof InvocationTargetException ite && ite.getCause() != null ? ite.getCause() : error;
        plugin.getLogger().warning(what + ": " + cause);
    }

    /** /setwarp ile ayni: Essentials warp'i olusturur ya da tasir. */
    boolean setWarp(String name, Location location) {
        Plugin essentials = enabled("Essentials");
        if (essentials == null) {
            return false;
        }
        try {
            Object warps = essentials.getClass().getMethod("getWarps").invoke(essentials);
            Class<?> type = Class.forName("com.earth2me.essentials.api.IWarps", true, essentials.getClass().getClassLoader());
            type.getMethod("setWarp", String.class, Location.class).invoke(warps, name, location);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("Essentials warp'i ayarlanamadi (" + name + ")", e);
            return false;
        }
    }

    /** /setspawn <grup> ile ayni (EssentialsSpawn). */
    boolean setSpawn(String group, Location location) {
        Plugin spawn = enabled("EssentialsSpawn");
        if (spawn == null) {
            return false;
        }
        try {
            Method method = spawn.getClass().getMethod("setSpawn", Location.class, String.class);
            method.invoke(spawn, location, group);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("Essentials spawn'i ayarlanamadi (" + group + ")", e);
            return false;
        }
    }

    /**
     * DiscordSRV acik ve bot bagliysa ana sohbet kanalina mesaj yollar (Discord bicimi: **kalin**, :emoji:).
     * &-renk kodlari silinir. Gonderildiyse true.
     */
    boolean sendToDiscord(String text) {
        Plugin discord = enabled("DiscordSRV");
        if (discord == null || text == null || text.isBlank()) {
            return false;
        }
        try {
            Object channel = discord.getClass().getMethod("getMainTextChannel").invoke(discord);
            if (channel == null) {
                return false;
            }
            Class<?> util = Class.forName("github.scarsz.discordsrv.util.DiscordUtil", true, discord.getClass().getClassLoader());
            for (Method method : util.getMethods()) {
                Class<?>[] types = method.getParameterTypes();
                if (method.getName().equals("sendMessage") && types.length == 2 && types[1] == String.class
                        && types[0].isInstance(channel)) {
                    method.invoke(null, channel, text.replaceAll("&[0-9a-fk-orA-FK-OR]", ""));
                    return true;
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("Discord'a mesaj gonderilemedi", e);
        }
        return false;
    }

    boolean hologramsAvailable() {
        return enabled("DecentHolograms") != null;
    }

    /** Ayni isimde hologram varsa silip yeniden olusturur (dosyaya kaydedilir, /dh ile duzenlenebilir). */
    boolean createHologram(String name, Location location, List<String> lines) {
        Plugin holograms = enabled("DecentHolograms");
        if (holograms == null) {
            return false;
        }
        try {
            Class<?> api = Class.forName("eu.decentsoftware.holograms.api.DHAPI", true, holograms.getClass().getClassLoader());
            if (api.getMethod("getHologram", String.class).invoke(null, name) != null) {
                api.getMethod("removeHologram", String.class).invoke(null, name);
            }
            api.getMethod("createHologram", String.class, Location.class, boolean.class, List.class)
                    .invoke(null, name, location, true, lines);
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            warn("Hologram olusturulamadi (" + name + ")", e);
            return false;
        }
    }
}
