package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.model.Rank;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the rank ladder and all rank-up logic. Ranks are read from
 * {@code ranks.yml}; their costs are either taken verbatim ("explicit" mode) or
 * derived from a geometric curve ("formula" mode). A rank's {@code cost} is the
 * price to advance <em>out</em> of it to the next rank.
 */
public final class RankManager {

    /** Outcome of a single rank-up attempt, used to drive user messaging. */
    public enum RankupResult {
        SUCCESS,
        MAX_RANK,
        NOT_ENOUGH_MONEY,
        ERROR
    }

    private final PrisonCore plugin;
    private final EconomyManager economy;
    private final List<Rank> ranks = new ArrayList<>();

    public RankManager(@NotNull PrisonCore plugin, @NotNull EconomyManager economy) {
        this.plugin = plugin;
        this.economy = economy;
    }

    /** (Re)load the rank ladder from configuration. */
    public void load() {
        ranks.clear();

        String mode = plugin.getConfig().getString("ranks.mode", "formula").toLowerCase();
        double baseCost = plugin.getConfig().getDouble("ranks.base-cost", 1000);
        double multiplier = plugin.getConfig().getDouble("ranks.multiplier", 1.5);

        List<String> ids = readRankIds();
        for (int index = 0; index < ids.size(); index++) {
            String id = ids.get(index);
            double cost = mode.equals("explicit")
                    ? explicitCost(id)
                    : baseCost * Math.pow(multiplier, index);
            ranks.add(new Rank(id, id, index, cost));
        }
        plugin.getLogger().info("Loaded " + ranks.size() + " ranks (" + mode + " mode).");
    }

    @NotNull
    private List<String> readRankIds() {
        File file = new File(plugin.getDataFolder(), "ranks.yml");
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection section = yaml.getConfigurationSection("ranks");
            if (section != null && !section.getKeys(false).isEmpty()) {
                return new ArrayList<>(section.getKeys(false));
            }
        }
        // Fallback: A..Z.
        List<String> fallback = new ArrayList<>(26);
        for (char c = 'A'; c <= 'Z'; c++) {
            fallback.add(String.valueOf(c));
        }
        return fallback;
    }

    private double explicitCost(@NotNull String id) {
        File file = new File(plugin.getDataFolder(), "ranks.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return yaml.getDouble("ranks." + id, 0);
    }

    // ------------------------------------------------------------------
    //  Queries
    // ------------------------------------------------------------------

    public int size() {
        return ranks.size();
    }

    @Nullable
    public Rank getRank(int index) {
        return (index >= 0 && index < ranks.size()) ? ranks.get(index) : null;
    }

    public boolean isMaxRank(int index) {
        return index >= ranks.size() - 1;
    }

    /** The human-readable name for a rank index, or {@code "#index"} when unknown. */
    @NotNull
    public String displayName(int index) {
        Rank rank = getRank(index);
        return rank != null ? rank.displayName() : "#" + index;
    }

    /**
     * The cost to advance from the given rank index to the next, or {@code -1}
     * when already at the maximum rank.
     */
    public double nextCost(int index) {
        if (isMaxRank(index)) {
            return -1;
        }
        Rank current = getRank(index);
        return current != null ? current.cost() : -1;
    }

    // ------------------------------------------------------------------
    //  Actions
    // ------------------------------------------------------------------

    /** Attempt a single rank-up, moving money via Vault on success. */
    @NotNull
    public RankupResult attemptRankup(@NotNull Player player, @NotNull PlayerData data) {
        if (isMaxRank(data.rankIndex())) {
            return RankupResult.MAX_RANK;
        }
        double cost = nextCost(data.rankIndex());
        if (!economy.has(player, cost)) {
            return RankupResult.NOT_ENOUGH_MONEY;
        }
        if (!economy.withdraw(player, cost)) {
            return RankupResult.ERROR;
        }
        data.setRankIndex(data.rankIndex() + 1);
        return RankupResult.SUCCESS;
    }

    /**
     * Rank up as many times as the player's balance allows, in a single
     * transaction.
     *
     * @return the number of ranks gained (0 when none were affordable).
     */
    public int attemptRankupMax(@NotNull Player player, @NotNull PlayerData data) {
        double balance = economy.getBalance(player);
        double spent = 0;
        int index = data.rankIndex();
        int gained = 0;
        while (!isMaxRank(index)) {
            double cost = nextCost(index);
            if (balance - spent < cost) {
                break;
            }
            spent += cost;
            index++;
            gained++;
        }
        if (gained == 0) {
            return 0;
        }
        if (!economy.withdraw(player, spent)) {
            return 0;
        }
        data.setRankIndex(index);
        return gained;
    }

    /**
     * Auto rank-up hook: ranks the player up as far as affordable when they have
     * autorankup enabled. Called after selling and mining in later milestones.
     *
     * @return the number of ranks gained.
     */
    public int tryAutoRankup(@NotNull Player player, @NotNull PlayerData data) {
        if (!data.autorankup()) {
            return 0;
        }
        return attemptRankupMax(player, data);
    }
}
