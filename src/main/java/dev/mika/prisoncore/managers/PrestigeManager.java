package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.PlayerData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Owns prestige progression and the multipliers it grants. A player may prestige
 * only when they are at the maximum rank; doing so resets them to the first rank
 * and increments their prestige level. Multipliers are derived from the
 * per-prestige configuration so tuning the config retroactively updates everyone.
 */
public final class PrestigeManager {

    /** Outcome of a single prestige attempt, used to drive user messaging. */
    public enum PrestigeResult {
        SUCCESS,
        NEED_MAX_RANK
    }

    /** Safety bound on the prestige-max loop. */
    private static final int MAX_ITERATIONS = 1000;

    private final PrisonCore plugin;
    private final RankManager rankManager;

    public PrestigeManager(@NotNull PrisonCore plugin, @NotNull RankManager rankManager) {
        this.plugin = plugin;
        this.rankManager = rankManager;
    }

    private double perPrestigeSell() {
        return plugin.getConfig().getDouble("prestige.per-prestige-sell-multiplier", 0.1);
    }

    private double perPrestigeToken() {
        return plugin.getConfig().getDouble("prestige.per-prestige-token-multiplier", 0.05);
    }

    /** The sell multiplier contributed by a given prestige level. */
    public double sellMultiplier(int prestige) {
        return 1.0 + prestige * perPrestigeSell();
    }

    /** The token multiplier contributed by a given prestige level. */
    public double tokenMultiplier(int prestige) {
        return 1.0 + prestige * perPrestigeToken();
    }

    /**
     * Perform a single prestige. Requires the player to be at the maximum rank;
     * on success they drop back to rank A with their prestige incremented.
     */
    @NotNull
    public PrestigeResult prestige(@NotNull PlayerData data) {
        if (!rankManager.isMaxRank(data.rankIndex())) {
            return PrestigeResult.NEED_MAX_RANK;
        }
        data.setRankIndex(0);
        data.setPrestige(data.prestige() + 1);
        data.setSellMultiplier(sellMultiplier(data.prestige()));
        return PrestigeResult.SUCCESS;
    }

    /**
     * Repeatedly climb to the maximum rank (spending money) and prestige, until
     * the player can no longer afford to reach the top.
     *
     * @return the number of prestige levels gained.
     */
    public int prestigeMax(@NotNull Player player, @NotNull PlayerData data) {
        int gained = 0;
        for (int iteration = 0; iteration < MAX_ITERATIONS; iteration++) {
            if (!rankManager.isMaxRank(data.rankIndex())) {
                rankManager.attemptRankupMax(player, data);
            }
            if (!rankManager.isMaxRank(data.rankIndex())) {
                break;
            }
            prestige(data);
            gained++;
        }
        return gained;
    }
}
