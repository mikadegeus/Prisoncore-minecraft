package dev.mika.prisoncore.managers;

import dev.mika.prisoncore.util.Cuboid;
import dev.mika.prisoncore.util.ItemBuilder;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds each admin's two-corner mine selection and owns the selection wand. The
 * wand is a tagged golden axe; left/right clicking a block sets corner one/two.
 */
public final class SelectionManager {

    private final NamespacedKey wandKey;
    private final Map<UUID, Location[]> selections = new ConcurrentHashMap<>();

    public SelectionManager(@NotNull Plugin plugin) {
        this.wandKey = new NamespacedKey(plugin, "wand");
    }

    /** Build a selection wand item. */
    @NotNull
    public ItemStack createWand() {
        ItemStack wand = new ItemBuilder(Material.GOLDEN_AXE)
                .name("&b&lMine Wand")
                .lore(List.of(
                        "&7Links-klik: &fhoek 1",
                        "&7Rechts-klik: &fhoek 2",
                        "&7Daarna: &f/pa create <naam>"))
                .glow()
                .build();
        ItemMeta meta = wand.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(wandKey, PersistentDataType.INTEGER, 1);
            wand.setItemMeta(meta);
        }
        return wand;
    }

    public boolean isWand(@Nullable ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer()
                .getOrDefault(wandKey, PersistentDataType.INTEGER, 0) == 1;
    }

    /** Set corner 0 or 1 for a player. */
    public void setCorner(@NotNull UUID player, int index, @NotNull Location location) {
        Location[] corners = selections.computeIfAbsent(player, key -> new Location[2]);
        corners[index] = location.clone();
    }

    /**
     * Build a cuboid from a player's selection.
     *
     * @return the selection, or {@code null} when both corners are not yet set in
     * the same world.
     */
    @Nullable
    public Cuboid selectionOf(@NotNull UUID player) {
        Location[] corners = selections.get(player);
        if (corners == null || corners[0] == null || corners[1] == null) {
            return null;
        }
        Location a = corners[0];
        Location b = corners[1];
        if (a.getWorld() == null || b.getWorld() == null
                || !a.getWorld().getName().equals(b.getWorld().getName())) {
            return null;
        }
        return new Cuboid(a.getWorld().getName(),
                a.getBlockX(), a.getBlockY(), a.getBlockZ(),
                b.getBlockX(), b.getBlockY(), b.getBlockZ());
    }

    public void clear(@NotNull UUID player) {
        selections.remove(player);
    }
}
