package dev.mika.prisoncore.listeners;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.jetbrains.annotations.NotNull;

/**
 * Turns left/right clicks with the mine wand into corner-one/corner-two
 * selections. Only admins can use the wand, and the interaction is cancelled so
 * the axe never breaks or places anything.
 */
public final class WandListener implements Listener {

    private static final String ADMIN_PERMISSION = "prisoncore.admin";

    private final PrisonCore plugin;

    public WandListener(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(@NotNull PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return; // Ignore the off-hand pass of the event.
        }
        if (!plugin.getSelectionManager().isWand(event.getItem())) {
            return;
        }
        Block block = event.getClickedBlock();
        if (block == null) {
            return;
        }
        event.setCancelled(true);
        if (!event.getPlayer().hasPermission(ADMIN_PERMISSION)) {
            return;
        }

        Action action = event.getAction();
        if (action == Action.LEFT_CLICK_BLOCK) {
            plugin.getSelectionManager().setCorner(event.getPlayer().getUniqueId(), 0, block.getLocation());
            message(event, "&aHoek 1 gezet: &f", block);
        } else if (action == Action.RIGHT_CLICK_BLOCK) {
            plugin.getSelectionManager().setCorner(event.getPlayer().getUniqueId(), 1, block.getLocation());
            message(event, "&aHoek 2 gezet: &f", block);
        }
    }

    private void message(@NotNull PlayerInteractEvent event, @NotNull String prefix, @NotNull Block block) {
        String pluginPrefix = plugin.getConfig().getString("messages.prefix", "");
        event.getPlayer().sendMessage(MessageUtil.color(pluginPrefix + prefix
                + block.getX() + ", " + block.getY() + ", " + block.getZ()));
    }
}
