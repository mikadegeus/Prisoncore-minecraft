package dev.mika.prisoncore.enchants;

import dev.mika.prisoncore.model.CustomEnchant;
import org.jetbrains.annotations.NotNull;

/**
 * A proc enchant: when it triggers, it adds extra blocks to the break's working
 * set. The trigger roll is handled centrally by {@link ProcEngine}; the handler
 * only decides <em>which</em> blocks to break once it has fired.
 */
public interface ProcEnchant {

    /** The enchant this handler implements. */
    @NotNull
    CustomEnchant enchant();

    /**
     * Add this enchant's extra blocks to the context. Implementations must use
     * {@link ProcContext#tryAdd(org.bukkit.block.Block)} so the cap, mine bounds
     * and air checks are respected.
     *
     * @param context the working set for this break
     * @param level   the enchant level on the pickaxe (always {@code >= 1})
     */
    void apply(@NotNull ProcContext context, int level);
}
