package net.skysurvival.skycore;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;

/** Blok koordinatlariyla dikdortgen alan (iki ucu da dahil). Dunya bilgisi tutmaz. */
record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    static Box of(List<Integer> min, List<Integer> max) {
        return new Box(Math.min(min.get(0), max.get(0)), Math.min(min.get(1), max.get(1)), Math.min(min.get(2), max.get(2)),
                Math.max(min.get(0), max.get(0)), Math.max(min.get(1), max.get(1)), Math.max(min.get(2), max.get(2)));
    }

    Box shift(int x, int y, int z) {
        return new Box(minX + x, minY + y, minZ + z, maxX + x, maxY + y, maxZ + z);
    }

    Box grow(int horizontal, int vertical) {
        return new Box(minX - horizontal, minY - vertical, minZ - horizontal, maxX + horizontal, maxY + vertical, maxZ + horizontal);
    }

    boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    boolean contains(Location location) {
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    boolean containsColumn(int x, int z) {
        return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
    }

    int sizeX() {
        return maxX - minX + 1;
    }

    int sizeY() {
        return maxY - minY + 1;
    }

    int sizeZ() {
        return maxZ - minZ + 1;
    }

    Location center(World world) {
        return new Location(world, (minX + maxX + 1) / 2.0, (minY + maxY + 1) / 2.0, (minZ + maxZ + 1) / 2.0);
    }
}
