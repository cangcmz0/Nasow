package net.skysurvival.skycore;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;

/**
 * Toplama kolayliklari: egilerek baltayla agac devirme, egilerek kazmayla bitisik madenleri kazma ve olgun ekine
 * sag tiklayarak hasat edip yeniden ekme. Her blok icin gercek bir BlockBreakEvent atilir; boylece arazi korumasi
 * (HuskClaims), kayit (CoreProtect), meslek parasi (Jobs) ve yetenek XP'si (AuraSkills) normal kazmadaki gibi calisir.
 */
final class GatheringModule implements Module {
    private static final Map<Material, Material> SEEDS = Map.of(
            Material.WHEAT, Material.WHEAT_SEEDS, Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART,
            Material.COCOA, Material.COCOA_BEANS);

    private final SkyCore plugin;
    /** Kendi attigimiz kirma olaylarinda tekrar devreye girmemek icin. */
    private final Set<UUID> working = new HashSet<>();
    private boolean timber;
    private boolean timberSneak;
    private int timberMax;
    private boolean vein;
    private boolean veinSneak;
    private int veinMax;
    private boolean harvest;

    GatheringModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
        timber = plugin.getConfig().getBoolean("toplama.agac-kesme.aktif", true);
        timberSneak = plugin.getConfig().getBoolean("toplama.agac-kesme.egilerek", true);
        timberMax = Math.max(1, plugin.getConfig().getInt("toplama.agac-kesme.max-blok", 96));
        vein = plugin.getConfig().getBoolean("toplama.damar-kazma.aktif", true);
        veinSneak = plugin.getConfig().getBoolean("toplama.damar-kazma.egilerek", true);
        veinMax = Math.max(1, plugin.getConfig().getInt("toplama.damar-kazma.max-blok", 24));
        harvest = plugin.getConfig().getBoolean("toplama.sag-tik-hasat.aktif", true);
    }

    @Override
    public void stop() {
    }

    static boolean isAxe(Material type) {
        return type.name().endsWith("_AXE") && !type.name().endsWith("PICKAXE");
    }

    static boolean isPickaxe(Material type) {
        return type.name().endsWith("_PICKAXE");
    }

    /** Ayni damar sayilan madenler (derin kayrak surumu dahil). */
    static String oreGroup(Material type) {
        String name = type.name();
        if (type == Material.ANCIENT_DEBRIS) {
            return name;
        }
        if (!name.endsWith("_ORE")) {
            return null;
        }
        return name.replace("DEEPSLATE_", "");
    }

    // ---- Agac devirme ve damar kazma ----

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (working.contains(player.getUniqueId()) || player.getGameMode() == GameMode.CREATIVE) {
            return;
        }
        Block block = event.getBlock();
        ItemStack tool = player.getInventory().getItemInMainHand();
        Material type = block.getType();
        if (timber && Tag.LOGS.isTagged(type) && isAxe(tool.getType()) && (!timberSneak || player.isSneaking())) {
            List<Block> logs = connected(block, b -> Tag.LOGS.isTagged(b.getType()), timberMax, true);
            if (naturalTree(logs)) {
                logs.sort(Comparator.comparingInt(Block::getY));
                breakAll(player, logs);
            }
        } else if (vein && oreGroup(type) != null && isPickaxe(tool.getType()) && (!veinSneak || player.isSneaking())
                && !block.getDrops(tool).isEmpty()) {
            String group = oreGroup(type);
            breakAll(player, connected(block, b -> group.equals(oreGroup(b.getType())), veinMax, true));
        }
    }

    /** Baslangic blogundan (dahil degil) kose komsulari dahil bagli bloklar. */
    static List<Block> connected(Block start, Predicate<Block> filter, int max, boolean diagonal) {
        Set<Block> seen = new LinkedHashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        seen.add(start);
        queue.add(start);
        List<Block> result = new ArrayList<>();
        while (!queue.isEmpty() && result.size() < max) {
            Block current = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if ((dx == 0 && dy == 0 && dz == 0) || (!diagonal && Math.abs(dx) + Math.abs(dy) + Math.abs(dz) != 1)) {
                            continue;
                        }
                        Block next = current.getRelative(dx, dy, dz);
                        if (seen.add(next) && filter.test(next)) {
                            queue.add(next);
                            result.add(next);
                            if (result.size() >= max) {
                                return result;
                            }
                        }
                    }
                }
            }
        }
        return result;
    }

    /** Oyuncunun yaptigi kutuk duvarlari devrilmesin: dogal (kendiliginden cikan) yapraklara degmeli. */
    static boolean naturalTree(Collection<Block> logs) {
        int leaves = 0;
        for (Block log : logs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = 0; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        Block near = log.getRelative(dx, dy, dz);
                        if (near.getBlockData() instanceof Leaves leaf && !leaf.isPersistent() && ++leaves >= 4) {
                            return true;
                        }
                    }
                }
            }
        }
        return false;
    }

    /** Her blok icin gercek kirma olayi; korunan bloklar atlanir, alet aşinir, alet kirilinca durur. */
    private void breakAll(Player player, List<Block> blocks) {
        UUID id = player.getUniqueId();
        working.add(id);
        try {
            for (Block block : blocks) {
                ItemStack tool = player.getInventory().getItemInMainHand();
                if (tool.getType().isAir()) {
                    break; // alet kirildi
                }
                BlockBreakEvent event = new BlockBreakEvent(block, player);
                plugin.getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    continue;
                }
                if (event.isDropItems()) {
                    block.breakNaturally(tool, true, true);
                } else {
                    block.setType(Material.AIR);
                }
                wear(player);
                player.setExhaustion(player.getExhaustion() + 0.005f);
            }
        } finally {
            working.remove(id);
        }
    }

    /** Eldeki alete 1 hasar (Kirilmazlik buyusu ve PlayerItemDamageEvent dahil); kirilinca elden duser. */
    private void wear(Player player) {
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (!(tool.getItemMeta() instanceof Damageable meta) || meta.isUnbreakable()) {
            return;
        }
        int max = meta.hasMaxDamage() ? meta.getMaxDamage() : tool.getType().getMaxDurability();
        if (max <= 0) {
            return;
        }
        int unbreaking = tool.getEnchantmentLevel(Enchantment.UNBREAKING);
        if (unbreaking > 0 && ThreadLocalRandom.current().nextInt(unbreaking + 1) != 0) {
            return;
        }
        PlayerItemDamageEvent event = new PlayerItemDamageEvent(player, tool, 1, 1);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || event.getDamage() <= 0) {
            return;
        }
        meta.setDamage(meta.getDamage() + event.getDamage());
        if (meta.getDamage() >= max) {
            player.getInventory().setItemInMainHand(null);
            Messages.sound(player, "entity.item.break", 1.0f);
            return;
        }
        tool.setItemMeta(meta);
        player.getInventory().setItemInMainHand(tool);
    }

    // ---- Sag tikla hasat ----

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHarvest(PlayerInteractEvent event) {
        Block block = event.getClickedBlock();
        if (!harvest || block == null || event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Material seed = SEEDS.get(block.getType());
        if (seed == null || !(block.getBlockData() instanceof Ageable crop) || crop.getAge() < crop.getMaximumAge()) {
            return;
        }
        Player player = event.getPlayer();
        BlockBreakEvent check = new BlockBreakEvent(block, player);
        working.add(player.getUniqueId());
        try {
            plugin.getServer().getPluginManager().callEvent(check);
        } finally {
            working.remove(player.getUniqueId());
        }
        if (check.isCancelled()) {
            return; // baskasinin arazisi vb.
        }
        event.setCancelled(true);
        Collection<ItemStack> drops = block.getDrops(player.getInventory().getItemInMainHand(), player);
        boolean replanted = false;
        Location center = new Location(block.getWorld(), block.getX() + 0.5, block.getY() + 0.5, block.getZ() + 0.5);
        for (ItemStack drop : drops) {
            if (!replanted && drop.getType() == seed) {
                drop.setAmount(drop.getAmount() - 1); // tohum yeniden ekildi
                replanted = true;
            }
            if (drop.getAmount() > 0) {
                block.getWorld().dropItemNaturally(center, drop);
            }
        }
        crop.setAge(0);
        block.setBlockData(crop);
        player.swingMainHand();
        Messages.sound(player, "item.crop.plant", 1.0f);
    }
}
