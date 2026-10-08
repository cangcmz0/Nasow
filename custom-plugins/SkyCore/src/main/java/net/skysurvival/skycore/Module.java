package net.skysurvival.skycore;

import org.bukkit.event.Listener;

/** Bir ozellik. Dinleyiciler bir kez kaydedilir; start/stop her /skycore reload'da yeniden calisir. */
interface Module extends Listener {
    /** Ayarlari okur ve gerekiyorsa zamanlayicilari baslatir. */
    void start();

    /** Zamanlayicilari durdurur. */
    void stop();
}
