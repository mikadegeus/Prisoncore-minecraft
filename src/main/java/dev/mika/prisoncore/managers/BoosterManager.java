package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Booster;
import dev.mika.prisoncore.model.BoosterType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Tracks active temporary boosters in memory, backed by the database so they
 * survive restarts. Boosters of the same type do not stack; the highest active
 * one wins. All access is on the main server thread.
 */
public final class BoosterManager {

    private final PrisonCore plugin;
    private final DatabaseManager database;
    private final List<Booster> active = new ArrayList<>();

    public BoosterManager(@NotNull PrisonCore plugin, @NotNull DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /** Load persisted boosters, dropping any that already expired. */
    public void load() {
        long now = System.currentTimeMillis();
        database.loadBoosters(list -> {
            for (Booster booster : list) {
                if (!booster.isExpired(now)) {
                    active.add(booster);
                }
            }
            plugin.getLogger().info("Loaded " + active.size() + " active booster(s).");
        });
        database.deleteExpiredBoosters(now);
    }

    /**
     * Start a booster. A {@code null} owner makes it server-wide.
     *
     * @param durationSeconds how long the booster lasts from now
     */
    public void addBooster(@Nullable UUID owner, @NotNull BoosterType type,
                           double multiplier, long durationSeconds) {
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;
        database.insertBooster(owner, type, multiplier, expiresAt,
                id -> active.add(new Booster(id, owner, type, multiplier, expiresAt)));
    }

    /**
     * The best active multiplier of a type for a player (their own boosters plus
     * any global ones), or {@code 1.0} when none apply.
     */
    public double multiplier(@NotNull UUID playerId, @NotNull BoosterType type) {
        long now = System.currentTimeMillis();
        double best = 1.0;
        for (Booster booster : active) {
            if (booster.type() == type && booster.appliesTo(playerId, now)) {
                best = Math.max(best, booster.multiplier());
            }
        }
        return best;
    }

    /** The active boosters applying to a player, for display. */
    @NotNull
    public List<Booster> activeFor(@NotNull UUID playerId) {
        long now = System.currentTimeMillis();
        List<Booster> result = new ArrayList<>();
        for (Booster booster : active) {
            if (booster.appliesTo(playerId, now)) {
                result.add(booster);
            }
        }
        return result;
    }

    /** Drop expired boosters from memory and the database. */
    public void sweep() {
        long now = System.currentTimeMillis();
        boolean removed = active.removeIf(booster -> booster.isExpired(now));
        if (removed) {
            database.deleteExpiredBoosters(now);
        }
    }
}
