package dev.mika.prisoncore.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

/**
 * An axis-aligned region between two corners in a single world, stored by world
 * name so it survives world reloads. Normalises its own min/max bounds and
 * offers fast containment checks plus block iteration for mine resets.
 */
public final class Cuboid {

    private final String worldName;
    private final int minX;
    private final int minY;
    private final int minZ;
    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public Cuboid(@NotNull String worldName,
                  int x1, int y1, int z1,
                  int x2, int y2, int z2) {
        this.worldName = worldName;
        this.minX = Math.min(x1, x2);
        this.minY = Math.min(y1, y2);
        this.minZ = Math.min(z1, z2);
        this.maxX = Math.max(x1, x2);
        this.maxY = Math.max(y1, y2);
        this.maxZ = Math.max(z1, z2);
    }

    @NotNull
    public String worldName() {
        return worldName;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }

    /** @return the total number of block positions in this region. */
    public long volume() {
        return (long) (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1);
    }

    /**
     * Whether the given location lies inside this region. A null location, or one
     * in a different world, is never contained.
     */
    public boolean contains(@Nullable Location location) {
        if (location == null || location.getWorld() == null) {
            return false;
        }
        if (!location.getWorld().getName().equals(worldName)) {
            return false;
        }
        return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    /** Whether the given block coordinates lie inside this region. */
    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    /**
     * Resolve the live {@link World} this region belongs to.
     *
     * @return the world, or {@code null} when it is not currently loaded.
     */
    @Nullable
    public World resolveWorld() {
        return org.bukkit.Bukkit.getWorld(worldName);
    }

    /**
     * Pick a random block inside this region.
     *
     * @return a block, or {@code null} when the world is not loaded.
     */
    @Nullable
    public Block randomBlock(@NotNull Random random) {
        World world = resolveWorld();
        if (world == null) {
            return null;
        }
        int x = minX + random.nextInt(maxX - minX + 1);
        int y = minY + random.nextInt(maxY - minY + 1);
        int z = minZ + random.nextInt(maxZ - minZ + 1);
        return world.getBlockAt(x, y, z);
    }
}
