package net.skysurvival.skycore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * /banknot <miktar>: parayi bir kagida cevirir; sag tiklayinca hesaba geri yatar.
 * Banknotun degeri esyanin gizli verisinde (PersistentDataContainer) tutulur; oyuncular
 * survival'da bu veriyi taklit edemez.
 */
final class BanknoteModule implements Module, CommandExecutor, TabCompleter {
    private final SkyCore plugin;
    private final NamespacedKey valueKey;
    private boolean enabled;

    BanknoteModule(SkyCore plugin) {
        this.plugin = plugin;
        this.valueKey = new NamespacedKey(plugin, "banknot-degeri");
    }

    @Override
    public void start() {
        enabled = plugin.getConfig().getBoolean("banknot.aktif", true);
    }

    @Override
    public void stop() {
    }

    /** "1000", "1.000" (binlik ayrac), "2,5" (ondalik), "5k", "1m" gibi yazimlari kabul eder. Gecersizse -1. */
    static double parseAmount(String text) {
        String cleaned = text.trim().toLowerCase(Locale.ROOT).replace("₺", "").replace("tl", "");
        if (cleaned.matches("\\d{1,3}(\\.\\d{3})+")) {
            cleaned = cleaned.replace(".", "");
        }
        cleaned = cleaned.replace(",", ".");
        double multiplier = 1;
        if (cleaned.endsWith("k")) {
            multiplier = 1_000;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        } else if (cleaned.endsWith("m")) {
            multiplier = 1_000_000;
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        try {
            double value = Double.parseDouble(cleaned) * multiplier;
            return Double.isFinite(value) ? Math.floor(value * 100) / 100 : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
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
        if (!plugin.economy().available()) {
            messages.send(player, "genel-mesajlar.ekonomi-yok");
            return true;
        }
        if (args.length != 1) {
            messages.send(player, "banknot.mesajlar.kullanim");
            return true;
        }
        double min = plugin.getConfig().getDouble("banknot.min-miktar", 10);
        double max = plugin.getConfig().getDouble("banknot.max-miktar", 10_000_000);
        double amount = parseAmount(args[0]);
        if (amount < min || amount > max) {
            messages.send(player, "banknot.mesajlar.gecersiz", "min", plugin.economy().format(min), "max", plugin.economy().format(max));
            return true;
        }
        if (!plugin.economy().withdraw(player, amount)) {
            messages.send(player, "banknot.mesajlar.yetersiz");
            return true;
        }
        give(player, createNote(amount, player.getName()));
        messages.send(player, "banknot.mesajlar.verildi", "miktar", plugin.economy().format(amount));
        return true;
    }

    ItemStack createNote(double amount, String writer) {
        String formatted = plugin.economy().format(amount);
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Messages.itemText(plugin.messages().raw("banknot.esya-adi", "miktar", formatted, "oyuncu", writer)));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.messages().rawList("banknot.aciklama", "miktar", formatted, "oyuncu", writer)) {
            lore.add(Messages.itemText(line));
        }
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(valueKey, PersistentDataType.DOUBLE, amount);
        item.setItemMeta(meta);
        return item;
    }

    Double noteValue(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer().get(valueKey, PersistentDataType.DOUBLE);
    }

    private static void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
        for (ItemStack rest : leftover.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), rest);
        }
    }

    // Modul kapali olsa bile elde kalan banknotlar bozdurulabilir; para kaybolmasin.
    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        Double value = noteValue(hand);
        if (value == null) {
            return;
        }
        event.setCancelled(true);
        if (!plugin.economy().available()) {
            plugin.messages().send(player, "genel-mesajlar.ekonomi-yok");
            return;
        }
        int count = player.isSneaking() ? hand.getAmount() : 1;
        double total = value * count;
        // Once kagit alinir, sonra para yatirilir; yatirma olmazsa kagit geri verilir.
        int remaining = hand.getAmount() - count;
        ItemStack removed = hand.clone();
        removed.setAmount(count);
        if (remaining <= 0) {
            inventory.setItemInMainHand(null);
        } else {
            hand.setAmount(remaining);
            inventory.setItemInMainHand(hand);
        }
        if (!plugin.economy().deposit(player, total)) {
            give(player, removed);
            plugin.messages().send(player, "genel-mesajlar.ekonomi-yok");
            return;
        }
        plugin.messages().send(player, "banknot.mesajlar.bozduruldu", "miktar", plugin.economy().format(total));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 ? List.of("100", "1000", "10000") : List.of();
    }
}
