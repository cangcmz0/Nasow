package net.skysurvival.skycore;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * Spawn adasinin noktalari (jar icindeki spawn/ada.yml). Koordinatlar adanin merkezine gore;
 * {@link #at} ile kurulan adanin gercek konumuna cevrilir.
 */
final class IslandLayout {
    record Point(double x, double y, double z, float yaw) {
        Location toLocation(World world, int ox, int oy, int oz) {
            return new Location(world, ox + x, oy + y, oz + z, yaw, 0);
        }
    }

    record Portal(Box area, String command) {}

    record Hologram(Point position, List<String> lines) {}

    final Box bounds;
    final Point spawn;
    final Map<String, Point> warps = new LinkedHashMap<>();
    final Map<String, Portal> portals = new LinkedHashMap<>();
    final Box arena;
    final Box koth;
    final Map<String, Point> crates = new LinkedHashMap<>();
    final Map<String, Hologram> holograms = new LinkedHashMap<>();

    private IslandLayout(YamlConfiguration yaml) {
        bounds = box(yaml.getConfigurationSection("boyut"));
        spawn = point(yaml.getConfigurationSection("spawn"));
        arena = box(yaml.getConfigurationSection("arena"));
        koth = box(yaml.getConfigurationSection("koth"));
        ConfigurationSection section = yaml.getConfigurationSection("warplar");
        for (String key : keys(section)) {
            warps.put(key, point(section.getConfigurationSection(key)));
        }
        section = yaml.getConfigurationSection("portallar");
        for (String key : keys(section)) {
            ConfigurationSection portal = section.getConfigurationSection(key);
            portals.put(key, new Portal(box(portal.getConfigurationSection("alan")), portal.getString("komut", "")));
        }
        section = yaml.getConfigurationSection("kasalar");
        for (String key : keys(section)) {
            crates.put(key, point(section.getConfigurationSection(key)));
        }
        holograms.putAll(holograms(yaml.getConfigurationSection("hologramlar")));
    }

    static IslandLayout load(SkyCore plugin) throws IOException {
        return new IslandLayout(resource(plugin, "spawn/ada.yml"));
    }

    static YamlConfiguration resource(SkyCore plugin, String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                throw new IOException(path + " jar icinde yok");
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                return YamlConfiguration.loadConfiguration(reader);
            }
        }
    }

    static Map<String, Hologram> holograms(ConfigurationSection section) {
        Map<String, Hologram> result = new LinkedHashMap<>();
        for (String key : keys(section)) {
            ConfigurationSection hologram = section.getConfigurationSection(key);
            result.put(key, new Hologram(point(hologram.getConfigurationSection("konum")), hologram.getStringList("satirlar")));
        }
        return result;
    }

    static Iterable<String> keys(ConfigurationSection section) {
        return section == null ? List.of() : section.getKeys(false);
    }

    static Point point(ConfigurationSection section) {
        if (section == null) {
            return new Point(0.5, 1, 0.5, 0);
        }
        return new Point(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"), (float) section.getDouble("yaw"));
    }

    static Box box(ConfigurationSection section) {
        if (section == null) {
            return new Box(0, 0, 0, 0, 0, 0);
        }
        return Box.of(section.getIntegerList("min"), section.getIntegerList("max"));
    }
}
