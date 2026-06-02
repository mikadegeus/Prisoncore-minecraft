package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.util.ItemBuilder;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.Function;

/**
 * The central prison menu. Each button opens another screen; opening is deferred
 * one tick so the click on this inventory finishes cleanly first.
 */
public final class PrisonHubGUI implements Menu {

    private static final int MINES_SLOT = 10;
    private static final int RANKUP_SLOT = 11;
    private static final int PRESTIGE_SLOT = 12;
    private static final int SELL_SLOT = 13;
    private static final int ENCHANT_SLOT = 14;
    private static final int STATS_SLOT = 15;
    private static final int LEADERBOARD_SLOT = 16;

    private final PrisonCore plugin;
    private final Inventory inventory;

    public PrisonHubGUI(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SMALL_SIZE,
                MessageUtil.color("&8» &bPrison &8«"));
        build();
    }

    private void build() {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SMALL_SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        inventory.setItem(MINES_SLOT, button(Material.IRON_PICKAXE, "&bMines", "Bekijk en bezoek de mines"));
        inventory.setItem(RANKUP_SLOT, button(Material.EMERALD, "&aRankup", "Klim omhoog in rang"));
        inventory.setItem(PRESTIGE_SLOT, button(Material.NETHER_STAR, "&dPrestige", "Prestige voor multipliers"));
        inventory.setItem(SELL_SLOT, button(Material.GOLD_INGOT, "&eVerkopen", "Verkoop je gemijnde blokken"));
        inventory.setItem(ENCHANT_SLOT, button(Material.ENCHANTED_BOOK, "&dEnchants", "Upgrade je pickaxe"));
        inventory.setItem(STATS_SLOT, button(Material.BOOK, "&bStatistieken", "Bekijk je voortgang"));
        inventory.setItem(LEADERBOARD_SLOT, button(Material.GOLD_BLOCK, "&6Leaderboard", "Top spelers"));
    }

    @NotNull
    private ItemStack button(@NotNull Material material, @NotNull String name, @NotNull String description) {
        return new ItemBuilder(material)
                .name(name)
                .lore(List.of("&7" + description, " ", "&eKlik om te openen"))
                .hideAttributes()
                .glow()
                .build();
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        Function<Player, Menu> target = menuFor(event.getRawSlot());
        if (target == null) {
            return;
        }
        // Defer the open so this click finishes on the current inventory first.
        plugin.getServer().getScheduler().runTask(plugin,
                () -> player.openInventory(target.apply(player).getInventory()));
    }

    @org.jetbrains.annotations.Nullable
    private Function<Player, Menu> menuFor(int slot) {
        return switch (slot) {
            case MINES_SLOT -> player -> new MineListGUI(plugin, player);
            case RANKUP_SLOT -> player -> new RankupGUI(plugin, player);
            case PRESTIGE_SLOT -> player -> new PrestigeGUI(plugin, player);
            case SELL_SLOT -> player -> new SellGUI(plugin, player);
            case ENCHANT_SLOT -> player -> new EnchantGUI(plugin, player);
            case STATS_SLOT -> player -> new StatsGUI(plugin, player);
            case LEADERBOARD_SLOT -> player -> new LeaderboardGUI(plugin);
            default -> null;
        };
    }

    @Override
    @NotNull
    public Inventory getInventory() {
        return inventory;
    }
}
