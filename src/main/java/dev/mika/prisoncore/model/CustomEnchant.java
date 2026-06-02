package dev.mika.prisoncore.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The fixed set of custom pickaxe enchants. Each constant carries its config key,
 * its {@link EnchantType} and a GUI icon; the numeric tuning (max level, costs,
 * bonuses) is loaded per-enchant from {@code enchants.yml} into the
 * {@link dev.mika.prisoncore.managers.EnchantManager}.
 */
public enum CustomEnchant {

    EFFICIENCY("EFFICIENCY", EnchantType.PASSIVE, "Efficiency", Material.GOLDEN_PICKAXE),
    HASTE("HASTE", EnchantType.PASSIVE, "Haste", Material.REDSTONE),
    SPEED("SPEED", EnchantType.PASSIVE, "Speed", Material.SUGAR),
    JUMP("JUMP", EnchantType.PASSIVE, "Jump", Material.RABBIT_FOOT),
    FORTUNE("FORTUNE", EnchantType.GREED, "Fortune", Material.DIAMOND),
    TOKEN_GREED("TOKEN_GREED", EnchantType.GREED, "Token Greed", Material.SUNFLOWER),
    MONEY_GREED("MONEY_GREED", EnchantType.GREED, "Money Greed", Material.GOLD_INGOT),
    EXPLOSIVE("EXPLOSIVE", EnchantType.PROC, "Explosive", Material.TNT),
    LASER("LASER", EnchantType.PROC, "Laser", Material.BLAZE_ROD),
    JACKHAMMER("JACKHAMMER", EnchantType.PROC, "Jackhammer", Material.NETHERITE_PICKAXE);

    private final String configKey;
    private final EnchantType type;
    private final String defaultDisplayName;
    private final Material icon;

    CustomEnchant(@NotNull String configKey, @NotNull EnchantType type,
                  @NotNull String defaultDisplayName, @NotNull Material icon) {
        this.configKey = configKey;
        this.type = type;
        this.defaultDisplayName = defaultDisplayName;
        this.icon = icon;
    }

    @NotNull
    public String configKey() {
        return configKey;
    }

    @NotNull
    public EnchantType type() {
        return type;
    }

    @NotNull
    public String defaultDisplayName() {
        return defaultDisplayName;
    }

    @NotNull
    public Material icon() {
        return icon;
    }

    /** The lowercase id used for the persistent-data key on the pickaxe. */
    @NotNull
    public String keyId() {
        return name().toLowerCase();
    }

    /** Resolve by config key, case-insensitively. */
    @Nullable
    public static CustomEnchant fromConfigKey(@NotNull String key) {
        for (CustomEnchant enchant : values()) {
            if (enchant.configKey.equalsIgnoreCase(key)) {
                return enchant;
            }
        }
        return null;
    }
}
