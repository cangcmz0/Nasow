package net.skysurvival.skycore;

import java.util.Collection;
import java.util.List;
import org.bukkit.Statistic;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectTypeCategory;

/**
 * Yeni oyuncu korumasi: ilk X dakika (toplam oynama suresi) oyuncular PvP'de vurulamaz ve vuramaz.
 * Arenalarda gecerli degildir. /koruma ile kalan sure, /koruma kapat ile erken kapatma.
 */
final class NewbieModule implements Module, CommandExecutor, TabCompleter {
    private final SkyCore plugin;
    private boolean enabled;
    private int minutes;

    NewbieModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("yeni-oyuncu-korumasi.aktif", true);
        minutes = Math.max(0, plugin.getConfig().getInt("yeni-oyuncu-korumasi.dakika", 60));
    }

    @Override
    public void stop() {
    }

    static int playedMinutes(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) / 1200; // istatistik tick cinsinden
    }

    boolean isProtected(Player player) {
        return enabled && !plugin.data().protectionOff(player.getUniqueId()) && playedMinutes(player) < minutes;
    }

    int minutesLeft(Player player) {
        return Math.max(0, minutes - playedMinutes(player));
    }

    private boolean inArena(Player player) {
        SpawnModule spawn = plugin.spawn();
        return spawn.inArena(player.getLocation()) || spawn.inKothArena(player.getLocation());
    }

    /** Yeni oyuncu korumasi bu iki oyuncu arasinda PvP'ye izin vermiyor mu. */
    boolean pvpBlocked(Player attacker, Player victim) {
        if (!enabled || attacker.equals(victim) || (inArena(victim) && inArena(attacker))) {
            return false;
        }
        return isProtected(victim) || isProtected(attacker);
    }

    /** Ada, yeni oyuncu ve klan kurallarinin hepsi: bu oyuncu ona zarar verebilir mi. */
    private boolean anyPvpRuleBlocks(Player attacker, Player victim) {
        return pvpBlocked(attacker, victim) || plugin.spawn().islandPvpBlocked(attacker, victim)
                || plugin.clans().friendlyFireBlocked(attacker, victim);
    }

    private static boolean harmful(Collection<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            if (effect.getType().getCategory() == PotionEffectTypeCategory.HARMFUL) {
                return true;
            }
        }
        return false;
    }

    /** Zehir, yavaslik vb. firlatilan iksirler korunan oyunculari etkilemez (vurus yasagini iksirle asma). */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower) || !harmful(event.getPotion().getEffects())) {
            return;
        }
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && !victim.equals(thrower) && anyPvpRuleBlocks(thrower, victim)) {
                event.setIntensity(victim, 0);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onLingering(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        if (!(cloud.getSource() instanceof Player thrower)) {
            return;
        }
        List<PotionEffect> effects = new java.util.ArrayList<>(cloud.getCustomEffects());
        if (cloud.getBasePotionType() != null) {
            effects.addAll(cloud.getBasePotionType().getPotionEffects());
        }
        if (harmful(effects)) {
            event.getAffectedEntities().removeIf(entity -> entity instanceof Player victim && !victim.equals(thrower)
                    && anyPvpRuleBlocks(thrower, victim));
        }
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!enabled || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = CombatModule.attacker(event.getDamager());
        if (attacker == null || !pvpBlocked(attacker, victim)) {
            return;
        }
        if (isProtected(victim)) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "yeni-oyuncu-korumasi.mesajlar.hedef-korumali", "oyuncu", victim.getName());
        } else if (isProtected(attacker)) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "yeni-oyuncu-korumasi.mesajlar.sen-korumalisin");
        }
    }

    /** Giriste korumali oyuncuya hatirlatma. */
    void remind(Player player) {
        if (isProtected(player)) {
            plugin.messages().send(player, "yeni-oyuncu-korumasi.mesajlar.giris", "dakika", minutesLeft(player));
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (!isProtected(player)) {
            messages.send(player, "yeni-oyuncu-korumasi.mesajlar.yok");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("kapat")) {
            if (args.length > 1 && args[1].equalsIgnoreCase("onayla")) {
                plugin.data().setProtectionOff(player.getUniqueId());
                messages.send(player, "yeni-oyuncu-korumasi.mesajlar.kapatildi");
            } else {
                messages.send(player, "yeni-oyuncu-korumasi.mesajlar.kapat-onay");
            }
            return true;
        }
        messages.send(player, "yeni-oyuncu-korumasi.mesajlar.durum", "dakika", minutesLeft(player));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && "kapat".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) {
            return List.of("kapat");
        }
        return List.of();
    }
}
