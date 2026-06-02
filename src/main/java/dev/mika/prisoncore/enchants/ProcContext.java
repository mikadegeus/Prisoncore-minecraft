package dev.mika.prisoncore.enchants;

import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.util.Cuboid;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Random;
import java.util.Set;

/**
 * The working set a proc enchant adds extra blocks to during a single break. The
 * {@link #tryAdd(Block)} guard enforces the three invariants that keep the hot
 * path safe: stay within the cap, stay inside the mine, and never touch air.
 */
public final class ProcContext {

    private final Player player;
    private final Mine mine;
    private final Block origin;
    private final Set<Block> targets;
    private final Random random;
    private final int cap;

    public ProcContext(@NotNull Player player, @NotNull Mine mine, @NotNull Block origin,
                       @NotNull Set<Block> targets, @NotNull Random random, int cap) {
        this.player = player;
        this.mine = mine;
        this.origin = origin;
        this.targets = targets;
        this.random = random;
        this.cap = cap;
    }

    @NotNull
    public Player player() {
        return player;
    }

    /** The region of the mine this break happened in. */
    @NotNull
    public Cuboid mineRegion() {
        return mine.region();
    }

    @NotNull
    public Block origin() {
        return origin;
    }

    @NotNull
    public Random random() {
        return random;
    }

    /** Whether the working set has reached its per-break block cap. */
    public boolean atCap() {
        return targets.size() >= cap;
    }

    /**
     * Add a block to the working set if it is within the cap, inside the mine and
     * not air.
     *
     * @return {@code true} when the block was newly added.
     */
    public boolean tryAdd(@NotNull Block block) {
        if (targets.size() >= cap) {
            return false;
        }
        if (block.getType().isAir()) {
            return false;
        }
        if (!mine.region().contains(block.getLocation())) {
            return false;
        }
        return targets.add(block);
    }
}
