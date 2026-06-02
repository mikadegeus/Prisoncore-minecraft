package dev.mika.prisoncore.listeners;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.enchants.ProcEngine;
import dev.mika.prisoncore.managers.EnchantManager;
import dev.mika.prisoncore.model.CustomEnchant;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.PlayerData;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.jetbrains.annotations.NotNull;

import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * The mining hot path. For a block broken inside a mine with the prison pickaxe it
 * gathers any proc-enchant blocks, then awards tokens and money (or items) with
 * the greed and prestige multipliers applied. It performs no synchronous I/O:
 * everything mutates the cached {@link PlayerData}, which the periodic flush
 * persists.
 */
public final class BlockBreakListener implements Listener {

    private final PrisonCore plugin;
    private final ProcEngine procEngine;

    public BlockBreakListener(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
        this.procEngine = new ProcEngine(plugin.getEnchantManager());
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(@NotNull BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block origin = event.getBlock();

        Mine mine = plugin.getMineManager().mineAt(origin.getLocation());
        if (mine == null) {
            return; // Outside any mine: leave vanilla behaviour untouched.
        }

        EnchantManager enchants = plugin.getEnchantManager();
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (!enchants.isPrisonPickaxe(hand)) {
            if (plugin.getConfig().getBoolean("pickaxe.required-to-mine", true)) {
                event.setCancelled(true);
            }
            return;
        }

        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return; // Profile not loaded yet; do not award anything.
        }

        // We award and clear blocks ourselves.
        event.setDropItems(false);

        ItemMeta meta = hand.getItemMeta();
        if (meta == null) {
            return;
        }
        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        int cap = Math.max(1, plugin.getConfig().getInt("mining.max-proc-blocks-per-break", 256));
        Set<Block> blocks = new LinkedHashSet<>();
        blocks.add(origin);
        procEngine.collect(player, mine, origin, pdc, blocks, cap);

        Map<Material, Integer> mined = tallyAndClear(blocks, origin, mine);
        int count = mined.values().stream().mapToInt(Integer::intValue).sum();
        if (count == 0) {
            return;
        }

        awardTokens(player, data, pdc, enchants, count);
        handleDrops(player, data, pdc, enchants, mined);
        data.addBlocksMined(count);
        plugin.getRankManager().tryAutoRankup(player, data);
    }

    /**
     * Count the broken blocks by material and clear the proc blocks (the origin is
     * left for the event to break). Skips air and anything outside the mine.
     */
    @NotNull
    private Map<Material, Integer> tallyAndClear(@NotNull Set<Block> blocks, @NotNull Block origin,
                                                 @NotNull Mine mine) {
        Map<Material, Integer> mined = new EnumMap<>(Material.class);
        for (Block block : blocks) {
            Material type = block.getType();
            if (type.isAir() || !mine.region().contains(block.getLocation())) {
                continue;
            }
            mined.merge(type, 1, Integer::sum);
            if (block != origin) {
                block.setType(Material.AIR, false);
            }
        }
        return mined;
    }

    private void awardTokens(@NotNull Player player, @NotNull PlayerData data,
                             @NotNull PersistentDataContainer pdc, @NotNull EnchantManager enchants, int count) {
        double base = plugin.getConfig().getDouble("mining.base-tokens-per-block", 1);
        int greedLevel = enchants.level(pdc, CustomEnchant.TOKEN_GREED);
        double greed = 1.0 + greedLevel * enchants.bonusPerLevel(CustomEnchant.TOKEN_GREED);
        double multiplier = plugin.getMultiplierService().tokenMultiplier(player, data);
        long tokens = Math.round(base * count * greed * multiplier);
        if (tokens > 0) {
            data.addTokens(tokens);
        }
    }

    private void handleDrops(@NotNull Player player, @NotNull PlayerData data,
                             @NotNull PersistentDataContainer pdc, @NotNull EnchantManager enchants,
                             @NotNull Map<Material, Integer> mined) {
        int fortuneLevel = enchants.level(pdc, CustomEnchant.FORTUNE);
        double fortune = 1.0 + fortuneLevel * enchants.bonusPerLevel(CustomEnchant.FORTUNE);

        if (data.autosell()) {
            int moneyGreedLevel = enchants.level(pdc, CustomEnchant.MONEY_GREED);
            double moneyGreed = 1.0 + moneyGreedLevel * enchants.bonusPerLevel(CustomEnchant.MONEY_GREED);
            double sellMultiplier = plugin.getSellManager().effectiveMultiplier(player, data);
            double money = 0;
            for (Map.Entry<Material, Integer> entry : mined.entrySet()) {
                money += plugin.getSellManager().basePrice(entry.getKey())
                        * entry.getValue() * sellMultiplier * fortune * moneyGreed;
            }
            if (money > 0) {
                plugin.getSellManager().deposit(player, data, money);
            }
            return;
        }

        // No autosell: hand the drops to the player, with Fortune multiplying amounts.
        for (Map.Entry<Material, Integer> entry : mined.entrySet()) {
            int amount = (int) Math.round(entry.getValue() * fortune);
            giveItems(player, entry.getKey(), amount);
        }
    }

    /** Give a material to the player in stack-sized chunks, dropping any overflow. */
    private void giveItems(@NotNull Player player, @NotNull Material material, int amount) {
        if (amount <= 0 || material.isAir()) {
            return;
        }
        int maxStack = Math.max(1, material.getMaxStackSize());
        int remaining = amount;
        while (remaining > 0) {
            int size = Math.min(remaining, maxStack);
            ItemStack stack = new ItemStack(material, size);
            player.getInventory().addItem(stack).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
            remaining -= size;
        }
    }
}
