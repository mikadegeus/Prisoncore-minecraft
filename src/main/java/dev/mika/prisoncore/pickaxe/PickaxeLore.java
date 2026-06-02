package dev.mika.prisoncore.pickaxe;

import dev.mika.prisoncore.managers.EnchantManager;
import dev.mika.prisoncore.model.CustomEnchant;
import dev.mika.prisoncore.util.MessageUtil;
import net.kyori.adventure.text.Component;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the prison pickaxe's lore from its stored enchant levels. Kept separate
 * from {@link EnchantManager} so lore rendering has a single home and the manager
 * stays focused on state.
 */
public final class PickaxeLore {

    private PickaxeLore() {
    }

    /**
     * Build the lore lines for a pickaxe, listing every enabled enchant the pickaxe
     * has at level one or higher.
     */
    @NotNull
    public static List<Component> build(@NotNull EnchantManager enchants,
                                        @NotNull PersistentDataContainer container) {
        List<Component> lore = new ArrayList<>();
        lore.add(MessageUtil.color("&8» &7Enchants"));

        boolean any = false;
        for (CustomEnchant enchant : enchants.enabledEnchants()) {
            int level = enchants.level(container, enchant);
            if (level <= 0) {
                continue;
            }
            any = true;
            lore.add(MessageUtil.color("&7" + enchants.displayName(enchant) + " &f" + level));
        }
        if (!any) {
            lore.add(MessageUtil.color("&8(geen enchants, gebruik /enchant)"));
        }
        return lore;
    }
}
