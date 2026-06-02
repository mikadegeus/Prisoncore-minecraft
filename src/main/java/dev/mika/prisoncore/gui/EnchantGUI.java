package dev.mika.prisoncore.gui;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.managers.EnchantManager;
import dev.mika.prisoncore.model.CustomEnchant;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Buy and upgrade pickaxe enchants with tokens. Each enabled enchant is shown
 * with its current level and next cost; clicking upgrades it on the pickaxe the
 * player is holding.
 */
public final class EnchantGUI implements Menu {

    private static final int TOKENS_SLOT = 4;

    private final PrisonCore plugin;
    private final Player viewer;
    private final Inventory inventory;
    private final Map<Integer, CustomEnchant> slotEnchants = new HashMap<>();

    public EnchantGUI(@NotNull PrisonCore plugin, @NotNull Player viewer) {
        this.plugin = plugin;
        this.viewer = viewer;
        this.inventory = Bukkit.createInventory(this, GuiLayout.SIZE, MessageUtil.color("&8» &dEnchants &8«"));
        build();
    }

    private void build() {
        ItemStack border = new ItemBuilder(GuiLayout.BORDER_MATERIAL).name(" ").build();
        for (int slot = 0; slot < GuiLayout.SIZE; slot++) {
            inventory.setItem(slot, border);
        }
        slotEnchants.clear();

        PlayerData data = plugin.getPlayerDataManager().get(viewer);
        long tokens = data != null ? data.tokens() : 0L;
        inventory.setItem(TOKENS_SLOT, new ItemBuilder(Material.SUNFLOWER)
                .name("&eTokens: &f" + NumberFormat.money(tokens))
                .lore(List.of("&7Verdien tokens door te minen."))
                .glow()
                .build());

        EnchantManager enchants = plugin.getEnchantManager();
        ItemStack pickaxe = viewer.getInventory().getItemInMainHand();
        boolean holding = enchants.isPrisonPickaxe(pickaxe);

        int index = 0;
        for (CustomEnchant enchant : enchants.enabledEnchants()) {
            if (index >= GuiLayout.pageCapacity()) {
                break;
            }
            int slot = GuiLayout.CONTENT_SLOTS[index];
            int level = holding ? enchants.getLevel(pickaxe, enchant) : 0;
            inventory.setItem(slot, renderEnchant(enchant, level, holding));
            slotEnchants.put(slot, enchant);
            index++;
        }
    }

    @NotNull
    private ItemStack renderEnchant(@NotNull CustomEnchant enchant, int level, boolean holding) {
        EnchantManager enchants = plugin.getEnchantManager();
        int maxLevel = enchants.maxLevel(enchant);
        boolean maxed = level >= maxLevel;

        List<String> lore = new ArrayList<>();
        lore.add("&7Type: &f" + enchant.type().name());
        lore.add("&7Level: &f" + level + "&7/&f" + maxLevel);
        lore.add(" ");
        if (!holding) {
            lore.add("&cHou je Prison Pickaxe vast");
        } else if (maxed) {
            lore.add("&aMaximaal level bereikt");
        } else {
            long cost = (long) Math.ceil(enchants.upgradeCost(enchant, level));
            lore.add("&7Volgende level: &e" + NumberFormat.money(cost) + " tokens");
            lore.add("&aKlik om te upgraden");
        }

        ItemBuilder builder = new ItemBuilder(enchant.icon())
                .name("&d" + enchants.displayName(enchant))
                .lore(lore)
                .hideAttributes();
        if (level > 0) {
            builder.glow();
        }
        return builder.build();
    }

    @Override
    public void handleClick(@NotNull InventoryClickEvent event) {
        event.setCancelled(true);
        CustomEnchant enchant = slotEnchants.get(event.getRawSlot());
        if (enchant == null || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            return;
        }
        EnchantManager enchants = plugin.getEnchantManager();
        ItemStack pickaxe = player.getInventory().getItemInMainHand();
        if (!enchants.isPrisonPickaxe(pickaxe)) {
            send("&cHou je Prison Pickaxe vast om te enchanten.");
            return;
        }
        switch (enchants.attemptUpgrade(data, pickaxe, enchant)) {
            case SUCCESS -> {
                int level = enchants.getLevel(pickaxe, enchant);
                String body = MessageUtil.replace(msg("enchant-upgraded"), "enchant",
                        enchants.displayName(enchant));
                body = MessageUtil.replace(body, "level", String.valueOf(level));
                send(body);
            }
            case MAX_LEVEL -> send("&eDeze enchant is al maximaal.");
            case NOT_ENOUGH_TOKENS -> {
                long cost = (long) Math.ceil(enchants.upgradeCost(enchant, enchants.getLevel(pickaxe, enchant)));
                send(MessageUtil.replace(msg("not-enough-tokens"), "cost", NumberFormat.money(cost) + " tokens"));
            }
            case DISABLED -> send("&cDeze enchant is uitgeschakeld.");
        }
        build();
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
