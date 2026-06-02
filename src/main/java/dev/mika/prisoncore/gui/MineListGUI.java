package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.PaletteEntry;
import dev.mika.prisoncore.model.PlayerData;
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
 * Lists every mine as a clickable icon. Mines the viewer has unlocked teleport
 * them in; locked mines show their rank requirement and refuse the click.
 */
public final class MineListGUI implements Menu {

    private final PrisonCore plugin;
    private final Inventory inventory;
    private final Map<Integer, Mine> slotMines = new HashMap<>();

    public MineListGUI(@NotNull PrisonCore plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SIZE,
                MessageUtil.color("&8» &bMines &8«"));
        build(viewer);
    }

    private void build(@NotNull Player viewer) {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SIZE; slot++) {
            inventory.setItem(slot, border);
        }

        int viewerRank = viewerRank(viewer);
        List<Mine> mines = new ArrayList<>(plugin.getMineManager().getAllMines());
        mines.sort((a, b) -> Integer.compare(a.requiredRank(), b.requiredRank()));

        int index = 0;
        for (Mine mine : mines) {
            if (index >= GuiLayout.pageCapacity()) {
                break;
            }
            int slot = GuiLayout.CONTENT_SLOTS[index];
            inventory.setItem(slot, renderMine(mine, viewerRank));
            slotMines.put(slot, mine);
            index++;
        }
    }

    @NotNull
    private ItemStack renderMine(@NotNull Mine mine, int viewerRank) {
        boolean unlocked = viewerRank >= mine.requiredRank();
        int resetIn = plugin.getMineManager().secondsUntilReset(mine);

        List<String> lore = new ArrayList<>();
        lore.add("&7Vereiste rank: &f" + plugin.getRankManager().displayName(mine.requiredRank()));
        lore.add("&7Reset over: &f" + resetIn + "s");
        lore.add(" ");
        lore.add(unlocked ? "&aKlik om te teleporteren" : "&cVergrendeld");

        ItemBuilder builder = new ItemBuilder(iconFor(mine))
                .name((unlocked ? "&b" : "&8") + mine.name())
                .lore(lore)
                .hideAttributes();
        if (unlocked) {
            builder.glow();
        }
        return builder.build();
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

    private int viewerRank(@NotNull Player viewer) {
        PlayerData data = plugin.getPlayerDataManager().get(viewer);
        return data != null ? data.rankIndex() : 0;
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        Mine mine = slotMines.get(event.getRawSlot());
        if (mine == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (viewerRank(player) < mine.requiredRank()) {
            String prefix = plugin.getConfig().getString("messages.prefix", "");
            String raw = MessageUtil.replace(
                    plugin.getConfig().getString("messages.mine-locked", ""),
                    "rank", plugin.getRankManager().displayName(mine.requiredRank()));
            player.sendMessage(MessageUtil.color(prefix + raw));
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
