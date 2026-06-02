package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.managers.SellManager;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.util.ItemBuilder;
import dev.mika.prisoncore.util.MessageUtil;
import dev.mika.prisoncore.util.NumberFormat;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * A drop-and-sell screen. The top five rows are a free drop area; the bottom row
 * holds a live total and a sell button. Items placed here are never lost: the
 * sell button only consumes sellable stacks, and anything left over is returned
 * to the player when the screen closes.
 */
public final class SellGUI implements Menu {

    private static final int SIZE = 54;
    private static final int DROP_AREA_END = 45;   // slots 0..44 are the drop area
    private static final int TOTAL_SLOT = 45;
    private static final int SELL_SLOT = 49;

    private final PrisonCore plugin;
    private final Player viewer;
    private final Inventory inventory;

    public SellGUI(@NotNull PrisonCore plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, SIZE, MessageUtil.color("&8» &eVerkopen &8«"));
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = DROP_AREA_END; slot < SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        inventory.setItem(SELL_SLOT, sellButton());
        updateTotal();
    }

    @NotNull
    private ItemStack sellButton() {
        return new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&aVerkoop alles")
                .lore(List.of("&7Verkoopt alle verkoopbare", "&7items hierboven."))
                .build();
    }

    private void updateTotal() {
        double total = currentValue();
        inventory.setItem(TOTAL_SLOT, new ItemBuilder(Material.GOLD_INGOT)
                .name("&6Totaal")
                .lore(List.of("&7Waarde: &f" + currency() + NumberFormat.money(total)))
                .build());
    }

    private double currentValue() {
        double multiplier = multiplier();
        double total = 0;
        for (int slot = 0; slot < DROP_AREA_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack != null) {
                total += plugin.getSellManager().valueOf(stack, multiplier);
            }
        }
        return total;
    }

    private double multiplier() {
        PlayerData data = plugin.getPlayerDataManager().get(viewer);
        return data != null ? plugin.getSellManager().effectiveMultiplier(viewer, data) : 1.0;
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        int raw = event.getRawSlot();
        // Bottom control row of the top inventory: never movable.
        if (raw >= DROP_AREA_END && raw < SIZE) {
            event.setCancelled(true);
            if (raw == SELL_SLOT) {
                doSell();
            }
            return;
        }
        // Drop area and player inventory clicks are allowed; refresh the total next tick.
        plugin.getServer().getScheduler().runTask(plugin, this::updateTotal);
    }

    @Override
    public boolean allowDrag(@NotNull InventoryDragEvent event) {
        for (int slot : event.getRawSlots()) {
            if (slot >= DROP_AREA_END && slot < SIZE) {
                return false;
            }
        }
        plugin.getServer().getScheduler().runTask(plugin, this::updateTotal);
        return true;
    }

    private void doSell() {
        PlayerData data = plugin.getPlayerDataManager().get(viewer);
        if (data == null) {
            return;
        }
        SellManager sell = plugin.getSellManager();
        double multiplier = sell.effectiveMultiplier(viewer, data);
        int items = 0;
        double earned = 0;
        for (int slot = 0; slot < DROP_AREA_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || !sell.isSellable(stack.getType())) {
                continue;
            }
            items += stack.getAmount();
            earned += sell.basePrice(stack.getType()) * stack.getAmount() * multiplier;
            inventory.setItem(slot, null);
        }
        if (items == 0 || !sell.deposit(viewer, data, earned)) {
            send(msg("nothing-to-sell"));
            updateTotal();
            return;
        }
        send(MessageUtil.replace(msg("sold"), "amount", currency() + NumberFormat.money(earned)));
        updateTotal();
    }

    @Override
    public void handleClose(@NotNull InventoryCloseEvent event) {
        // Return whatever the player left in the drop area so nothing is lost.
        for (int slot = 0; slot < DROP_AREA_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack == null || stack.getType().isAir()) {
                continue;
            }
            inventory.setItem(slot, null);
            viewer.getInventory().addItem(stack).values()
                    .forEach(left -> viewer.getWorld().dropItemNaturally(viewer.getLocation(), left));
        }
    }

    private void send(@NotNull String body) {
        viewer.sendMessage(MessageUtil.color(plugin.getConfig().getString("messages.prefix", "") + body));
    }

    @NotNull
    private String msg(@NotNull String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    @NotNull
    private String currency() {
        return plugin.getConfig().getString("economy.currency-symbol", "$");
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
