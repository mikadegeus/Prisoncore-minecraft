package dev.mika.prisoncore.util;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Fluent builder for constructing {@link ItemStack}s with display names, lore,
 * amounts and a subtle "glow" effect, without repeating boilerplate.
 */
public final class ItemBuilder {

    private final ItemStack itemStack;
    private final ItemMeta meta;

    public ItemBuilder(@NotNull Material material) {
        this.itemStack = new ItemStack(material);
        this.meta = itemStack.getItemMeta();
    }

    public ItemBuilder(@NotNull ItemStack source) {
        this.itemStack = source.clone();
        this.meta = itemStack.getItemMeta();
    }

    /**
     * Set the display name from a legacy {@code &}-coded string.
     */
    @NotNull
    public ItemBuilder name(@NotNull String legacy) {
        if (meta != null) {
            meta.displayName(MessageUtil.color(legacy));
        }
        return this;
    }

    /**
     * Set the display name from a pre-built component.
     */
    @NotNull
    public ItemBuilder name(@NotNull Component component) {
        if (meta != null) {
            meta.displayName(component);
        }
        return this;
    }

    /**
     * Set the lore from a list of legacy {@code &}-coded strings.
     */
    @NotNull
    public ItemBuilder lore(@NotNull List<String> legacyLines) {
        if (meta != null) {
            meta.lore(MessageUtil.colorList(legacyLines));
        }
        return this;
    }

    /**
     * Set the lore from a list of pre-built components.
     */
    @NotNull
    public ItemBuilder loreComponents(@NotNull List<Component> lines) {
        if (meta != null) {
            meta.lore(new ArrayList<>(lines));
        }
        return this;
    }

    @NotNull
    public ItemBuilder amount(int amount) {
        itemStack.setAmount(Math.max(1, Math.min(amount, itemStack.getMaxStackSize())));
        return this;
    }

    /**
     * Add an enchantment glow without showing the enchantment text.
     */
    @NotNull
    public ItemBuilder glow() {
        if (meta != null) {
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        }
        return this;
    }

    /**
     * Hide attribute tooltips (damage, attack speed) for cleaner display items.
     */
    @NotNull
    public ItemBuilder hideAttributes() {
        if (meta != null) {
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        }
        return this;
    }

    @NotNull
    public ItemStack build() {
        if (meta != null) {
            itemStack.setItemMeta(meta);
        }
        return itemStack;
    }
}
