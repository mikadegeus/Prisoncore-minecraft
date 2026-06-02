package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.PlayerData;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads sell prices and turns mined blocks into money. The price a player
 * actually receives is the base price times an effective multiplier built from
 * their prestige and any permission-based bonus (boosters join the stack in M7).
 * Selling never mutates Vault directly here; it goes through {@link EconomyManager}.
 */
public final class SellManager {

    private final PrisonCore plugin;
    private final EconomyManager economy;
    private final MultiplierService multipliers;
    private final RankManager rankManager;

    private final Map<Material, Double> prices = new EnumMap<>(Material.class);

    public SellManager(@NotNull PrisonCore plugin, @NotNull EconomyManager economy,
                       @NotNull MultiplierService multipliers, @NotNull RankManager rankManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.multipliers = multipliers;
        this.rankManager = rankManager;
    }

    /** (Re)load sell prices from {@code sellprices.yml}. */
    public void load() {
        prices.clear();
        File file = new File(plugin.getDataFolder(), "sellprices.yml");
        if (!file.exists()) {
            plugin.getLogger().warning("sellprices.yml not found; nothing is sellable.");
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("prices");
        if (section == null) {
            return;
        }
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null) {
                plugin.getLogger().warning("Unknown sell material '" + key + "'; skipping.");
                continue;
            }
            prices.put(material, section.getDouble(key));
        }
        plugin.getLogger().info("Loaded " + prices.size() + " sell prices.");
    }

    // ------------------------------------------------------------------
    //  Pricing
    // ------------------------------------------------------------------

    public boolean isSellable(@Nullable Material material) {
        return material != null && prices.containsKey(material);
    }

    /** Base (pre-multiplier) price for one item of a material; 0 when not sellable. */
    public double basePrice(@NotNull Material material) {
        return prices.getOrDefault(material, 0.0);
    }

    /**
     * The effective sell multiplier for a player: prestige, permission and booster
     * bonuses combined. Delegates to {@link MultiplierService}.
     */
    public double effectiveMultiplier(@NotNull Player player, @NotNull PlayerData data) {
        return multipliers.sellMultiplier(player, data);
    }

    /** The post-multiplier value of a single stack for the given player. */
    public double valueOf(@NotNull ItemStack stack, double multiplier) {
        if (stack.getType().isAir() || !isSellable(stack.getType())) {
            return 0.0;
        }
        return basePrice(stack.getType()) * stack.getAmount() * multiplier;
    }

    // ------------------------------------------------------------------
    //  Selling
    // ------------------------------------------------------------------

    /** Sell the item in the player's main hand. */
    @NotNull
    public SaleResult sellHand(@NotNull Player player, @NotNull PlayerData data) {
        PlayerInventory inventory = player.getInventory();
        ItemStack hand = inventory.getItemInMainHand();
        if (hand.getType().isAir() || !isSellable(hand.getType())) {
            return SaleResult.EMPTY;
        }
        double multiplier = effectiveMultiplier(player, data);
        int amount = hand.getAmount();
        double earned = basePrice(hand.getType()) * amount * multiplier;
        if (!deposit(player, data, earned)) {
            return SaleResult.EMPTY;
        }
        inventory.setItemInMainHand(null);
        return new SaleResult(amount, earned);
    }

    /** Sell every sellable item in the player's main storage (hotbar + inventory). */
    @NotNull
    public SaleResult sellAll(@NotNull Player player, @NotNull PlayerData data) {
        PlayerInventory inventory = player.getInventory();
        double multiplier = effectiveMultiplier(player, data);
        int totalItems = 0;
        double earned = 0;

        ItemStack[] storage = inventory.getStorageContents();
        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack stack = storage[slot];
            if (stack == null || stack.getType().isAir() || !isSellable(stack.getType())) {
                continue;
            }
            totalItems += stack.getAmount();
            earned += basePrice(stack.getType()) * stack.getAmount() * multiplier;
            inventory.setItem(slot, null);
        }
        if (totalItems == 0 || !deposit(player, data, earned)) {
            return SaleResult.EMPTY;
        }
        return new SaleResult(totalItems, earned);
    }

    /**
     * Deposit a sale's proceeds and run the auto rank-up hook. Returns false (and
     * makes no change) when the Vault deposit fails, so callers never delete items
     * they could not pay for.
     */
    public boolean deposit(@NotNull Player player, @NotNull PlayerData data, double amount) {
        if (amount <= 0) {
            return false;
        }
        if (!economy.deposit(player, amount)) {
            return false;
        }
        rankManager.tryAutoRankup(player, data);
        return true;
    }

    /** Outcome of a sell operation. */
    public record SaleResult(int items, double earned) {
        public static final SaleResult EMPTY = new SaleResult(0, 0);

        public boolean sold() {
            return items > 0 && earned > 0;
        }
    }
}
