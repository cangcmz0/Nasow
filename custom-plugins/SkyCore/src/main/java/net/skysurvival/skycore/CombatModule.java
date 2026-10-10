package net.skysurvival.skycore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.entity.AnimalTamer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

/** PvP'ye giren oyuncular belirli sure cikamaz ve isinlanamaz; savastayken cikan oyuncu olur. */
final class CombatModule implements Module {
    static final String BYPASS = "skycore.savas.bypass";

    private final SkyCore plugin;
    /** Oyuncu -> savas modunun bitecegi zaman (ms). */
    private final Map<UUID, Long> tagged = new HashMap<>();
    private final Set<UUID> kicked = new HashSet<>();
    /** Savastan kacip oldurulmekte olan oyuncular (olum olayi sirasinda hala savasta sayilir). */
    private final Set<UUID> punishing = new HashSet<>();
    private final Set<String> blockedCommands = new HashSet<>();
    private boolean enabled;
    private boolean punishLogout;
    private long durationMillis;
    private BukkitTask task;

    CombatModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("savas.aktif", true);
        punishLogout = plugin.getConfig().getBoolean("savas.kacana-ceza", true);
        durationMillis = Math.max(1, plugin.getConfig().getInt("savas.sure-saniye", 15)) * 1000L;
        blockedCommands.clear();
        for (String command : plugin.getConfig().getStringList("savas.yasakli-komutlar")) {
            blockedCommands.add(command.toLowerCase(Locale.ROOT));
        }
        if (enabled) {
            task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        }
    }

    @Override
    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        tagged.clear();
    }

    boolean isTagged(Player player) {
        Long until = tagged.get(player.getUniqueId());
        return until != null && until > System.currentTimeMillis();
    }

    /** Savasta mi ya da savastan kactigi icin olduruluyor mu (mezar ve takas icin). */
    boolean inCombat(Player player) {
        return isTagged(player) || punishing.contains(player.getUniqueId());
    }

    private long secondsLeft(Player player) {
        Long until = tagged.get(player.getUniqueId());
        return until == null ? 0 : Math.max(1, (until - System.currentTimeMillis() + 999) / 1000);
    }

    private void tag(Player player) {
        if (player.hasPermission(BYPASS) || player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) {
            return;
        }
        boolean wasTagged = isTagged(player);
        tagged.put(player.getUniqueId(), System.currentTimeMillis() + durationMillis);
        if (!wasTagged) {
            plugin.messages().send(player, "savas.mesajlar.basladi", "sure", durationMillis / 1000);
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> it = tagged.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Long> entry = it.next();
            Player player = plugin.getServer().getPlayer(entry.getKey());
            if (player == null) {
                it.remove();
            } else if (entry.getValue() <= now) {
                it.remove();
                plugin.messages().send(player, "savas.mesajlar.bitti");
            } else {
                plugin.messages().actionBar(player, "savas.mesajlar.actionbar", "kalan", secondsLeft(player));
            }
        }
    }

    /** Hasari veren oyuncuyu bulur: dogrudan vurus, ok/trident, TNT veya evcil hayvan. */
    static Player attacker(Entity damager) {
        if (damager instanceof Player player) {
            return player;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (damager instanceof TNTPrimed tnt && tnt.getSource() instanceof Player player) {
            return player;
        }
        if (damager instanceof Tameable pet) {
            AnimalTamer owner = pet.getOwner();
            if (owner instanceof Player player) {
                return player;
            }
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        tag(victim);
        tag(attacker);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!enabled || !isTagged(player)) {
            return;
        }
        String typed = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (isBlocked(typed)) {
            event.setCancelled(true);
            plugin.messages().send(player, "savas.mesajlar.komut-yasak", "kalan", secondsLeft(player));
        }
    }

    /** Yazilan komut, takma adi (ehome -> home) ya da eklenti onekiyle (essentials:spawn) yasakli mi. */
    boolean isBlocked(String typed) {
        Set<String> names = new HashSet<>();
        names.add(typed);
        int namespace = typed.indexOf(':');
        if (namespace >= 0) {
            names.add(typed.substring(namespace + 1)); // essentials:spawn -> spawn
        }
        Command command = plugin.getServer().getCommandMap().getCommand(typed);
        if (command != null) {
            names.add(command.getName().toLowerCase(Locale.ROOT));
            command.getAliases().forEach(alias -> names.add(alias.toLowerCase(Locale.ROOT)));
        }
        for (String name : names) {
            if (blockedCommands.contains(name)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKick(PlayerKickEvent event) {
        // Yetkili tarafindan atilan oyuncu cezalandirilmaz.
        kicked.add(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        boolean wasKicked = kicked.remove(player.getUniqueId());
        boolean inCombat = isTagged(player);
        tagged.remove(player.getUniqueId());
        if (!enabled || !punishLogout || !inCombat || wasKicked || player.isDead()) {
            return;
        }
        punishing.add(player.getUniqueId());
        try {
            player.setHealth(0.0); // esyalar yere duser, son vuran "oldurmus" sayilir
        } finally {
            punishing.remove(player.getUniqueId());
        }
        plugin.messages().broadcast("savas.mesajlar.kacti", "oyuncu", player.getName());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        tagged.remove(event.getEntity().getUniqueId());
    }
}
