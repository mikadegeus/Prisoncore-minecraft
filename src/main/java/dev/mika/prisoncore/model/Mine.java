package dev.mika.prisoncore.model;

import dev.mika.prisoncore.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A configured mine: a region with a weighted block palette, a rank requirement,
 * an optional teleport destination and reset rules. Configuration data, mutated
 * only by admin tooling, so it is a plain holder with getters.
 */
public final class Mine {

    private final String name;
    private final Cuboid region;
    private final MinePalette palette;
    private final int requiredRank;
    private final int resetSeconds;
    private final int resetPercentage;

    private final boolean hasTeleport;
    private final double tpX;
    private final double tpY;
    private final double tpZ;
    private final float tpYaw;
    private final float tpPitch;

    public Mine(@NotNull String name, @NotNull Cuboid region, @NotNull MinePalette palette,
                int requiredRank, int resetSeconds, int resetPercentage,
                boolean hasTeleport, double tpX, double tpY, double tpZ, float tpYaw, float tpPitch) {
        this.name = name;
        this.region = region;
        this.palette = palette;
        this.requiredRank = requiredRank;
        this.resetSeconds = resetSeconds;
        this.resetPercentage = resetPercentage;
        this.hasTeleport = hasTeleport;
        this.tpX = tpX;
        this.tpY = tpY;
        this.tpZ = tpZ;
        this.tpYaw = tpYaw;
        this.tpPitch = tpPitch;
    }

    @NotNull
    public String name() {
        return name;
    }

    @NotNull
    public Cuboid region() {
        return region;
    }

    @NotNull
    public MinePalette palette() {
        return palette;
    }

    public int requiredRank() {
        return requiredRank;
    }

    public int resetSeconds() {
        return resetSeconds;
    }

    public int resetPercentage() {
        return resetPercentage;
    }

    public boolean hasTeleport() {
        return hasTeleport;
    }

    public double tpX() {
        return tpX;
    }

    public double tpY() {
        return tpY;
    }

    public double tpZ() {
        return tpZ;
    }

    public float tpYaw() {
        return tpYaw;
    }

    public float tpPitch() {
        return tpPitch;
    }

    // ------------------------------------------------------------------
    //  Immutable edits (used by admin tooling)
    // ------------------------------------------------------------------

    @NotNull
    public Mine withRequiredRank(int newRequiredRank) {
        return new Mine(name, region, palette, newRequiredRank, resetSeconds, resetPercentage,
                hasTeleport, tpX, tpY, tpZ, tpYaw, tpPitch);
    }

    @NotNull
    public Mine withPalette(@NotNull MinePalette newPalette) {
        return new Mine(name, region, newPalette, requiredRank, resetSeconds, resetPercentage,
                hasTeleport, tpX, tpY, tpZ, tpYaw, tpPitch);
    }

    @NotNull
    public Mine withReset(int newResetSeconds, int newResetPercentage) {
        return new Mine(name, region, palette, requiredRank, newResetSeconds, newResetPercentage,
                hasTeleport, tpX, tpY, tpZ, tpYaw, tpPitch);
    }

    @NotNull
    public Mine withTeleport(double x, double y, double z, float yaw, float pitch) {
        return new Mine(name, region, palette, requiredRank, resetSeconds, resetPercentage,
                true, x, y, z, yaw, pitch);
    }

    /**
     * A safe destination to send players: the configured teleport when set,
     * otherwise a point two blocks above the region centre. Null when the world
     * is not currently loaded.
     */
    @Nullable
    public Location safeDestination() {
        World world = region.resolveWorld();
        if (world == null) {
            return null;
        }
        if (hasTeleport) {
            return new Location(world, tpX, tpY, tpZ, tpYaw, tpPitch);
        }
        double centreX = (region.minX() + region.maxX()) / 2.0 + 0.5;
        double centreZ = (region.minZ() + region.maxZ()) / 2.0 + 0.5;
        return new Location(world, centreX, region.maxY() + 2.0, centreZ);
    }
}
