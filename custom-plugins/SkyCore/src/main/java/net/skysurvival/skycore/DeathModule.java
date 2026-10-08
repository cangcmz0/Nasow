package net.skysurvival.skycore;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

/** Olunce koordinat mesaji ve PvP'de olen oyuncunun kafasinin dusmesi. */
final class DeathModule implements Module {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private final SkyCore plugin;

    DeathModule(SkyCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();

        if (plugin.getConfig().getBoolean("olum-koordinat.aktif", true)) {
            Location at = victim.getLocation();
            plugin.messages().send(victim, "olum-koordinat.mesaj", "x", at.getBlockX(), "y", at.getBlockY(),
                    "z", at.getBlockZ(), "dunya", at.getWorld().getName());
        }

        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim) || !plugin.getConfig().getBoolean("kafa-dusurme.aktif", true)) {
            return;
        }
        double chance = plugin.getConfig().getDouble("kafa-dusurme.sans-yuzde", 100);
        if (ThreadLocalRandom.current().nextDouble(100) >= chance) {
            return;
        }
        event.getDrops().add(head(victim, killer.getName()));
    }

    private ItemStack head(Player victim, String killer) {
        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) head.getItemMeta();
        // Profil skin bilgisini de tasir (SkinsRestorer ile crack oyuncularin skini de gorunur).
        meta.setPlayerProfile(victim.getPlayerProfile());
        Object[] pairs = {"oyuncu", victim.getName(), "avci", killer, "tarih", LocalDate.now().format(DATE)};
        meta.displayName(Messages.itemText(plugin.messages().raw("kafa-dusurme.esya-adi", pairs)));
        List<Component> lore = new ArrayList<>();
        for (String line : plugin.messages().rawList("kafa-dusurme.aciklama", pairs)) {
            lore.add(Messages.itemText(line));
        }
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }
}
