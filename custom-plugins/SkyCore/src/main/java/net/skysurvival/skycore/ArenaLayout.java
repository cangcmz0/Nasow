package net.skysurvival.skycore;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.configuration.file.YamlConfiguration;

/**
 * KOTH etkinlik arenasinin noktalari (jar icindeki koth/arena.yml, tools/import_map.py uretir).
 * Harita: "The Hill" - Articray, TheZaner, xXFracXx (CC BY-SA 4.0).
 */
final class ArenaLayout {
    record Head(int x, int y, int z, String name, String uuid, String texture, String signature) {}

    record SignText(int x, int y, int z, String block, List<String> lines) {}

    final Box bounds;
    final Box hill;
    final List<IslandLayout.Point> spawns = new ArrayList<>();
    final IslandLayout.Point lobby;
    final Box portal;
    final List<Head> heads = new ArrayList<>();
    final List<SignText> signs = new ArrayList<>();
    final Map<String, IslandLayout.Hologram> holograms;

    private ArenaLayout(YamlConfiguration yaml) {
        bounds = IslandLayout.box(yaml.getConfigurationSection("boyut"));
        hill = IslandLayout.box(yaml.getConfigurationSection("tepe"));
        lobby = IslandLayout.point(yaml.getConfigurationSection("tanitim"));
        portal = IslandLayout.box(yaml.getConfigurationSection("portal"));
        for (Map<?, ?> raw : yaml.getMapList("dogus-noktalari")) {
            spawns.add(new IslandLayout.Point(number(raw.get("x")), number(raw.get("y")), number(raw.get("z")), (float) number(raw.get("yaw"))));
        }
        for (Map<?, ?> raw : yaml.getMapList("kafalar")) {
            heads.add(new Head((int) number(raw.get("x")), (int) number(raw.get("y")), (int) number(raw.get("z")),
                    text(raw.get("isim")), text(raw.get("uuid")), text(raw.get("doku")), text(raw.get("imza"))));
        }
        for (Map<?, ?> raw : yaml.getMapList("tabelalar")) {
            List<String> lines = new ArrayList<>();
            if (raw.get("satirlar") instanceof List<?> list) {
                list.forEach(line -> lines.add(text(line)));
            }
            signs.add(new SignText((int) number(raw.get("x")), (int) number(raw.get("y")), (int) number(raw.get("z")),
                    text(raw.get("blok")), lines));
        }
        holograms = IslandLayout.holograms(yaml.getConfigurationSection("hologramlar"));
    }

    static ArenaLayout load(SkyCore plugin) throws IOException {
        return new ArenaLayout(IslandLayout.resource(plugin, "koth/arena.yml"));
    }

    private static double number(Object value) {
        return value instanceof Number number ? number.doubleValue() : 0;
    }

    private static String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
