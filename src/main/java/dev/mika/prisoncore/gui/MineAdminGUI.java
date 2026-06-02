package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.PaletteEntry;
import dev.mika.prisoncore.util.ItemBuilder;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin overview of every mine. Left-click teleports to a mine; right-click forces
 * a reset. Ignores rank gating so admins see and reach all mines.
 */
public final class MineAdminGUI implements Menu {

    private final PrisonCore plugin;
    private final Inventory inventory;
    private final Map<Integer, Mine> slotMines = new HashMap<>();

    public MineAdminGUI(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SIZE,
                MessageUtil.color("&8» &cMine beheer &8«"));
        build();
    }

    private void build() {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        slotMines.clear();

        int index = 0;
        for (Mine mine : plugin.getMineManager().getAllMines()) {
            if (index >= GuiLayout.pageCapacity()) {
                break;
            }
            int slot = GuiLayout.CONTENT_SLOTS[index];
            inventory.setItem(slot, render(mine));
            slotMines.put(slot, mine);
            index++;
        }
    }

    @NotNull
    private ItemStack render(@NotNull Mine mine) {
        List<String> lore = new ArrayList<>();
        lore.add("&7Wereld: &f" + mine.region().worldName());
        lore.add("&7Van: &f" + mine.region().minX() + ", " + mine.region().minY() + ", " + mine.region().minZ());
        lore.add("&7Tot: &f" + mine.region().maxX() + ", " + mine.region().maxY() + ", " + mine.region().maxZ());
        lore.add("&7Vereiste rank: &f" + plugin.getRankManager().displayName(mine.requiredRank()));
        lore.add("&7Reset: &f" + mine.resetSeconds() + "s &7(over " + plugin.getMineManager().secondsUntilReset(mine) + "s)");
        lore.add("&7Palette: &f" + mine.palette().entries().size() + " blokken");
        lore.add(" ");
        lore.add("&eLinks: teleporteer");
        lore.add("&eRechts: reset nu");

        return new ItemBuilder(iconFor(mine))
                .name("&c" + mine.name())
                .lore(lore)
                .hideAttributes()
                .build();
    }

    @NotNull
    private Material iconFor(@NotNull Mine mine) {
        List<PaletteEntry> entries = mine.palette().entries();
        if (entries.isEmpty()) {
            return Material.STONE;
        }
        Material material = entries.get(0).material();
        return material.isItem() ? material : Material.STONE;
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        Mine mine = slotMines.get(event.getRawSlot());
        if (mine == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        if (event.isRightClick()) {
            plugin.getMineManager().forceReset(mine.name());
            player.sendMessage(MessageUtil.color(prefix + "&aMine &f" + mine.name() + " &agereset."));
            build();
            return;
        }
        Location destination = mine.safeDestination();
        if (destination != null) {
            player.closeInventory();
            player.teleport(destination);
        }
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
