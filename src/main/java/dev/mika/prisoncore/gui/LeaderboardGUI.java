package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.managers.DatabaseManager;
import dev.mika.prisoncore.util.ItemBuilder;
import dev.mika.prisoncore.util.MessageUtil;
import dev.mika.prisoncore.util.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Top players by blocks mined. The data is read asynchronously; the inventory
 * shows a loading marker until the query returns, then fills in the ranking.
 */
public final class LeaderboardGUI implements Menu {

    private static final int LIMIT = 10;

    private final PrisonCore plugin;
    private final Inventory inventory;

    public LeaderboardGUI(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SIZE,
                MessageUtil.color("&8» &6Leaderboard &8«"));
        fillBorder();
        inventory.setItem(22, new ItemBuilder(Material.CLOCK).name("&7Laden...").build());
        plugin.getDatabaseManager().getTopBlocks(LIMIT, this::populate);
    }

    private void fillBorder() {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SIZE; slot++) {
            inventory.setItem(slot, border);
        }
    }

    private void populate(@NotNull List<DatabaseManager.TopEntry> entries) {
        fillBorder();
        if (entries.isEmpty()) {
            inventory.setItem(22, new ItemBuilder(Material.BARRIER).name("&7Nog geen data").build());
            return;
        }
        int index = 0;
        for (DatabaseManager.TopEntry entry : entries) {
            if (index >= GuiLayout.pageCapacity()) {
                break;
            }
            int position = index + 1;
            inventory.setItem(GuiLayout.CONTENT_SLOTS[index], render(position, entry));
            index++;
        }
    }

    @NotNull
    private ItemStack render(int position, @NotNull DatabaseManager.TopEntry entry) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Blokken: &f" + NumberFormat.money(entry.blocksMined()));
        lore.add("&7Tokens: &f" + NumberFormat.money(entry.tokens()));
        lore.add("&7Prestige: &f" + entry.prestige());
        Material icon = switch (position) {
            case 1 -> Material.GOLD_BLOCK;
            case 2 -> Material.IRON_BLOCK;
            case 3 -> Material.COPPER_BLOCK;
            default -> Material.PLAYER_HEAD;
        };
        return new ItemBuilder(icon)
                .name("&6#" + position + " &f" + entry.name())
                .lore(lore)
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
