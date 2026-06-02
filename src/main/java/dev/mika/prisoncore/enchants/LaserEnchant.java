package dev.mika.prisoncore.enchants;

import dev.mika.prisoncore.model.CustomEnchant;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.jetbrains.annotations.NotNull;

/**
 * Laser: breaks a straight line of blocks in the direction the player is facing.
 * The beam lengthens with level.
 */
public final class LaserEnchant implements ProcEnchant {

    private static final int BASE_LENGTH = 4;

    @Override
    @NotNull
    public CustomEnchant enchant() {
        return CustomEnchant.LASER;
    }

    @Override
    public void apply(@NotNull ProcContext context, int level) {
        BlockFace facing = context.player().getFacing();
        int length = BASE_LENGTH + level;
        Block origin = context.origin();
        for (int step = 1; step <= length; step++) {
            if (context.atCap()) {
                return;
            }
            context.tryAdd(origin.getRelative(facing, step));
        }
    }
}
