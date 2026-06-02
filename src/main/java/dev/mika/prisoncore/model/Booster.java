package dev.mika.prisoncore.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * A temporary multiplier. A {@code null} owner means the booster is server-wide
 * and applies to everyone; otherwise it applies only to that player.
 *
 * @param id        the database id (0 before it has been persisted)
 * @param owner     the player it belongs to, or {@code null} for a global booster
 * @param type      whether it boosts sell or token income
 * @param multiplier the multiplier value (e.g. {@code 2.0} for double)
 * @param expiresAt  epoch millis when the booster ends
 */
public record Booster(long id, @Nullable UUID owner, @NotNull BoosterType type,
                      double multiplier, long expiresAt) {

    public boolean isGlobal() {
        return owner == null;
    }

    public boolean isExpired(long now) {
        return now >= expiresAt;
    }

    /** Whether this booster applies to the given player at the given time. */
    public boolean appliesTo(@NotNull UUID playerId, long now) {
        return !isExpired(now) && (isGlobal() || playerId.equals(owner));
    }

    /** Remaining lifetime in seconds (never negative). */
    public long secondsLeft(long now) {
        return Math.max(0L, (expiresAt - now) / 1000L);
    }
}
