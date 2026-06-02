package dev.mika.prisoncore.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Contract for every PrisonCore inventory. Implemented as an
 * {@link InventoryHolder} so a clicked inventory can be identified reliably (no
 * fragile title matching) and asked to handle its own interactions.
 */
public interface Menu extends InventoryHolder {

    /**
     * Handle a click inside this menu. Implementations are responsible for
     * cancelling the event where appropriate.
     */
    void handleClick(@NotNull InventoryClickEvent event);

    /**
     * Handle the menu being closed. Default is a no-op; menus that hold player
     * items override this to return them.
     */
    default void handleClose(@NotNull InventoryCloseEvent event) {
    }

    /**
     * @return {@code true} to permit an item drag inside this menu. Defaults to
     * {@code false}, which causes the listener to cancel the drag.
     */
    default boolean allowDrag(@NotNull InventoryDragEvent event) {
        return false;
    }
}
