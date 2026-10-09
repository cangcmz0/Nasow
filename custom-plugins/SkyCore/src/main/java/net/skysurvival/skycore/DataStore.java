package net.skysurvival.skycore;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/** Oyuncu verileri ve kelle odulleri: plugins/SkyCore/veriler.yml */
final class DataStore {
    record Bounty(UUID id, String name, double amount) {}

    record Origin(String world, int x, int y, int z) {}

    private final SkyCore plugin;
    private final File file;
    private YamlConfiguration yaml = new YamlConfiguration();
    private boolean dirty;

    DataStore(SkyCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "veriler.yml");
    }

    void load() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    void save() {
        try {
            yaml.save(file);
            dirty = false;
        } catch (IOException e) {
            plugin.getLogger().severe("veriler.yml kaydedilemedi: " + e.getMessage());
        }
    }

    void saveIfDirty() {
        if (dirty) {
            save();
        }
    }

    private String player(UUID id, String key) {
        return "oyuncular." + id + "." + key;
    }

    private void set(String path, Object value) {
        yaml.set(path, value);
        dirty = true;
    }

    // ---- Gunluk odul ----

    String lastDaily(UUID id) {
        return yaml.getString(player(id, "gunluk-son"));
    }

    int dailyStreak(UUID id) {
        return yaml.getInt(player(id, "gunluk-seri"), 0);
    }

    void setDaily(UUID id, String name, String date, int streak) {
        set(player(id, "isim"), name);
        set(player(id, "gunluk-son"), date);
        set(player(id, "gunluk-seri"), streak);
    }

    // ---- Aktiflik ----

    int activeMinutes(UUID id) {
        return yaml.getInt(player(id, "aktif-dakika"), 0);
    }

    void setActiveMinutes(UUID id, int minutes) {
        set(player(id, "aktif-dakika"), minutes);
    }

    // ---- Spawn adasi (kurulan yer) ----

    Origin islandOrigin() {
        String world = yaml.getString("spawn-adasi.dunya");
        if (world == null) {
            return null;
        }
        return new Origin(world, yaml.getInt("spawn-adasi.x"), yaml.getInt("spawn-adasi.y"), yaml.getInt("spawn-adasi.z"));
    }

    void setIslandOrigin(Origin origin) {
        set("spawn-adasi.dunya", origin.world());
        set("spawn-adasi.x", origin.x());
        set("spawn-adasi.y", origin.y());
        set("spawn-adasi.z", origin.z());
    }

    // ---- KOTH ----

    int kothWins(UUID id) {
        return yaml.getInt(player(id, "koth-galibiyet"), 0);
    }

    void addKothWin(UUID id, String name) {
        set(player(id, "isim"), name);
        set(player(id, "koth-galibiyet"), kothWins(id) + 1);
    }

    String lastKothWinner() {
        return yaml.getString("koth.son-kazanan", "");
    }

    void setLastKothWinner(String name) {
        set("koth.son-kazanan", name);
    }

    // ---- Kelle avi ----

    double bounty(UUID id) {
        return yaml.getDouble("kelleler." + id + ".miktar", 0);
    }

    void setBounty(UUID id, String name, double amount) {
        set("kelleler." + id + ".isim", name);
        set("kelleler." + id + ".miktar", amount);
    }

    void removeBounty(UUID id) {
        set("kelleler." + id, null);
    }

    List<Bounty> topBounties(int limit) {
        List<Bounty> list = new ArrayList<>();
        ConfigurationSection section = yaml.getConfigurationSection("kelleler");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    list.add(new Bounty(UUID.fromString(key), section.getString(key + ".isim", "?"), section.getDouble(key + ".miktar")));
                } catch (IllegalArgumentException ignored) {
                    // bozuk kayit
                }
            }
        }
        list.sort(Comparator.comparingDouble(Bounty::amount).reversed());
        return list.size() > limit ? list.subList(0, limit) : list;
    }
}
