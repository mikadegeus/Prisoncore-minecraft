package dev.mika.prisoncore.managers;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Booster;
import dev.mika.prisoncore.model.BoosterType;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.MinePalette;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.util.Cuboid;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Owns the HikariCP connection pool and every persistence operation. Supports
 * MySQL and an SQLite fallback. Queries run off the main server thread; result
 * callbacks are posted back to the main thread so callers can safely touch the
 * Bukkit API. Saves can also run synchronously during {@code onDisable} when the
 * scheduler is no longer accepting tasks.
 */
public final class DatabaseManager {

    private static final String PLAYER_DATA = "player_data";
    private static final String MINES = "mines";
    private static final String BOOSTERS = "boosters";

    private final PrisonCore plugin;
    private HikariDataSource dataSource;
    private boolean mysql;

    public DatabaseManager(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    /**
     * Build the connection pool and ensure the schema exists.
     *
     * @return {@code true} when the pool connected and the tables are ready.
     */
    public boolean connect() {
        FileConfiguration config = plugin.getConfig();
        String type = config.getString("database.type", "sqlite").toLowerCase();
        this.mysql = type.equals("mysql");

        HikariConfig hikari = new HikariConfig();
        hikari.setPoolName("PrisonCore-Pool");
        hikari.setMaximumPoolSize(config.getInt("database.pool-size", 10));
        hikari.setConnectionTimeout(config.getLong("database.connection-timeout-ms", 30000));

        if (mysql) {
            String host = config.getString("database.host", "localhost");
            int port = config.getInt("database.port", 3306);
            String database = config.getString("database.database", "prisoncore");
            String user = config.getString("database.username", "root");
            String password = config.getString("database.password", "");
            hikari.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                    + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
            hikari.setUsername(user);
            hikari.setPassword(password);
            hikari.addDataSourceProperty("cachePrepStmts", "true");
            hikari.addDataSourceProperty("prepStmtCacheSize", "250");
        } else {
            File dbFile = new File(plugin.getDataFolder(), "prisoncore.db");
            hikari.setJdbcUrl("jdbc:sqlite:" + dbFile.getAbsolutePath());
            // SQLite is single-writer; a small pool avoids lock contention.
            hikari.setMaximumPoolSize(1);
        }

        try {
            this.dataSource = new HikariDataSource(hikari);
            createTables();
            return true;
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to initialise the database: " + e.getMessage());
            return false;
        }
    }

    private void createTables() throws SQLException {
        String playerDataDdl = "CREATE TABLE IF NOT EXISTS " + PLAYER_DATA + " ("
                + "uuid VARCHAR(36) PRIMARY KEY, "
                + "name VARCHAR(16) NOT NULL, "
                + "rank_index INT NOT NULL DEFAULT 0, "
                + "prestige INT NOT NULL DEFAULT 0, "
                + "tokens BIGINT NOT NULL DEFAULT 0, "
                + "blocks_mined BIGINT NOT NULL DEFAULT 0, "
                + "autosell TINYINT NOT NULL DEFAULT 0, "
                + "autorankup TINYINT NOT NULL DEFAULT 0, "
                + "sell_multiplier DOUBLE NOT NULL DEFAULT 1.0, "
                + "last_seen BIGINT NOT NULL"
                + ")";

        String minesDdl = "CREATE TABLE IF NOT EXISTS " + MINES + " ("
                + "name VARCHAR(32) PRIMARY KEY, "
                + "world VARCHAR(64) NOT NULL, "
                + "min_x INT NOT NULL, min_y INT NOT NULL, min_z INT NOT NULL, "
                + "max_x INT NOT NULL, max_y INT NOT NULL, max_z INT NOT NULL, "
                + "has_teleport TINYINT NOT NULL DEFAULT 0, "
                + "tp_x DOUBLE NOT NULL DEFAULT 0, tp_y DOUBLE NOT NULL DEFAULT 0, "
                + "tp_z DOUBLE NOT NULL DEFAULT 0, tp_yaw DOUBLE NOT NULL DEFAULT 0, "
                + "tp_pitch DOUBLE NOT NULL DEFAULT 0, "
                + "required_rank INT NOT NULL DEFAULT 0, "
                + "palette_json TEXT NOT NULL, "
                + "reset_seconds INT NOT NULL DEFAULT 300, "
                + "reset_percentage INT NOT NULL DEFAULT 0"
                + ")";

        String boostersId = mysql
                ? "id BIGINT AUTO_INCREMENT PRIMARY KEY"
                : "id INTEGER PRIMARY KEY AUTOINCREMENT";
        String boostersDdl = "CREATE TABLE IF NOT EXISTS " + BOOSTERS + " ("
                + boostersId + ", "
                + "uuid VARCHAR(36) DEFAULT NULL, "
                + "type VARCHAR(16) NOT NULL, "
                + "multiplier DOUBLE NOT NULL, "
                + "expires_at BIGINT NOT NULL"
                + ")";

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(playerDataDdl);
            statement.execute(minesDdl);
            statement.execute(boostersDdl);
        }
    }

    // ------------------------------------------------------------------
    //  Player data
    // ------------------------------------------------------------------

    /**
     * Load a player's data asynchronously. The callback receives an empty
     * optional when the player has no row yet, on the main server thread.
     */
    public void loadPlayer(@NotNull UUID uuid, @NotNull Consumer<Optional<PlayerData>> callback) {
        runAsync(() -> {
            Optional<PlayerData> result = readPlayer(uuid);
            runSync(() -> callback.accept(result));
        });
    }

    /**
     * Persist a player's data asynchronously. The callback receives success on
     * the main thread. A read-consistent copy of the mutable fields is taken on
     * the calling thread before handing off to the pool.
     */
    public void savePlayer(@NotNull PlayerData data, @NotNull Consumer<Boolean> callback) {
        PlayerSnapshot snapshot = PlayerSnapshot.of(data);
        runAsync(() -> {
            boolean ok = writePlayer(snapshot);
            runSync(() -> callback.accept(ok));
        });
    }

    /**
     * Persist a player's data on the <em>current</em> thread. Used during
     * {@code onDisable}, when the Bukkit scheduler no longer runs tasks.
     */
    public boolean savePlayerBlocking(@NotNull PlayerData data) {
        return writePlayer(PlayerSnapshot.of(data));
    }

    @NotNull
    private Optional<PlayerData> readPlayer(@NotNull UUID uuid) {
        String sql = "SELECT * FROM " + PLAYER_DATA + " WHERE uuid=?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, uuid.toString());
            try (ResultSet rs = statement.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapPlayer(rs));
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to read player " + uuid + ": " + e.getMessage());
        }
        return Optional.empty();
    }

    private boolean writePlayer(@NotNull PlayerSnapshot snapshot) {
        // Portable upsert: try UPDATE first, INSERT when no row was touched.
        String update = "UPDATE " + PLAYER_DATA + " SET name=?, rank_index=?, prestige=?, tokens=?, "
                + "blocks_mined=?, autosell=?, autorankup=?, sell_multiplier=?, last_seen=? WHERE uuid=?";
        String insert = "INSERT INTO " + PLAYER_DATA + " (uuid, name, rank_index, prestige, tokens, "
                + "blocks_mined, autosell, autorankup, sell_multiplier, last_seen) VALUES (?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                statement.setString(1, snapshot.name());
                statement.setInt(2, snapshot.rankIndex());
                statement.setInt(3, snapshot.prestige());
                statement.setLong(4, snapshot.tokens());
                statement.setLong(5, snapshot.blocksMined());
                statement.setInt(6, snapshot.autosell() ? 1 : 0);
                statement.setInt(7, snapshot.autorankup() ? 1 : 0);
                statement.setDouble(8, snapshot.sellMultiplier());
                statement.setLong(9, snapshot.lastSeen());
                statement.setString(10, snapshot.uuid().toString());
                if (statement.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                statement.setString(1, snapshot.uuid().toString());
                statement.setString(2, snapshot.name());
                statement.setInt(3, snapshot.rankIndex());
                statement.setInt(4, snapshot.prestige());
                statement.setLong(5, snapshot.tokens());
                statement.setLong(6, snapshot.blocksMined());
                statement.setInt(7, snapshot.autosell() ? 1 : 0);
                statement.setInt(8, snapshot.autorankup() ? 1 : 0);
                statement.setDouble(9, snapshot.sellMultiplier());
                statement.setLong(10, snapshot.lastSeen());
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save player " + snapshot.uuid() + ": " + e.getMessage());
            return false;
        }
    }

    @NotNull
    private PlayerData mapPlayer(@NotNull ResultSet rs) throws SQLException {
        UUID uuid = UUID.fromString(rs.getString("uuid"));
        String name = rs.getString("name");
        int rankIndex = rs.getInt("rank_index");
        int prestige = rs.getInt("prestige");
        long tokens = rs.getLong("tokens");
        long blocksMined = rs.getLong("blocks_mined");
        boolean autosell = rs.getInt("autosell") != 0;
        boolean autorankup = rs.getInt("autorankup") != 0;
        double sellMultiplier = rs.getDouble("sell_multiplier");
        long lastSeen = rs.getLong("last_seen");
        return new PlayerData(uuid, name, rankIndex, prestige, tokens, blocksMined,
                autosell, autorankup, sellMultiplier, lastSeen);
    }

    /**
     * Fetch the top players by blocks mined, newest data first, capped at
     * {@code limit}. Used by the leaderboard.
     */
    public void getTopBlocks(int limit, @NotNull Consumer<List<TopEntry>> callback) {
        runAsync(() -> {
            List<TopEntry> entries = new ArrayList<>();
            String sql = "SELECT name, blocks_mined, tokens, prestige FROM " + PLAYER_DATA
                    + " ORDER BY blocks_mined DESC LIMIT ?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setInt(1, limit);
                try (ResultSet rs = statement.executeQuery()) {
                    while (rs.next()) {
                        entries.add(new TopEntry(rs.getString("name"), rs.getLong("blocks_mined"),
                                rs.getLong("tokens"), rs.getInt("prestige")));
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to read leaderboard: " + e.getMessage());
            }
            runSync(() -> callback.accept(entries));
        });
    }

    /** A single leaderboard row. */
    public record TopEntry(@NotNull String name, long blocksMined, long tokens, int prestige) {
    }

    // ------------------------------------------------------------------
    //  Mines
    // ------------------------------------------------------------------

    /** Load every configured mine asynchronously; callback runs on the main thread. */
    public void loadAllMines(@NotNull Consumer<List<Mine>> callback) {
        runAsync(() -> {
            List<Mine> mines = new ArrayList<>();
            String sql = "SELECT * FROM " + MINES;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    mines.add(mapMine(rs));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load mines: " + e.getMessage());
            }
            runSync(() -> callback.accept(mines));
        });
    }

    /** Insert or update a mine (keyed by name) asynchronously. */
    public void saveMine(@NotNull Mine mine, @NotNull Consumer<Boolean> callback) {
        runAsync(() -> {
            boolean ok = writeMine(mine);
            runSync(() -> callback.accept(ok));
        });
    }

    private boolean writeMine(@NotNull Mine mine) {
        String update = "UPDATE " + MINES + " SET world=?, min_x=?, min_y=?, min_z=?, max_x=?, max_y=?, "
                + "max_z=?, has_teleport=?, tp_x=?, tp_y=?, tp_z=?, tp_yaw=?, tp_pitch=?, required_rank=?, "
                + "palette_json=?, reset_seconds=?, reset_percentage=? WHERE name=?";
        String insert = "INSERT INTO " + MINES + " (world, min_x, min_y, min_z, max_x, max_y, max_z, "
                + "has_teleport, tp_x, tp_y, tp_z, tp_yaw, tp_pitch, required_rank, palette_json, "
                + "reset_seconds, reset_percentage, name) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
        Cuboid region = mine.region();
        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement statement = connection.prepareStatement(update)) {
                bindMineBody(statement, mine, region);
                statement.setString(18, mine.name());
                if (statement.executeUpdate() > 0) {
                    return true;
                }
            }
            try (PreparedStatement statement = connection.prepareStatement(insert)) {
                bindMineBody(statement, mine, region);
                statement.setString(18, mine.name());
                return statement.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            plugin.getLogger().warning("Failed to save mine " + mine.name() + ": " + e.getMessage());
            return false;
        }
    }

    /** Bind columns 1..17 (the shared body); the caller binds the name in slot 18. */
    private void bindMineBody(@NotNull PreparedStatement statement, @NotNull Mine mine,
                              @NotNull Cuboid region) throws SQLException {
        statement.setString(1, region.worldName());
        statement.setInt(2, region.minX());
        statement.setInt(3, region.minY());
        statement.setInt(4, region.minZ());
        statement.setInt(5, region.maxX());
        statement.setInt(6, region.maxY());
        statement.setInt(7, region.maxZ());
        statement.setInt(8, mine.hasTeleport() ? 1 : 0);
        statement.setDouble(9, mine.tpX());
        statement.setDouble(10, mine.tpY());
        statement.setDouble(11, mine.tpZ());
        statement.setDouble(12, mine.tpYaw());
        statement.setDouble(13, mine.tpPitch());
        statement.setInt(14, mine.requiredRank());
        statement.setString(15, mine.palette().toJson());
        statement.setInt(16, mine.resetSeconds());
        statement.setInt(17, mine.resetPercentage());
    }

    @NotNull
    private Mine mapMine(@NotNull ResultSet rs) throws SQLException {
        String name = rs.getString("name");
        Cuboid region = new Cuboid(rs.getString("world"),
                rs.getInt("min_x"), rs.getInt("min_y"), rs.getInt("min_z"),
                rs.getInt("max_x"), rs.getInt("max_y"), rs.getInt("max_z"));
        MinePalette palette = MinePalette.fromJson(rs.getString("palette_json"));
        boolean hasTeleport = rs.getInt("has_teleport") != 0;
        return new Mine(name, region, palette,
                rs.getInt("required_rank"), rs.getInt("reset_seconds"), rs.getInt("reset_percentage"),
                hasTeleport, rs.getDouble("tp_x"), rs.getDouble("tp_y"), rs.getDouble("tp_z"),
                (float) rs.getDouble("tp_yaw"), (float) rs.getDouble("tp_pitch"));
    }

    /** Delete a mine by name asynchronously. */
    public void deleteMine(@NotNull String name, @NotNull Consumer<Boolean> callback) {
        runAsync(() -> {
            boolean ok = false;
            String sql = "DELETE FROM " + MINES + " WHERE name=?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, name);
                ok = statement.executeUpdate() > 0;
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to delete mine " + name + ": " + e.getMessage());
            }
            boolean result = ok;
            runSync(() -> callback.accept(result));
        });
    }

    // ------------------------------------------------------------------
    //  Boosters
    // ------------------------------------------------------------------

    /** Load every persisted booster asynchronously; callback runs on the main thread. */
    public void loadBoosters(@NotNull Consumer<List<Booster>> callback) {
        runAsync(() -> {
            List<Booster> boosters = new ArrayList<>();
            String sql = "SELECT * FROM " + BOOSTERS;
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql);
                 ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    BoosterType type = BoosterType.fromString(rs.getString("type"));
                    if (type == null) {
                        continue;
                    }
                    String ownerRaw = rs.getString("uuid");
                    UUID owner = ownerRaw == null ? null : UUID.fromString(ownerRaw);
                    boosters.add(new Booster(rs.getLong("id"), owner, type,
                            rs.getDouble("multiplier"), rs.getLong("expires_at")));
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to load boosters: " + e.getMessage());
            }
            runSync(() -> callback.accept(boosters));
        });
    }

    /**
     * Insert a booster asynchronously. The callback receives the generated id (or
     * {@code 0} on failure) on the main thread.
     */
    public void insertBooster(@Nullable UUID owner, @NotNull BoosterType type, double multiplier,
                              long expiresAt, @NotNull Consumer<Long> callback) {
        runAsync(() -> {
            long id = 0L;
            String sql = "INSERT INTO " + BOOSTERS + " (uuid, type, multiplier, expires_at) VALUES (?,?,?,?)";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                if (owner == null) {
                    statement.setNull(1, java.sql.Types.VARCHAR);
                } else {
                    statement.setString(1, owner.toString());
                }
                statement.setString(2, type.name());
                statement.setDouble(3, multiplier);
                statement.setLong(4, expiresAt);
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (keys.next()) {
                        id = keys.getLong(1);
                    }
                }
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to insert booster: " + e.getMessage());
            }
            long result = id;
            runSync(() -> callback.accept(result));
        });
    }

    /** Delete all boosters that have expired by the given time. Fire-and-forget. */
    public void deleteExpiredBoosters(long now) {
        runAsync(() -> {
            String sql = "DELETE FROM " + BOOSTERS + " WHERE expires_at<=?";
            try (Connection connection = dataSource.getConnection();
                 PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, now);
                statement.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().warning("Failed to prune boosters: " + e.getMessage());
            }
        });
    }

    // ------------------------------------------------------------------
    //  Lifecycle and scheduling helpers
    // ------------------------------------------------------------------

    private void runAsync(@NotNull Runnable task) {
        if (!isReady()) {
            return;
        }
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, task);
    }

    private void runSync(@NotNull Runnable task) {
        if (!plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, task);
    }

    /** @return {@code true} when the pool is connected and ready for queries. */
    public boolean isReady() {
        return dataSource != null && !dataSource.isClosed();
    }

    /** Close the pool. Safe to call multiple times. */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * An immutable snapshot of a player's persistable fields, captured on the
     * main thread so the async writer never reads a concurrently mutated object.
     */
    private record PlayerSnapshot(@NotNull UUID uuid, @NotNull String name, int rankIndex, int prestige,
                                  long tokens, long blocksMined, boolean autosell, boolean autorankup,
                                  double sellMultiplier, long lastSeen) {

        @NotNull
        static PlayerSnapshot of(@NotNull PlayerData data) {
            return new PlayerSnapshot(data.uuid(), data.name(), data.rankIndex(), data.prestige(),
                    data.tokens(), data.blocksMined(), data.autosell(), data.autorankup(),
                    data.sellMultiplier(), data.lastSeen());
        }
    }
}
