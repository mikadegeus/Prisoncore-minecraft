package dev.mika.prisoncore.model;

import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * Mutable, in-memory state for one player. This is the hot-path object touched by
 * the block-break loop, so it is a plain mutable holder rather than an immutable
 * record. Every mutation flips the {@link #dirty} flag so the
 * {@link dev.mika.prisoncore.managers.PlayerDataManager} only persists changed
 * players. All access happens on the main server thread except the async flush,
 * which reads a consistent snapshot.
 */
public final class PlayerData {

    private final UUID uuid;
    private String name;

    private int rankIndex;
    private int prestige;
    private long tokens;
    private long blocksMined;
    private boolean autosell;
    private boolean autorankup;

    /** Cached effective sell multiplier, recomputed on load and on prestige. */
    private double sellMultiplier;
    private long lastSeen;

    /** Transient: set whenever a field changes, cleared after a successful flush. */
    private transient boolean dirty;

    public PlayerData(@NotNull UUID uuid, @NotNull String name) {
        this.uuid = uuid;
        this.name = name;
        this.rankIndex = 0;
        this.prestige = 0;
        this.tokens = 0L;
        this.blocksMined = 0L;
        this.autosell = false;
        this.autorankup = false;
        this.sellMultiplier = 1.0;
        this.lastSeen = System.currentTimeMillis();
        this.dirty = true;
    }

    /** Full constructor used when loading an existing row from the database. */
    public PlayerData(@NotNull UUID uuid, @NotNull String name, int rankIndex, int prestige,
                      long tokens, long blocksMined, boolean autosell, boolean autorankup,
                      double sellMultiplier, long lastSeen) {
        this.uuid = uuid;
        this.name = name;
        this.rankIndex = rankIndex;
        this.prestige = prestige;
        this.tokens = tokens;
        this.blocksMined = blocksMined;
        this.autosell = autosell;
        this.autorankup = autorankup;
        this.sellMultiplier = sellMultiplier;
        this.lastSeen = lastSeen;
        this.dirty = false;
    }

    @NotNull
    public UUID uuid() {
        return uuid;
    }

    @NotNull
    public String name() {
        return name;
    }

    public void setName(@NotNull String name) {
        if (!name.equals(this.name)) {
            this.name = name;
            this.dirty = true;
        }
    }

    public int rankIndex() {
        return rankIndex;
    }

    public void setRankIndex(int rankIndex) {
        this.rankIndex = rankIndex;
        this.dirty = true;
    }

    public int prestige() {
        return prestige;
    }

    public void setPrestige(int prestige) {
        this.prestige = prestige;
        this.dirty = true;
    }

    public long tokens() {
        return tokens;
    }

    public void setTokens(long tokens) {
        this.tokens = Math.max(0L, tokens);
        this.dirty = true;
    }

    /** Add (or, with a negative delta, subtract) tokens, clamped at zero. */
    public void addTokens(long delta) {
        setTokens(this.tokens + delta);
    }

    public long blocksMined() {
        return blocksMined;
    }

    public void addBlocksMined(long delta) {
        this.blocksMined += delta;
        this.dirty = true;
    }

    public boolean autosell() {
        return autosell;
    }

    public void setAutosell(boolean autosell) {
        this.autosell = autosell;
        this.dirty = true;
    }

    public boolean autorankup() {
        return autorankup;
    }

    public void setAutorankup(boolean autorankup) {
        this.autorankup = autorankup;
        this.dirty = true;
    }

    public double sellMultiplier() {
        return sellMultiplier;
    }

    public void setSellMultiplier(double sellMultiplier) {
        this.sellMultiplier = sellMultiplier;
        this.dirty = true;
    }

    public long lastSeen() {
        return lastSeen;
    }

    public void setLastSeen(long lastSeen) {
        this.lastSeen = lastSeen;
        this.dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public void markClean() {
        this.dirty = false;
    }
}
