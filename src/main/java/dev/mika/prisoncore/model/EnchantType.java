package dev.mika.prisoncore.model;

/**
 * How a custom enchant takes effect.
 *
 * <ul>
 *   <li>{@code PASSIVE} - a held-item effect (mining speed, movement).</li>
 *   <li>{@code GREED} - bonus tokens, money or sell value per block broken.</li>
 *   <li>{@code PROC} - a chance to break extra blocks inside the mine.</li>
 * </ul>
 */
public enum EnchantType {
    PASSIVE,
    GREED,
    PROC
}
