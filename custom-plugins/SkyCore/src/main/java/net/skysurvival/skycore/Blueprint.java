package net.skysurvival.skycore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;

/**
 * Spawn adasinin bloklari (jar icindeki spawn/ada.bp.gz, tools/spawn_island.py uretir).
 * Bicim: "SKYBP1", "palette N" + N blok, "runs M" + M satir "y z x uzunluk palet-sirasi"
 * (x yonunde ayni bloktan uzunluk kadar). Koordinatlar adanin merkezine gore.
 */
final class Blueprint {
    final String[] palette;
    final Box bounds;
    /** bounds icindeki her hucre icin palet sirasi + 1; 0 = hava. */
    private final short[] cells;

    private Blueprint(String[] palette, Box bounds, short[] cells) {
        this.palette = palette;
        this.bounds = bounds;
        this.cells = cells;
    }

    static Blueprint load(SkyCore plugin) throws IOException {
        try (InputStream in = plugin.getResource("spawn/ada.bp.gz")) {
            if (in == null) {
                throw new IOException("spawn/ada.bp.gz jar icinde yok");
            }
            return read(new BufferedReader(new InputStreamReader(new GZIPInputStream(in), StandardCharsets.UTF_8)));
        }
    }

    static Blueprint read(BufferedReader reader) throws IOException {
        if (!"SKYBP1".equals(reader.readLine())) {
            throw new IOException("ada.bp.gz bicimi taninmadi");
        }
        String[] palette = new String[count(reader.readLine(), "palette")];
        for (int i = 0; i < palette.length; i++) {
            palette[i] = reader.readLine();
        }
        int runCount = count(reader.readLine(), "runs");
        List<int[]> runs = new ArrayList<>(runCount);
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < runCount; i++) {
            String[] parts = reader.readLine().split(" ");
            int[] run = new int[5];
            for (int j = 0; j < 5; j++) {
                run[j] = Integer.parseInt(parts[j]);
            }
            if (run[3] < 1 || run[4] < 0 || run[4] >= palette.length) {
                throw new IOException("ada.bp.gz bozuk satir: " + String.join(" ", parts));
            }
            runs.add(run);
            minY = Math.min(minY, run[0]);
            maxY = Math.max(maxY, run[0]);
            minZ = Math.min(minZ, run[1]);
            maxZ = Math.max(maxZ, run[1]);
            minX = Math.min(minX, run[2]);
            maxX = Math.max(maxX, run[2] + run[3] - 1);
        }
        if (runs.isEmpty()) {
            throw new IOException("ada.bp.gz bos");
        }
        Box bounds = new Box(minX, minY, minZ, maxX, maxY, maxZ);
        short[] cells = new short[bounds.sizeX() * bounds.sizeY() * bounds.sizeZ()];
        for (int[] run : runs) {
            for (int k = 0; k < run[3]; k++) {
                cells[index(bounds, run[2] + k, run[0], run[1])] = (short) (run[4] + 1);
            }
        }
        return new Blueprint(palette, bounds, cells);
    }

    private static int count(String line, String name) throws IOException {
        if (line == null || !line.startsWith(name + " ")) {
            throw new IOException("ada.bp.gz: '" + name + "' satiri bekleniyordu");
        }
        return Integer.parseInt(line.substring(name.length() + 1).trim());
    }

    private static int index(Box b, int x, int y, int z) {
        return ((y - b.minY()) * b.sizeZ() + (z - b.minZ())) * b.sizeX() + (x - b.minX());
    }

    int size() {
        return cells.length;
    }

    /** Sira numarasindaki hucrenin koordinati (y, z, x sirasiyla dolasilir). */
    int x(int i) {
        return bounds.minX() + i % bounds.sizeX();
    }

    int z(int i) {
        return bounds.minZ() + (i / bounds.sizeX()) % bounds.sizeZ();
    }

    int y(int i) {
        return bounds.minY() + i / (bounds.sizeX() * bounds.sizeZ());
    }

    /** Hucredeki blogun palet sirasi; hava icin -1. */
    int cell(int i) {
        return cells[i] - 1;
    }

    /** Hucredeki blok; hava icin null. */
    String block(int i) {
        int value = cells[i];
        return value == 0 ? null : palette[value - 1];
    }

    String blockAt(int x, int y, int z) {
        return bounds.contains(x, y, z) ? block(index(bounds, x, y, z)) : null;
    }

    int solidCount() {
        int count = 0;
        for (short cell : cells) {
            if (cell != 0) {
                count++;
            }
        }
        return count;
    }
}
