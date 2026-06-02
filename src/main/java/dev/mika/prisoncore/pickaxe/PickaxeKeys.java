package dev.mika.prisoncore.pickaxe;

import dev.mika.prisoncore.model.CustomEnchant;
import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.Map;

/**
 * Owns the {@link NamespacedKey}s used to tag the prison pickaxe and store its
 * enchant levels in persistent data. Created once at startup so every key shares
 * the plugin namespace.
 */
public final class PickaxeKeys {

    private final NamespacedKey identityKey;
    private final Map<CustomEnchant, NamespacedKey> enchantKeys = new EnumMap<>(CustomEnchant.class);

    public PickaxeKeys(@NotNull Plugin plugin) {
        this.identityKey = new NamespacedKey(plugin, "pickaxe");
        for (CustomEnchant enchant : CustomEnchant.values()) {
            enchantKeys.put(enchant, new NamespacedKey(plugin, "ench_" + enchant.keyId()));
        }
    }

    /** The marker key identifying an item as a prison pickaxe. */
    @NotNull
    public NamespacedKey identity() {
        return identityKey;
    }

    /** The key storing the given enchant's level. */
    @NotNull
    public NamespacedKey enchant(@NotNull CustomEnchant enchant) {
        return enchantKeys.get(enchant);
    }
}
