package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.PlayerData;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory cache of {@link PlayerData}, backed by the database. Data is loaded
 * on join, mutated in memory on the main thread during play, and flushed to the
 * database periodically and on quit. The block-break hot path only ever touches
 * the cached object, never the database directly.
 */
public final class PlayerDataManager {

    private final PrisonCore plugin;
    private final DatabaseManager database;
    private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

    public PlayerDataManager(@NotNull PrisonCore plugin, @NotNull DatabaseManager database) {
        this.plugin = plugin;
        this.database = database;
    }

    /**
     * Load a player's data into the cache, creating a fresh row when they are new.
     * Runs the database read asynchronously and finishes on the main thread.
     */
    public void load(@NotNull Player player) {
        UUID uuid = player.getUniqueId();
        String name = player.getName();
        database.loadPlayer(uuid, optional -> {
            PlayerData data = optional.orElseGet(() -> new PlayerData(uuid, name));
            data.setName(name);
            data.setLastSeen(System.currentTimeMillis());
            cache.put(uuid, data);
            // Persist immediately so a brand-new player exists in the table.
            if (optional.isEmpty()) {
                database.savePlayer(data, success -> data.markClean());
            }
        });
    }

    /**
     * @return the cached data for a player, or {@code null} when it has not
     * finished loading yet (or the player is offline).
     */
    @Nullable
    public PlayerData get(@NotNull UUID uuid) {
        return cache.get(uuid);
    }

    @Nullable
    public PlayerData get(@NotNull Player player) {
        return cache.get(player.getUniqueId());
    }

    /** Flush a single player and drop them from the cache (called on quit). */
    public void unload(@NotNull UUID uuid) {
        PlayerData data = cache.remove(uuid);
        if (data != null && data.isDirty()) {
            data.setLastSeen(System.currentTimeMillis());
            database.savePlayer(data, success -> {
                if (success) {
                    data.markClean();
                }
            });
        }
    }

    /** Asynchronously flush every dirty player. Used by the periodic task. */
    public void flushDirty() {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                database.savePlayer(data, success -> {
                    if (success) {
                        data.markClean();
                    }
                });
            }
        }
    }

    /**
     * Synchronously flush every dirty player. Used during {@code onDisable} when
     * the scheduler no longer runs async tasks.
     */
    public void flushAllBlocking() {
        for (PlayerData data : cache.values()) {
            if (data.isDirty()) {
                data.setLastSeen(System.currentTimeMillis());
                if (database.savePlayerBlocking(data)) {
                    data.markClean();
                }
            }
        }
        cache.clear();
    }
}
