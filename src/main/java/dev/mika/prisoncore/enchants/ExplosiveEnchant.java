package dev.mika.prisoncore.enchants;

import dev.mika.prisoncore.model.CustomEnchant;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Explosive: breaks a cube around the mined block. The cube's radius grows by one
 * every ten levels, so higher levels clear progressively larger pockets.
 */
public final class ExplosiveEnchant implements ProcEnchant {

    private static final int BASE_RADIUS = 1;
    private static final int LEVELS_PER_RADIUS = 10;

    @Override
    @NotNull
    public CustomEnchant enchant() {
        return CustomEnchant.EXPLOSIVE;
    }

    @Override
    public void apply(@NotNull ProcContext context, int level) {
        int radius = BASE_RADIUS + (level / LEVELS_PER_RADIUS);
        Block origin = context.origin();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (context.atCap()) {
                        return;
                    }
                    context.tryAdd(origin.getRelative(dx, dy, dz));
                }
            }
        }
    }
}
