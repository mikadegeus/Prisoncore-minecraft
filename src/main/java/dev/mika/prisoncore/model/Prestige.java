package dev.mika.prisoncore.model;

/**
 * The derived bonuses granted by a prestige level. Computed from the per-prestige
 * configuration rather than stored, so tuning the config retroactively updates
 * everyone's multipliers.
 *
 * @param level           the prestige level (0 = not prestiged)
 * @param sellMultiplier  the sell-value multiplier contributed by prestige
 * @param tokenMultiplier the token multiplier contributed by prestige
 */
public record Prestige(int level, double sellMultiplier, double tokenMultiplier) {
}
