package dev.mika.prisoncore.pickaxe;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.managers.EnchantManager;
import dev.mika.prisoncore.model.CustomEnchant;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

/**
 * Builds and hands out the unique prison pickaxe. The pickaxe is tagged in
 * persistent data so it can be recognised regardless of renaming, and is given
 * once to players who do not already have one.
 */
public final class PickaxeFactory {

    private final PrisonCore plugin;
    private final PickaxeKeys keys;
    private final EnchantManager enchants;

    public PickaxeFactory(@NotNull PrisonCore plugin, @NotNull PickaxeKeys keys,
                          @NotNull EnchantManager enchants) {
        this.plugin = plugin;
        this.keys = keys;
        this.enchants = enchants;
    }

    /** Create a fresh prison pickaxe. */
    @NotNull
    public ItemStack create() {
        ItemStack pickaxe = new ItemStack(Material.DIAMOND_PICKAXE);
        ItemMeta meta = pickaxe.getItemMeta();
        if (meta != null) {
            meta.displayName(MessageUtil.color("&b&lPrison Pickaxe"));
            meta.getPersistentDataContainer().set(keys.identity(), PersistentDataType.INTEGER, 1);
            if (plugin.getConfig().getBoolean("pickaxe.unbreakable", true)) {
                meta.setUnbreakable(true);
            }
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_UNBREAKABLE);
            meta.lore(PickaxeLore.build(enchants, meta.getPersistentDataContainer()));
            pickaxe.setItemMeta(meta);
        }
        // Give a starting level of Efficiency when that enchant is enabled.
        if (enchants.maxLevel(CustomEnchant.EFFICIENCY) > 0) {
            enchants.setLevel(pickaxe, CustomEnchant.EFFICIENCY, 1);
        }
        return pickaxe;
    }

    /** Whether the player already carries a prison pickaxe. */
    public boolean hasPickaxe(@NotNull Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (enchants.isPrisonPickaxe(item)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Give the player a pickaxe when configured to and they do not already have
     * one. Called on join.
     */
    public void giveIfNeeded(@NotNull Player player) {
        if (!plugin.getConfig().getBoolean("pickaxe.give-on-first-join", true)) {
            return;
        }
        if (hasPickaxe(player)) {
            return;
        }
        player.getInventory().addItem(create());
    }
}
