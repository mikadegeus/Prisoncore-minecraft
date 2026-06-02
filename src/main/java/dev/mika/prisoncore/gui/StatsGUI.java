package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.util.ItemBuilder;
import dev.mika.prisoncore.util.MessageUtil;
import dev.mika.prisoncore.util.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A read-only summary of the player's prison progress: rank, prestige, balance,
 * tokens, blocks mined and effective sell multiplier.
 */
public final class StatsGUI implements Menu {

    private final PrisonCore plugin;
    private final Inventory inventory;

    public StatsGUI(@NotNull PrisonCore plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SMALL_SIZE,
                MessageUtil.color("&8» &bStatistieken &8«"));
        build(viewer);
    }

    private void build(@NotNull Player viewer) {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SMALL_SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        PlayerData data = plugin.getPlayerDataManager().get(viewer);
        if (data == null) {
            return;
        }
        String currency = plugin.getConfig().getString("economy.currency-symbol", "$");
        double balance = plugin.getEconomyManager().isReady() ? plugin.getEconomyManager().getBalance(viewer) : 0;
        double sellMultiplier = plugin.getMultiplierService().sellMultiplier(viewer, data);

        inventory.setItem(10, icon(Material.NAME_TAG, "&7Rank",
                plugin.getRankManager().displayName(data.rankIndex())));
        inventory.setItem(11, icon(Material.NETHER_STAR, "&7Prestige", String.valueOf(data.prestige())));
        inventory.setItem(12, icon(Material.GOLD_INGOT, "&7Saldo", currency + NumberFormat.money(balance)));
        inventory.setItem(13, icon(Material.SUNFLOWER, "&7Tokens", NumberFormat.money(data.tokens())));
        inventory.setItem(14, icon(Material.DIAMOND_PICKAXE, "&7Blokken gebroken",
                NumberFormat.money(data.blocksMined())));
        inventory.setItem(15, icon(Material.EXPERIENCE_BOTTLE, "&7Sell-multiplier",
                NumberFormat.compact(sellMultiplier) + "x"));
        inventory.setItem(16, icon(data.autosell() ? Material.LIME_DYE : Material.GRAY_DYE,
                "&7Autosell", data.autosell() ? "&aaan" : "&cuit"));
    }

    @NotNull
    private ItemStack icon(@NotNull Material material, @NotNull String name, @NotNull String value) {
        return new ItemBuilder(material)
                .name("&b" + name.substring(2))
                .lore(List.of("&f" + value))
                .hideAttributes()
                .build();
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
