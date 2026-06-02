package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.MinePalette;
import dev.mika.prisoncore.model.PaletteEntry;
import dev.mika.prisoncore.util.Cuboid;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Loads and indexes mines, answers fast "which mine is this location in" lookups,
 * and drives the per-mine reset countdown. Mines live in the database; on first
 * run an empty table is seeded from the bundled {@code mines.yml}.
 */
public final class MineManager {

    /** Seconds-remaining values at which an imminent-reset warning is broadcast. */
    private static final int[] WARNING_SECONDS = {60, 30, 10, 5};

    private final PrisonCore plugin;
    private final DatabaseManager database;
    private final MineResetService resetService;

    private final Map<String, Mine> byName = new ConcurrentHashMap<>();
    private final Map<String, List<Mine>> byWorld = new ConcurrentHashMap<>();
    private final Map<String, Integer> countdown = new ConcurrentHashMap<>();

    public MineManager(@NotNull PrisonCore plugin, @NotNull DatabaseManager database,
                       @NotNull MineResetService resetService) {
        this.plugin = plugin;
        this.database = database;
        this.resetService = resetService;
    }

    /** Load mines from the database, seeding from {@code mines.yml} when empty. */
    public void load() {
        List<Mine> fileMines = parseFromFile();
        database.loadAllMines(dbMines -> {
            List<Mine> active;
            if (dbMines.isEmpty() && !fileMines.isEmpty()) {
                for (Mine mine : fileMines) {
                    database.saveMine(mine, success -> { });
                }
                active = fileMines;
                plugin.getLogger().info("Seeded " + fileMines.size() + " mine(s) from mines.yml.");
            } else {
                active = dbMines;
            }
            index(active);
            plugin.getLogger().info("Loaded " + active.size() + " mine(s).");
        });
    }

    private void index(@NotNull List<Mine> mines) {
        byName.clear();
        byWorld.clear();
        countdown.clear();
        for (Mine mine : mines) {
            String key = mine.name().toLowerCase();
            byName.put(key, mine);
            byWorld.computeIfAbsent(mine.region().worldName(), w -> new ArrayList<>()).add(mine);
            countdown.put(key, mine.resetSeconds());
        }
    }

    /**
     * Per-second tick: decrement each mine's countdown, broadcast warnings and
     * trigger a reset when the timer reaches zero.
     */
    public void tick() {
        for (Mine mine : byName.values()) {
            String key = mine.name().toLowerCase();
            int left = countdown.getOrDefault(key, mine.resetSeconds()) - 1;
            if (left <= 0) {
                resetService.reset(mine);
                countdown.put(key, mine.resetSeconds());
                continue;
            }
            countdown.put(key, left);
            maybeWarn(mine, left);
        }
    }

    private void maybeWarn(@NotNull Mine mine, int secondsLeft) {
        for (int threshold : WARNING_SECONDS) {
            if (secondsLeft == threshold && mine.resetSeconds() > threshold) {
                broadcastToMine(mine, secondsLeft);
                return;
            }
        }
    }

    private void broadcastToMine(@NotNull Mine mine, int secondsLeft) {
        var world = mine.region().resolveWorld();
        if (world == null) {
            return;
        }
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String raw = plugin.getConfig().getString("messages.mine-resetting", "");
        String message = MessageUtil.replace(
                MessageUtil.replace(raw, "mine", mine.name()),
                "seconds", String.valueOf(secondsLeft));
        for (Player player : world.getPlayers()) {
            if (mine.region().contains(player.getLocation())) {
                player.sendMessage(MessageUtil.color(prefix + message));
            }
        }
    }

    /**
     * @return the mine containing the location, or {@code null} when none does.
     * Only scans mines in the location's world, so this stays cheap on the
     * block-break hot path.
     */
    @Nullable
    public Mine mineAt(@NotNull Location location) {
        if (location.getWorld() == null) {
            return null;
        }
        List<Mine> candidates = byWorld.get(location.getWorld().getName());
        if (candidates == null) {
            return null;
        }
        for (Mine mine : candidates) {
            if (mine.region().contains(location)) {
                return mine;
            }
        }
        return null;
    }

    @Nullable
    public Mine getMine(@NotNull String name) {
        return byName.get(name.toLowerCase());
    }

    @NotNull
    public Collection<Mine> getAllMines() {
        return byName.values();
    }

    /** Seconds until the given mine next resets (defaults to its full interval). */
    public int secondsUntilReset(@NotNull Mine mine) {
        return countdown.getOrDefault(mine.name().toLowerCase(), mine.resetSeconds());
    }

    /**
     * Add a new mine or replace an existing one (by name), persisting it. The
     * reset countdown is (re)started at the mine's full interval.
     */
    public void put(@NotNull Mine mine) {
        String key = mine.name().toLowerCase();
        // Drop any previous instance from the per-world index before re-adding.
        Mine previous = byName.get(key);
        if (previous != null) {
            List<Mine> world = byWorld.get(previous.region().worldName());
            if (world != null) {
                world.removeIf(existing -> existing.name().equalsIgnoreCase(mine.name()));
            }
        }
        byName.put(key, mine);
        byWorld.computeIfAbsent(mine.region().worldName(), w -> new ArrayList<>()).add(mine);
        countdown.put(key, mine.resetSeconds());
        database.saveMine(mine, success -> { });
    }

    /** Delete a mine by name from memory and the database. */
    public boolean remove(@NotNull String name) {
        String key = name.toLowerCase();
        Mine mine = byName.remove(key);
        if (mine == null) {
            return false;
        }
        List<Mine> world = byWorld.get(mine.region().worldName());
        if (world != null) {
            world.removeIf(existing -> existing.name().equalsIgnoreCase(name));
        }
        countdown.remove(key);
        database.deleteMine(name, success -> { });
        return true;
    }

    /** Force an immediate reset and restart the countdown. */
    public boolean forceReset(@NotNull String name) {
        Mine mine = getMine(name);
        if (mine == null) {
            return false;
        }
        resetService.reset(mine);
        countdown.put(mine.name().toLowerCase(), mine.resetSeconds());
        return true;
    }

    // ------------------------------------------------------------------
    //  mines.yml parsing (seed source)
    // ------------------------------------------------------------------

    @NotNull
    private List<Mine> parseFromFile() {
        File file = new File(plugin.getDataFolder(), "mines.yml");
        if (!file.exists()) {
            return List.of();
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("mines");
        if (root == null) {
            return List.of();
        }
        List<Mine> mines = new ArrayList<>();
        for (String name : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(name);
            if (section == null) {
                continue;
            }
            Mine mine = parseMine(name, section);
            if (mine != null) {
                mines.add(mine);
            }
        }
        return mines;
    }

    @Nullable
    private Mine parseMine(@NotNull String name, @NotNull ConfigurationSection section) {
        String world = section.getString("world");
        ConfigurationSection c1 = section.getConfigurationSection("corner1");
        ConfigurationSection c2 = section.getConfigurationSection("corner2");
        if (world == null || c1 == null || c2 == null) {
            plugin.getLogger().warning("Mine '" + name + "' is missing world or corners; skipping.");
            return null;
        }
        Cuboid region = new Cuboid(world,
                c1.getInt("x"), c1.getInt("y"), c1.getInt("z"),
                c2.getInt("x"), c2.getInt("y"), c2.getInt("z"));

        MinePalette palette = parsePalette(section.getMapList("palette"));
        int requiredRank = section.getInt("required-rank", 0);
        int resetSeconds = Math.max(1, section.getInt("reset-seconds", 300));
        int resetPercentage = section.getInt("reset-percentage", 0);

        ConfigurationSection tp = section.getConfigurationSection("teleport");
        if (tp != null) {
            return new Mine(name, region, palette, requiredRank, resetSeconds, resetPercentage,
                    true, tp.getDouble("x"), tp.getDouble("y"), tp.getDouble("z"),
                    (float) tp.getDouble("yaw"), (float) tp.getDouble("pitch"));
        }
        return new Mine(name, region, palette, requiredRank, resetSeconds, resetPercentage,
                false, 0, 0, 0, 0, 0);
    }

    @NotNull
    private MinePalette parsePalette(@NotNull List<Map<?, ?>> raw) {
        List<PaletteEntry> entries = new ArrayList<>();
        for (Map<?, ?> map : raw) {
            Object materialName = map.get("material");
            Object weight = map.get("weight");
            if (materialName == null || weight == null) {
                continue;
            }
            Material material = Material.matchMaterial(materialName.toString());
            if (material == null) {
                plugin.getLogger().warning("Unknown palette material '" + materialName + "'; skipping.");
                continue;
            }
            double parsedWeight = weight instanceof Number number ? number.doubleValue() : 0;
            entries.add(new PaletteEntry(material, parsedWeight));
        }
        return new MinePalette(entries);
    }
}
