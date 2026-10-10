package net.skysurvival.skycore;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.bossbar.BossBar;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.scheduler.BukkitTask;

/**
 * Tepenin Krali: arenadaki altin platformu belirlenen sure tek basina (ya da klaniyla) tutan kazanir.
 * Ayarlanan saatlerde kendiliginden baslar; /skycore koth baslat|bitir ile elle de yonetilir.
 */
final class KothModule implements Module, CommandExecutor, TabCompleter {
    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");

    private final SkyCore plugin;
    private boolean enabled;
    private BukkitTask clockTask;
    private BukkitTask gameTask;
    private BossBar bar;
    private String lastAutoStart = "";

    // Suren etkinlik
    private UUID king;
    private int held;
    private int elapsed;
    private int holdSeconds;
    private int maxSeconds;

    KothModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("koth.aktif", true);
        if (enabled) {
            clockTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::checkClock, 20L * 15, 20L * 15);
        }
    }

    @Override
    public void stop() {
        if (clockTask != null) {
            clockTask.cancel();
            clockTask = null;
        }
        end(null, false);
    }

    boolean running() {
        return gameTask != null;
    }

    private ZoneId zone() {
        try {
            return ZoneId.of(plugin.getConfig().getString("gunluk-odul.saat-dilimi", "Europe/Istanbul"));
        } catch (RuntimeException e) {
            return ZoneId.of("Europe/Istanbul");
        }
    }

    private void checkClock() {
        if (running()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(zone());
        String minute = now.format(MINUTE);
        if (minute.equals(lastAutoStart) || !plugin.getConfig().getStringList("koth.saatler").contains(now.format(CLOCK))) {
            return;
        }
        lastAutoStart = minute;
        int min = plugin.getConfig().getInt("koth.min-oyuncu", 2);
        if (plugin.getServer().getOnlinePlayers().size() < min) {
            plugin.messages().broadcast("koth.mesajlar.az-oyuncu", "min", min);
            return;
        }
        begin(plugin.getServer().getConsoleSender());
    }

    /** Etkinligi baslatir; baslamazsa nedenini gonderene yazar. */
    boolean begin(CommandSender sender) {
        Messages messages = plugin.messages();
        if (running()) {
            messages.send(sender, "koth.mesajlar.zaten");
            return false;
        }
        if (plugin.spawn().kothArea() == null) {
            messages.send(sender, "koth.mesajlar.ada-yok");
            return false;
        }
        holdSeconds = Math.max(10, plugin.getConfig().getInt("koth.tutma-saniye", 120));
        maxSeconds = Math.max(holdSeconds, plugin.getConfig().getInt("koth.max-dakika", 15) * 60);
        king = null;
        held = 0;
        elapsed = 0;
        bar = BossBar.bossBar(Messages.color(messages.raw("koth.mesajlar.bossbar-bos")), 0f, BossBar.Color.YELLOW, BossBar.Overlay.NOTCHED_10);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.showBossBar(bar);
            Messages.sound(player, "event.raid.horn", 1.0f);
        }
        for (String line : messages.rawList("koth.mesajlar.basladi", "sure", holdSeconds)) {
            messages.broadcastText(line);
        }
        plugin.announceToDiscord("koth-basladi", "sure", holdSeconds);
        gameTask = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        return true;
    }

    /** Etkinligi bitirir; winner null ise kazanan yok. */
    void end(Player winner, boolean announce) {
        if (gameTask != null) {
            gameTask.cancel();
            gameTask = null;
        }
        if (bar != null) {
            for (Player player : plugin.getServer().getOnlinePlayers()) {
                player.hideBossBar(bar);
            }
            bar = null;
        }
        if (!announce) {
            return;
        }
        Messages messages = plugin.messages();
        if (winner == null) {
            messages.broadcast("koth.mesajlar.kazanan-yok");
            return;
        }
        double money = plugin.getConfig().getDouble("koth.odul-para", 5000);
        String keyType = plugin.getConfig().getString("koth.odul-anahtar", "efsane");
        int keys = plugin.getConfig().getInt("koth.odul-anahtar-adet", 1);
        plugin.economy().deposit(winner, money);
        plugin.crates().giveKey(winner, keyType, keys);
        plugin.data().addKothWin(winner.getUniqueId(), winner.getName());
        plugin.data().setLastKothWinner(winner.getName());
        messages.broadcast("koth.mesajlar.kazandi", "oyuncu", winner.getName(), "sure", holdSeconds,
                "para", plugin.economy().format(money), "anahtar", keys + "x " + plugin.crates().typeName(keyType));
        plugin.announceToDiscord("koth-kazandi", "oyuncu", winner.getName(), "para", plugin.economy().format(money),
                "anahtar", keys + "x " + plugin.crates().typeName(keyType));
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            Messages.sound(player, "ui.toast.challenge_complete", 1.0f);
        }
        launchFirework(winner.getLocation());
    }

    private void launchFirework(Location location) {
        World world = location.getWorld();
        if (world == null) {
            return;
        }
        Firework firework = world.spawn(location.clone().add(0, 1, 0), Firework.class);
        FireworkMeta meta = firework.getFireworkMeta();
        meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.STAR).withColor(Color.YELLOW, Color.ORANGE)
                .withFade(Color.WHITE).trail(true).flicker(true).build());
        meta.setPower(1);
        firework.setFireworkMeta(meta);
    }

    /** Alandaki canli, hayatta kalma/macera modundaki oyuncular. */
    private List<Player> playersOnHill(Box hill) {
        List<Player> result = new ArrayList<>();
        World world = plugin.spawn().kothWorld();
        if (world == null) {
            return result;
        }
        for (Player player : world.getPlayers()) {
            GameMode mode = player.getGameMode();
            if (!player.isDead() && (mode == GameMode.SURVIVAL || mode == GameMode.ADVENTURE) && hill.contains(player.getLocation())) {
                result.add(player);
            }
        }
        return result;
    }

    private void tick() {
        Box hill = plugin.spawn().kothArea();
        if (hill == null) {
            end(null, false);
            return;
        }
        elapsed++;
        List<Player> onHill = playersOnHill(hill);
        Messages messages = plugin.messages();
        Set<String> sides = new HashSet<>();
        for (Player player : onHill) {
            String clan = plugin.clans().clanName(player.getUniqueId());
            sides.add(clan != null ? "klan:" + clan.toLowerCase(java.util.Locale.ROOT) : "oyuncu:" + player.getUniqueId());
        }
        Player current = king == null ? null : plugin.getServer().getPlayer(king);
        if (onHill.isEmpty()) {
            king = null;
            held = 0;
            bar.name(Messages.color(messages.raw("koth.mesajlar.bossbar-bos")));
            bar.color(BossBar.Color.YELLOW);
        } else if (sides.size() > 1) {
            bar.name(Messages.color(messages.raw("koth.mesajlar.bossbar-cekisme", "oyuncular", onHill.size())));
            bar.color(BossBar.Color.RED);
        } else {
            if (current == null || !onHill.contains(current)) {
                Player newKing = onHill.get(0);
                String oldClan = king == null ? null : plugin.clans().clanName(king);
                boolean sameClan = oldClan != null && Objects.equals(oldClan, plugin.clans().clanName(newKing.getUniqueId()));
                if (!sameClan) {
                    held = 0;
                    messages.broadcast("koth.mesajlar.kral-degisti", "oyuncu", newKing.getName());
                }
                king = newKing.getUniqueId();
                current = newKing;
            }
            held++;
            bar.name(Messages.color(messages.raw("koth.mesajlar.bossbar-kral", "oyuncu", current.getName(),
                    "kalan", Math.max(0, holdSeconds - held))));
            bar.color(BossBar.Color.GREEN);
            if (held >= holdSeconds) {
                bar.progress(1f);
                end(current, true);
                return;
            }
        }
        bar.progress(Math.min(1f, (float) held / holdSeconds));
        outline(hill);
        if (elapsed >= maxSeconds) {
            end(null, true);
        }
    }

    private void outline(Box hill) {
        World world = plugin.spawn().kothWorld();
        double y = hill.minY() + 0.1;
        for (int x = hill.minX(); x <= hill.maxX() + 1; x++) {
            world.spawnParticle(Particle.FLAME, x, y, hill.minZ(), 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, x, y, hill.maxZ() + 1, 1, 0, 0, 0, 0);
        }
        for (int z = hill.minZ(); z <= hill.maxZ() + 1; z++) {
            world.spawnParticle(Particle.FLAME, hill.minX(), y, z, 1, 0, 0, 0, 0);
            world.spawnParticle(Particle.FLAME, hill.maxX() + 1, y, z, 1, 0, 0, 0, 0);
        }
    }

    /** Yeni giren oyuncu suren etkinligin cubugunu gorsun. */
    void showTo(Player player) {
        if (bar != null) {
            player.showBossBar(bar);
        }
    }

    // ---- /koth ----

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (args.length > 0 && (args[0].equalsIgnoreCase("katil") || args[0].equalsIgnoreCase("git"))) {
            join(sender);
            return true;
        }
        if (running()) {
            Player current = king == null ? null : plugin.getServer().getPlayer(king);
            messages.sendLines(sender, "koth.mesajlar.durum-suruyor", "oyuncu", current == null ? "-" : current.getName(),
                    "kalan", Math.max(0, holdSeconds - held), "bitis", Math.max(0, (maxSeconds - elapsed) / 60));
        } else {
            String last = plugin.data().lastKothWinner();
            messages.sendLines(sender, "koth.mesajlar.durum-yok",
                    "saatler", String.join(", ", plugin.getConfig().getStringList("koth.saatler")),
                    "son", last.isEmpty() ? "-" : last);
        }
        if (plugin.spawn().arenaBuilt()) {
            messages.sendLines(sender, "koth.mesajlar.harita");
        }
        return true;
    }

    /** /koth katil: arenaya (rastgele bir usse) isinlar. */
    private void join(CommandSender sender) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return;
        }
        if (plugin.combat().isTagged(player)) {
            messages.send(player, "vahsi-doga.mesajlar.savasta");
            return;
        }
        Location target = plugin.spawn().kothJoinLocation();
        if (target == null) {
            messages.send(player, "koth.mesajlar.ada-yok");
            return;
        }
        if (player.teleport(target)) {
            messages.send(player, running() ? "koth.mesajlar.katildin-etkinlik" : "koth.mesajlar.katildin");
            Messages.sound(player, "entity.enderman.teleport", 1.0f);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && "katil".startsWith(args[0].toLowerCase(java.util.Locale.ROOT)) ? List.of("katil") : List.of();
    }
}
