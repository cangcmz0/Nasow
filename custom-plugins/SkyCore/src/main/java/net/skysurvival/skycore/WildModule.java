package net.skysurvival.skycore;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * /vahsi: oyuncuyu dunyada rastgele, guvenli bir yere isinlar (spawn adasindaki portal da bunu kullanir).
 * Parcalar (chunk) arka planda yuklenir, sunucu donmaz. Su, lav, kaktus, toz kar gibi yerlere isinlamaz.
 */
final class WildModule implements Module, CommandExecutor, TabCompleter {
    private static final Set<Material> UNSAFE = Set.of(Material.WATER, Material.LAVA, Material.FIRE, Material.SOUL_FIRE,
            Material.CACTUS, Material.MAGMA_BLOCK, Material.POWDER_SNOW, Material.SWEET_BERRY_BUSH, Material.CAMPFIRE,
            Material.SOUL_CAMPFIRE, Material.POINTED_DRIPSTONE, Material.KELP, Material.SEAGRASS, Material.TALL_SEAGRASS,
            Material.BUBBLE_COLUMN, Material.COBWEB);

    private final SkyCore plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Set<UUID> searching = new HashSet<>();
    private boolean enabled;

    WildModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("vahsi-doga.aktif", true);
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Messages messages = plugin.messages();
        if (!(sender instanceof Player player)) {
            messages.send(sender, "genel-mesajlar.sadece-oyuncu");
            return true;
        }
        if (!enabled) {
            messages.send(player, "genel-mesajlar.yetki-yok");
            return true;
        }
        if (plugin.combat().isTagged(player)) {
            messages.send(player, "vahsi-doga.mesajlar.savasta");
            return true;
        }
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long until = cooldown.get(id);
        if (until != null && until > now && !player.hasPermission("skycore.vahsi.bekleme-yok")) {
            messages.send(player, "vahsi-doga.mesajlar.bekle", "kalan", (until - now + 999) / 1000);
            return true;
        }
        if (!searching.add(id)) {
            return true;
        }
        String worldName = plugin.getConfig().getString("vahsi-doga.dunya", "world");
        World world = plugin.getServer().getWorld(worldName);
        if (world == null) {
            searching.remove(id);
            messages.send(player, "spawn-adasi.mesajlar.dunya-yok", "dunya", worldName);
            return true;
        }
        messages.send(player, "vahsi-doga.mesajlar.araniyor");
        search(player, world, Math.max(1, plugin.getConfig().getInt("vahsi-doga.deneme", 15)));
        return true;
    }

    /** Rastgele bir nokta secip parcasini arka planda yukler; uygun degilse tekrar dener. */
    private void search(Player player, World world, int attemptsLeft) {
        if (!player.isOnline()) {
            searching.remove(player.getUniqueId());
            return;
        }
        if (attemptsLeft <= 0) {
            searching.remove(player.getUniqueId());
            plugin.messages().send(player, "vahsi-doga.mesajlar.bulunamadi");
            return;
        }
        int[] point = randomPoint();
        world.getChunkAtAsync(point[0] >> 4, point[1] >> 4).thenAccept(chunk -> {
            if (plugin.combat().isTagged(player)) {
                searching.remove(player.getUniqueId());
                plugin.messages().send(player, "vahsi-doga.mesajlar.savasta"); // ararken savasa girdi
                return;
            }
            Location target = safeSpot(world, point[0], point[1]);
            if (target == null) {
                search(player, world, attemptsLeft - 1);
                return;
            }
            player.teleportAsync(target, PlayerTeleportEvent.TeleportCause.COMMAND).thenAccept(success -> {
                searching.remove(player.getUniqueId());
                if (!success) {
                    return;
                }
                cooldown.put(player.getUniqueId(), System.currentTimeMillis()
                        + Math.max(0, plugin.getConfig().getInt("vahsi-doga.bekleme-saniye", 60)) * 1000L);
                Messages messages = plugin.messages();
                messages.send(player, "vahsi-doga.mesajlar.isinlandi", "x", target.getBlockX(), "y", target.getBlockY(), "z", target.getBlockZ());
                messages.title(player, "vahsi-doga.mesajlar.baslik", "vahsi-doga.mesajlar.alt-baslik");
                Messages.sound(player, "entity.enderman.teleport", 1.0f);
            });
        }).exceptionally(error -> {
            searching.remove(player.getUniqueId());
            plugin.getLogger().warning("/vahsi parca yuklenemedi: " + error);
            return null;
        });
    }

    /** Merkez etrafinda min-max mesafe arasinda (kare halka) rastgele x, z. */
    int[] randomPoint() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int cx = plugin.getConfig().getInt("vahsi-doga.merkez.x", 0);
        int cz = plugin.getConfig().getInt("vahsi-doga.merkez.z", 0);
        int min = Math.max(0, plugin.getConfig().getInt("vahsi-doga.min-mesafe", 500));
        int max = Math.max(min + 1, plugin.getConfig().getInt("vahsi-doga.max-mesafe", 5000));
        int dx;
        int dz;
        do {
            dx = random.nextInt(-max, max + 1);
            dz = random.nextInt(-max, max + 1);
        } while (Math.max(Math.abs(dx), Math.abs(dz)) < min);
        return new int[] {cx + dx, cz + dz};
    }

    /** Ayak basilacak blok guvenliyse ustundeki noktayi verir. */
    private Location safeSpot(World world, int x, int z) {
        Block ground = world.getHighestBlockAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        if (ground.getY() <= world.getMinHeight() || UNSAFE.contains(ground.getType()) || !ground.getType().isSolid()) {
            return null;
        }
        Block feet = ground.getRelative(0, 1, 0);
        Block head = ground.getRelative(0, 2, 0);
        if (feet.getType().isSolid() || head.getType().isSolid() || UNSAFE.contains(feet.getType())) {
            return null;
        }
        Location location = new Location(world, x + 0.5, ground.getY() + 1, z + 0.5,
                ThreadLocalRandom.current().nextFloat() * 360 - 180, 0);
        return world.getWorldBorder().isInside(location) ? location : null;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        searching.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }
}
