package dev.mika.prisoncore.enchants;

import dev.mika.prisoncore.model.CustomEnchant;
import dev.mika.prisoncore.util.Cuboid;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Jackhammer: a rare, powerful proc that clears the entire horizontal layer at
 * the mined block's height, bounded by the mine and the per-break cap.
 */
public final class JackhammerEnchant implements ProcEnchant {

    @Override
    @NotNull
    public CustomEnchant enchant() {
        return CustomEnchant.JACKHAMMER;
    }

    @Override
    public void apply(@NotNull ProcContext context, int level) {
        Block origin = context.origin();
        World world = origin.getWorld();
        int y = origin.getY();
        Cuboid bounds = context.mineRegion();
        for (int x = bounds.minX(); x <= bounds.maxX(); x++) {
            for (int z = bounds.minZ(); z <= bounds.maxZ(); z++) {
                if (context.atCap()) {
                    return;
                }
                context.tryAdd(world.getBlockAt(x, y, z));
            }
        }
    }
}
