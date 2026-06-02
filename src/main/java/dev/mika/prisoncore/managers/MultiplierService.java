package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.model.BoosterType;
import dev.mika.prisoncore.model.PlayerData;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.jetbrains.annotations.NotNull;

/**
 * Single source of truth for a player's effective sell and token multipliers.
 * Combines three layers: the prestige bonus, the best permission-based bonus
 * ({@code prisoncore.multiplier.<n>}), and the best active booster.
 */
public final class MultiplierService {

    private static final String PERMISSION_PREFIX = "prisoncore.multiplier.";

    private final PrestigeManager prestige;
    private final BoosterManager boosters;

    public MultiplierService(@NotNull PrestigeManager prestige, @NotNull BoosterManager boosters) {
        this.prestige = prestige;
        this.boosters = boosters;
    }

    /** The effective sell multiplier: prestige x permission x booster. */
    public double sellMultiplier(@NotNull Player player, @NotNull PlayerData data) {
        return prestige.sellMultiplier(data.prestige())
                * permissionMultiplier(player)
                * boosters.multiplier(player.getUniqueId(), BoosterType.SELL);
    }

    /** The effective token multiplier: prestige x booster. */
    public double tokenMultiplier(@NotNull Player player, @NotNull PlayerData data) {
        return prestige.tokenMultiplier(data.prestige())
                * boosters.multiplier(player.getUniqueId(), BoosterType.TOKEN);
    }

    /** The highest permission-based sell multiplier the player holds. */
    public double permissionMultiplier(@NotNull Player player) {
        double best = 1.0;
        for (PermissionAttachmentInfo info : player.getEffectivePermissions()) {
            if (!info.getValue() || !info.getPermission().startsWith(PERMISSION_PREFIX)) {
                continue;
            }
            String suffix = info.getPermission().substring(PERMISSION_PREFIX.length());
            try {
                best = Math.max(best, Double.parseDouble(suffix));
            } catch (NumberFormatException ignored) {
                // Non-numeric multiplier permission node, skip it.
            }
        }
        return best;
    }
}
