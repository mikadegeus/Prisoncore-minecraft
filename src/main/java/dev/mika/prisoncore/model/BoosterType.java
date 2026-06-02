package dev.mika.prisoncore.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * What a booster multiplies: sell income or token income.
 */
public enum BoosterType {
    SELL,
    TOKEN;

    /** Resolve from a user-supplied string, case-insensitively. */
    @Nullable
    public static BoosterType fromString(@NotNull String raw) {
        for (BoosterType type : values()) {
            if (type.name().equalsIgnoreCase(raw)) {
                return type;
            }
        }
        return null;
    }
}
