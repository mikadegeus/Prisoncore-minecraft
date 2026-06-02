package dev.mika.prisoncore.model;

import org.bukkit.Material;
import org.jetbrains.annotations.NotNull;

/**
 * One weighted entry in a mine's block palette.
 *
 * @param material the block to place
 * @param weight   its relative share of the palette (higher = more common)
 */
public record PaletteEntry(@NotNull Material material, double weight) {
}
