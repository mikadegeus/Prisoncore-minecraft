package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.managers.RankManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Three-row rankup screen: shows the current rank, the next rank and its cost,
 * the player's balance, and buttons to rank up once or as far as affordable.
 */
public final class RankupGUI implements Menu {

    private static final int RANKUP_SLOT = 11;
    private static final int INFO_SLOT = 13;
    private static final int RANKUP_MAX_SLOT = 15;

    private final PrisonCore plugin;
    private final Player viewer;
    private final Inventory inventory;

    public RankupGUI(@NotNull PrisonCore plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SMALL_SIZE,
                MessageUtil.color("&8» &bRankup &8«"));
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
        RankManager ranks = plugin.getRankManager();
        String currency = plugin.getConfig().getString("economy.currency-symbol", "$");
        double balance = plugin.getEconomyManager().getBalance(viewer);

        inventory.setItem(INFO_SLOT, new ItemBuilder(Material.BOOK)
                .name("&bJouw voortgang")
                .lore(List.of(
                        "&7Rank: &f" + ranks.displayName(data.rankIndex()),
                        "&7Prestige: &f" + data.prestige(),
                        "&7Saldo: &f" + currency + NumberFormat.money(balance)))
                .build());

        if (ranks.isMaxRank(data.rankIndex())) {
            inventory.setItem(RANKUP_SLOT, new ItemBuilder(Material.BARRIER)
                    .name("&cMaximale rank")
                    .lore(List.of("&7Prestige om verder te gaan."))
                    .build());
            inventory.setItem(RANKUP_MAX_SLOT, new ItemBuilder(Material.BARRIER)
                    .name("&cMaximale rank")
                    .lore(List.of("&7Prestige om verder te gaan."))
                    .build());
            return;
        }

        double cost = ranks.nextCost(data.rankIndex());
        boolean affordable = balance >= cost;
        String nextRank = ranks.displayName(data.rankIndex() + 1);

        List<String> rankupLore = new ArrayList<>();
        rankupLore.add("&7Volgende rank: &f" + nextRank);
        rankupLore.add("&7Kosten: &f" + currency + NumberFormat.money(cost));
        rankupLore.add(" ");
        rankupLore.add(affordable ? "&aKlik om te rankuppen" : "&cNiet genoeg geld");
        inventory.setItem(RANKUP_SLOT, new ItemBuilder(
                affordable ? Material.LIME_DYE : Material.GRAY_DYE)
                .name("&aRankup")
                .lore(rankupLore)
                .build());

        inventory.setItem(RANKUP_MAX_SLOT, new ItemBuilder(Material.EMERALD_BLOCK)
                .name("&aRankup Max")
                .lore(List.of(
                        "&7Rank zo ver mogelijk op",
                        "&7met je huidige saldo.",
                        " ",
                        "&aKlik om maximaal te rankuppen"))
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
        if (slot == RANKUP_SLOT) {
            doRankup(player, data);
            build();
        } else if (slot == RANKUP_MAX_SLOT) {
            doRankupMax(player, data);
            build();
        }
    }

    private void doRankup(@NotNull Player player, @NotNull PlayerData data) {
        RankManager ranks = plugin.getRankManager();
        double costBefore = ranks.nextCost(data.rankIndex());
        RankManager.RankupResult result = ranks.attemptRankup(player, data);
        switch (result) {
            case SUCCESS -> send(format("rankup-success", "rank", ranks.displayName(data.rankIndex())));
            case MAX_RANK -> send(msg("rankup-max-rank"));
            case NOT_ENOUGH_MONEY -> send(format("not-enough-money", "cost",
                    currency() + NumberFormat.money(costBefore)));
            case ERROR -> send("&cEr ging iets mis bij het rankuppen.");
        }
    }

    private void doRankupMax(@NotNull Player player, @NotNull PlayerData data) {
        RankManager ranks = plugin.getRankManager();
        double nextCost = ranks.nextCost(data.rankIndex());
        int gained = ranks.attemptRankupMax(player, data);
        if (gained > 0) {
            send("&aGerankt naar &f" + ranks.displayName(data.rankIndex()) + " &a(+" + gained + " ranks).");
        } else if (ranks.isMaxRank(data.rankIndex())) {
            send(msg("rankup-max-rank"));
        } else {
            send(format("not-enough-money", "cost", currency() + NumberFormat.money(nextCost)));
        }
    }

    @NotNull
    private String currency() {
        return plugin.getConfig().getString("economy.currency-symbol", "$");
    }

    private void send(@NotNull String body) {
        viewer.sendMessage(MessageUtil.color(plugin.getConfig().getString("messages.prefix", "") + body));
    }

    @NotNull
    private String msg(@NotNull String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    @NotNull
    private String format(@NotNull String key, @NotNull String placeholder, @NotNull String value) {
        return MessageUtil.replace(msg(key), placeholder, value);
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
