package dev.mika.prisoncore.gui;

import org.bukkit.Material;

/**
 * Shared GUI layout constants and the content-slot grid used by the paginated
 * menus, kept in one place to avoid magic numbers scattered across the GUIs.
 */
public final class GuiLayout {

    private GuiLayout() {
    }

    /** A six-row chest inventory. */
    public static final int ROWS = 6;
    public static final int SIZE = ROWS * 9;

    /** A three-row chest inventory (hub, confirmation screens). */
    public static final int SMALL_SIZE = 27;

    /** Inner slots (everything not on the glass border) used for content. */
    public static final int[] CONTENT_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    /** Navigation slots on the bottom row. */
    public static final int PREV_SLOT = 48;
    public static final int CLOSE_SLOT = 49;
    public static final int NEXT_SLOT = 50;
    public static final int BACK_SLOT = 45;
    /** Slot for a filter / toggle control on the top row. */
    public static final int FILTER_SLOT = 4;

    public static final Material BORDER_MATERIAL = Material.GRAY_STAINED_GLASS_PANE;
    public static final Material FILLER_MATERIAL = Material.BLACK_STAINED_GLASS_PANE;

    /** @return how many content items fit on a single page. */
    public static int pageCapacity() {
        return CONTENT_SLOTS.length;
    }
}
