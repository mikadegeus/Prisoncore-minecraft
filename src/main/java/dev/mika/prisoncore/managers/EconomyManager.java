package dev.mika.prisoncore.managers;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Thin wrapper around the Vault {@link Economy} service. Hooks into the
 * registered provider on startup and exposes the money operations PrisonCore
 * needs (rankup, prestige, selling). Money is never tracked by the plugin
 * itself; Vault remains the single source of truth.
 */
public final class EconomyManager {

    private final JavaPlugin plugin;
    private Economy economy;

    public EconomyManager(@NotNull JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Attempt to hook into Vault's economy provider.
     *
     * @return {@code true} when an economy provider was found and registered.
     */
    public boolean setup() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer()
                .getServicesManager()
                .getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        this.economy = rsp.getProvider();
        return this.economy != null;
    }

    public boolean isReady() {
        return economy != null;
    }

    public boolean has(@NotNull OfflinePlayer player, double amount) {
        return economy.has(player, amount);
    }

    public double getBalance(@NotNull OfflinePlayer player) {
        return economy.getBalance(player);
    }

    /**
     * Withdraw money from a player.
     *
     * @return {@code true} when the transaction succeeded.
     */
    public boolean withdraw(@NotNull OfflinePlayer player, double amount) {
        EconomyResponse response = economy.withdrawPlayer(player, amount);
        return response.transactionSuccess();
    }

    /**
     * Deposit money to a player.
     *
     * @return {@code true} when the transaction succeeded.
     */
    public boolean deposit(@NotNull OfflinePlayer player, double amount) {
        EconomyResponse response = economy.depositPlayer(player, amount);
        return response.transactionSuccess();
    }

    @Nullable
    public Economy getEconomy() {
        return economy;
    }
}
