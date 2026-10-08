package net.skysurvival.skycore;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

/** Giris yapan oyuncuya buyuk baslik ve ses. */
final class WelcomeModule implements Module {
    private final SkyCore plugin;
    private final Set<UUID> firstJoin = new HashSet<>();

    WelcomeModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    /** PlayerJoinEvent sirasinda cagrilir; oyuncunun ilk girisi olup olmadigini aklinda tutar. */
    void rememberFirstJoin(Player player) {
        if (!player.hasPlayedBefore()) {
            firstJoin.add(player.getUniqueId());
        }
    }

    void greet(Player player) {
        boolean first = firstJoin.remove(player.getUniqueId());
        if (!plugin.getConfig().getBoolean("hosgeldin.aktif", true)) {
            return;
        }
        Messages messages = plugin.messages();
        String subtitlePath = first ? "hosgeldin.ilk-giris-alt-baslik" : "hosgeldin.alt-baslik";
        Title title = Title.title(
                Messages.color(messages.raw("hosgeldin.baslik", "oyuncu", player.getName())),
                Messages.color(messages.raw(subtitlePath, "oyuncu", player.getName())),
                Title.Times.times(Duration.ofMillis(500), Duration.ofMillis(3000), Duration.ofMillis(1000)));
        player.showTitle(title);
        if (plugin.getConfig().getBoolean("hosgeldin.ses", true)) {
            String sound = first ? "ui.toast.challenge_complete" : "entity.player.levelup";
            player.playSound(Sound.sound(Key.key(sound), Sound.Source.MASTER, 0.8f, 1.0f));
        }
    }
}
