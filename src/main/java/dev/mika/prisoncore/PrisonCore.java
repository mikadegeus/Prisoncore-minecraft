package dev.mika.prisoncore;

import dev.mika.prisoncore.commands.BoosterCommand;
import dev.mika.prisoncore.commands.EnchantCommand;
import dev.mika.prisoncore.commands.MineCommand;
import dev.mika.prisoncore.commands.PrestigeCommand;
import dev.mika.prisoncore.commands.PrisonAdminCommand;
import dev.mika.prisoncore.commands.PrisonCommand;
import dev.mika.prisoncore.commands.RankupCommand;
import dev.mika.prisoncore.commands.SellCommand;
import dev.mika.prisoncore.commands.TokensCommand;
import dev.mika.prisoncore.listeners.BlockBreakListener;
import dev.mika.prisoncore.listeners.GUIListener;
import dev.mika.prisoncore.listeners.PickaxeProtectionListener;
import dev.mika.prisoncore.listeners.PlayerConnectionListener;
import dev.mika.prisoncore.listeners.WandListener;
import dev.mika.prisoncore.managers.BoosterManager;
import dev.mika.prisoncore.managers.DatabaseManager;
import dev.mika.prisoncore.managers.EconomyManager;
import dev.mika.prisoncore.managers.EnchantManager;
import dev.mika.prisoncore.managers.MineManager;
import dev.mika.prisoncore.managers.MineResetService;
import dev.mika.prisoncore.managers.MultiplierService;
import dev.mika.prisoncore.managers.PlayerDataManager;
import dev.mika.prisoncore.managers.PrestigeManager;
import dev.mika.prisoncore.managers.RankManager;
import dev.mika.prisoncore.managers.SelectionManager;
import dev.mika.prisoncore.managers.SellManager;
import dev.mika.prisoncore.pickaxe.PickaxeFactory;
import dev.mika.prisoncore.pickaxe.PickaxeKeys;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.PluginCommand;
import org.bukkit.command.TabCompleter;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * PrisonCore main plugin class. Wires together the configuration, Vault economy
 * hook, database pool and player-data cache, registers commands and listeners,
 * and schedules the periodic player-data flush.
 *
 * <p>This is milestone 1 (foundation). Later milestones add the mine, rank,
 * prestige, sell and enchant systems on top of these getters.
 */
public final class PrisonCore extends JavaPlugin {

    private static final long TICKS_PER_SECOND = 20L;

    private DatabaseManager databaseManager;
    private EconomyManager economyManager;
    private PlayerDataManager playerDataManager;
    private MineResetService mineResetService;
    private MineManager mineManager;
    private RankManager rankManager;
    private PrestigeManager prestigeManager;
    private BoosterManager boosterManager;
    private MultiplierService multiplierService;
    private SellManager sellManager;
    private PickaxeKeys pickaxeKeys;
    private EnchantManager enchantManager;
    private PickaxeFactory pickaxeFactory;
    private SelectionManager selectionManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveDefaultResources();

        // Economy is required: rankup, prestige and selling all move money via Vault.
        economyManager = new EconomyManager(this);
        if (!economyManager.setup()) {
            getLogger().severe("Vault economy provider not found. Disabling PrisonCore.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        databaseManager = new DatabaseManager(this);
        if (!databaseManager.connect()) {
            getLogger().severe("Database unavailable. Disabling PrisonCore.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        playerDataManager = new PlayerDataManager(this, databaseManager);

        rankManager = new RankManager(this, economyManager);
        rankManager.load();
        prestigeManager = new PrestigeManager(this, rankManager);
        boosterManager = new BoosterManager(this, databaseManager);
        boosterManager.load();
        multiplierService = new MultiplierService(prestigeManager, boosterManager);
        sellManager = new SellManager(this, economyManager, multiplierService, rankManager);
        sellManager.load();

        pickaxeKeys = new PickaxeKeys(this);
        enchantManager = new EnchantManager(this, pickaxeKeys);
        enchantManager.load();
        pickaxeFactory = new PickaxeFactory(this, pickaxeKeys, enchantManager);
        selectionManager = new SelectionManager(this);

        mineResetService = new MineResetService(this);
        mineManager = new MineManager(this, databaseManager, mineResetService);
        mineManager.load();

        registerCommands();
        registerListeners();
        scheduleTasks();

        // Load data for anyone already online (e.g. on a /reload).
        getServer().getOnlinePlayers().forEach(playerDataManager::load);

        getLogger().info("PrisonCore enabled successfully.");
    }

    @Override
    public void onDisable() {
        if (playerDataManager != null) {
            playerDataManager.flushAllBlocking();
        }
        if (databaseManager != null) {
            databaseManager.close();
        }
        getLogger().info("PrisonCore disabled.");
    }

    /** Copy the bundled configuration files into the data folder on first run. */
    private void saveDefaultResources() {
        saveResourceIfAbsent("ranks.yml");
        saveResourceIfAbsent("sellprices.yml");
        saveResourceIfAbsent("enchants.yml");
        saveResourceIfAbsent("mines.yml");
    }

    private void saveResourceIfAbsent(@NotNull String name) {
        if (!new java.io.File(getDataFolder(), name).exists()) {
            saveResource(name, false);
        }
    }

    private void registerCommands() {
        PrisonCommand prison = new PrisonCommand(this);
        bind("prison", prison, prison);

        MineCommand mine = new MineCommand(this);
        bind("mine", mine, mine);

        RankupCommand rankup = new RankupCommand(this);
        bind("rankup", rankup, rankup);
        bind("rankupmax", rankup, rankup);

        PrestigeCommand prestige = new PrestigeCommand(this);
        bind("prestige", prestige, prestige);

        SellCommand sell = new SellCommand(this);
        bind("sell", sell, sell);
        bind("sellall", sell, sell);

        EnchantCommand enchant = new EnchantCommand(this);
        bindExecutor("enchant", enchant);

        TokensCommand tokens = new TokensCommand(this);
        bind("tokens", tokens, tokens);

        BoosterCommand booster = new BoosterCommand(this);
        bind("booster", booster, booster);

        PrisonAdminCommand admin = new PrisonAdminCommand(this);
        bind("prisonadmin", admin, admin);
    }

    private void bindExecutor(@NotNull String name, @NotNull CommandExecutor executor) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
    }

    private void bind(@NotNull String name, @NotNull CommandExecutor executor,
                      @NotNull TabCompleter completer) {
        PluginCommand command = getCommand(name);
        if (command == null) {
            getLogger().severe("Command '" + name + "' is missing from plugin.yml.");
            return;
        }
        command.setExecutor(executor);
        command.setTabCompleter(completer);
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new PlayerConnectionListener(this), this);
        getServer().getPluginManager().registerEvents(new GUIListener(), this);
        getServer().getPluginManager().registerEvents(new PickaxeProtectionListener(this), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getServer().getPluginManager().registerEvents(new WandListener(this), this);
    }

    private void scheduleTasks() {
        long flushSeconds = Math.max(5, getConfig().getLong("mining.flush-interval-seconds", 30));
        long flushTicks = flushSeconds * TICKS_PER_SECOND;
        getServer().getScheduler().runTaskTimer(this,
                () -> playerDataManager.flushDirty(), flushTicks, flushTicks);

        // Drive every mine's reset countdown once per second.
        getServer().getScheduler().runTaskTimer(this,
                () -> mineManager.tick(), TICKS_PER_SECOND, TICKS_PER_SECOND);

        // Refresh held-pickaxe passive effects (haste, speed, jump) every two seconds.
        long passiveTicks = 2L * TICKS_PER_SECOND;
        getServer().getScheduler().runTaskTimer(this, () ->
                getServer().getOnlinePlayers().forEach(enchantManager::applyPassiveEffects),
                passiveTicks, passiveTicks);

        // Prune expired boosters every 30 seconds.
        long boosterTicks = 30L * TICKS_PER_SECOND;
        getServer().getScheduler().runTaskTimer(this, () -> boosterManager.sweep(),
                boosterTicks, boosterTicks);
    }

    @NotNull
    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    @NotNull
    public EconomyManager getEconomyManager() {
        return economyManager;
    }

    @NotNull
    public PlayerDataManager getPlayerDataManager() {
        return playerDataManager;
    }

    @NotNull
    public MineManager getMineManager() {
        return mineManager;
    }

    @NotNull
    public RankManager getRankManager() {
        return rankManager;
    }

    @NotNull
    public PrestigeManager getPrestigeManager() {
        return prestigeManager;
    }

    @NotNull
    public SellManager getSellManager() {
        return sellManager;
    }

    @NotNull
    public BoosterManager getBoosterManager() {
        return boosterManager;
    }

    @NotNull
    public MultiplierService getMultiplierService() {
        return multiplierService;
    }

    @NotNull
    public EnchantManager getEnchantManager() {
        return enchantManager;
    }

    @NotNull
    public PickaxeFactory getPickaxeFactory() {
        return pickaxeFactory;
    }

    @NotNull
    public PickaxeKeys getPickaxeKeys() {
        return pickaxeKeys;
    }

    @NotNull
    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
}
