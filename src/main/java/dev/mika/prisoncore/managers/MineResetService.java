package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.util.Cuboid;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Refills a mine's region from its palette. Players standing inside are
 * teleported to safety first, then the region is rewritten in fixed-size batches
 * spread across server ticks so a large mine never freezes the main thread.
 *
 * <p>When FastAsyncWorldEdit is present a future revision can swap in an async
 * edit session; this native batched strategy is the dependency-free fallback and
 * the default. A guard prevents a mine from being reset while a previous reset of
 * the same mine is still running.
 */
public final class MineResetService {

    private static final int BLOCKS_PER_BATCH = 4000;

    private final PrisonCore plugin;
    private final Random random = new Random();
    private final Set<String> resetting = ConcurrentHashMap.newKeySet();

    public MineResetService(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Reset a mine. No-op when the world is unloaded or a reset for this mine is
     * already in progress.
     */
    public void reset(@NotNull Mine mine) {
        World world = mine.region().resolveWorld();
        if (world == null) {
            return;
        }
        if (!resetting.add(mine.name().toLowerCase())) {
            return;
        }
        evacuate(mine, world);
        new FillTask(mine, world).runTaskTimer(plugin, 1L, 1L);
    }

    /** Teleport every player currently inside the region to the mine's safe spot. */
    private void evacuate(@NotNull Mine mine, @NotNull World world) {
        Location destination = mine.safeDestination();
        if (destination == null) {
            return;
        }
        for (Player player : world.getPlayers()) {
            if (mine.region().contains(player.getLocation())) {
                player.teleport(destination);
            }
        }
    }

    /**
     * Walks the cuboid coordinate space, writing {@link #BLOCKS_PER_BATCH} blocks
     * per tick. Pre-resolves a {@link BlockData} per material so repeated palette
     * picks do not re-parse block data.
     */
    private final class FillTask extends BukkitRunnable {

        private final Mine mine;
        private final World world;
        private final Cuboid region;
        private final Map<Material, BlockData> dataCache = new HashMap<>();

        private int x;
        private int y;
        private int z;

        private FillTask(@NotNull Mine mine, @NotNull World world) {
            this.mine = mine;
            this.world = world;
            this.region = mine.region();
            this.x = region.minX();
            this.y = region.minY();
            this.z = region.minZ();
        }

        @Override
        public void run() {
            int processed = 0;
            while (processed < BLOCKS_PER_BATCH) {
                Material material = mine.palette().randomMaterial(random);
                BlockData data = dataCache.computeIfAbsent(material, Material::createBlockData);
                // Physics off: avoids cascading updates that would wreck performance.
                world.getBlockAt(x, y, z).setBlockData(data, false);
                processed++;
                if (!advance()) {
                    finish();
                    return;
                }
            }
        }

        /** Move the cursor to the next block; returns false when the region is done. */
        private boolean advance() {
            x++;
            if (x <= region.maxX()) {
                return true;
            }
            x = region.minX();
            z++;
            if (z <= region.maxZ()) {
                return true;
            }
            z = region.minZ();
            y++;
            return y <= region.maxY();
        }

        private void finish() {
            cancel();
            resetting.remove(mine.name().toLowerCase());
        }
    }
}
