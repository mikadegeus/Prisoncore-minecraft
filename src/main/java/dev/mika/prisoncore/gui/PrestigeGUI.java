package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.managers.PrestigeManager;
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
 * Three-row prestige screen: shows the current prestige level and sell
 * multiplier, the bonus the next prestige grants, and buttons to prestige once or
 * as far as the player can afford to climb.
 */
public final class PrestigeGUI implements Menu {

    private static final int PRESTIGE_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int PRESTIGE_MAX_SLOT = 15;

    private final PrisonCore plugin;
    private final Player viewer;
    private final Inventory inventory;

    public PrestigeGUI(@NotNull PrisonCore plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SMALL_SIZE,
                MessageUtil.color("&8» &dPrestige &8«"));
        build();
    }

    private void build() {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SMALL_SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        PlayerData data = plugin.getPlayerDataManager().get(viewer);
        if (data == null) {
            return;
        }
        PrestigeManager prestige = plugin.getPrestigeManager();
        boolean maxRank = plugin.getRankManager().isMaxRank(data.rankIndex());

        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.NETHER_STAR)
                .name("&dJouw prestige")
                .lore(List.of(
                        "&7Prestige: &f" + data.prestige(),
                        "&7Sell-multiplier: &f" + NumberFormat.compact(prestige.sellMultiplier(data.prestige())) + "x",
                        "&7Volgende multiplier: &f"
                                + NumberFormat.compact(prestige.sellMultiplier(data.prestige() + 1)) + "x"))
                .glow()
                .build());

        List<String> prestigeLore = List.of(
                "&7Rank: &f" + plugin.getRankManager().displayName(data.rankIndex()),
                maxRank ? "&aJe kunt prestigen!" : "&cVereist maximale rank",
                " ",
                maxRank ? "&aKlik om te prestigen" : "&7Rank eerst naar het maximum");
        inventory.setItem(PRESTIGE_SLOT, new ItemBuilder(maxRank ? Material.NETHER_STAR : Material.GRAY_DYE)
                .name("&dPrestige")
                .lore(prestigeLore)
                .build());

        inventory.setItem(PRESTIGE_MAX_SLOT, new ItemBuilder(Material.BEACON)
                .name("&dPrestige Max")
                .lore(List.of(
                        "&7Klim met je saldo herhaaldelijk",
                        "&7naar het maximum en prestige.",
                        " ",
                        "&aKlik om maximaal te prestigen"))
                .build());
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return;
        }
        int slot = event.getRawSlot();
        if (slot == PRESTIGE_SLOT) {
            doPrestige(data);
            build();
        } else if (slot == PRESTIGE_MAX_SLOT) {
            doPrestigeMax(player, data);
            build();
        }
    }

    private void doPrestige(@NotNull PlayerData data) {
        PrestigeManager.PrestigeResult result = plugin.getPrestigeManager().prestige(data);
        if (result == PrestigeManager.PrestigeResult.SUCCESS) {
            sendPrestigeSuccess(data);
        } else {
            send(msg("prestige-need-max-rank"));
        }
    }

    private void doPrestigeMax(@NotNull Player player, @NotNull PlayerData data) {
        int gained = plugin.getPrestigeManager().prestigeMax(player, data);
        if (gained > 0) {
            sendPrestigeSuccess(data);
        } else {
            send(msg("prestige-need-max-rank"));
        }
    }

    private void sendPrestigeSuccess(@NotNull PlayerData data) {
        double multiplier = plugin.getPrestigeManager().sellMultiplier(data.prestige());
        String body = MessageUtil.replace(msg("prestige-success"), "prestige", String.valueOf(data.prestige()));
        body = MessageUtil.replace(body, "multiplier", NumberFormat.compact(multiplier));
        send(body);
    }

    private void send(@NotNull String body) {
        viewer.sendMessage(MessageUtil.color(plugin.getConfig().getString("messages.prefix", "") + body));
    }

    @NotNull
    private String msg(@NotNull String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
