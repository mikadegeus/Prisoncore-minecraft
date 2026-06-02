package dev.mika.prisoncore.enchants;

import dev.mika.prisoncore.managers.EnchantManager;
import dev.mika.prisoncore.model.Mine;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Rolls each proc enchant on the pickaxe and lets the ones that fire add their
 * extra blocks to the break's working set. The trigger probability is
 * {@code level * proc-chance-per-level}, clamped to 100%.
 */
public final class ProcEngine {

    private final EnchantManager enchants;
    private final List<ProcEnchant> handlers;
    private final Random random = new Random();

    public ProcEngine(@NotNull EnchantManager enchants) {
        this.enchants = enchants;
        this.handlers = List.of(
                new ExplosiveEnchant(),
                new LaserEnchant(),
                new JackhammerEnchant());
    }

    /**
     * Roll every proc enchant and collect the blocks they break into {@code targets}.
     *
     * @param player    the miner
     * @param mine      the mine being mined
     * @param origin    the block the player actually broke
     * @param container the pickaxe's persistent data (enchant levels)
     * @param targets   the working set to add extra blocks to (already contains the origin)
     * @param cap       the maximum total blocks for this break
     */
    public void collect(@NotNull Player player, @NotNull Mine mine, @NotNull Block origin,
                        @NotNull PersistentDataContainer container, @NotNull Set<Block> targets, int cap) {
        ProcContext context = new ProcContext(player, mine, origin, targets, random, cap);
        for (ProcEnchant handler : handlers) {
            if (context.atCap()) {
                return;
            }
            int level = enchants.level(container, handler.enchant());
            if (level <= 0) {
                continue;
            }
            double chance = Math.min(1.0, level * enchants.procChancePerLevel(handler.enchant()));
            if (random.nextDouble() < chance) {
                handler.apply(context, level);
            }
        }
    }
}
