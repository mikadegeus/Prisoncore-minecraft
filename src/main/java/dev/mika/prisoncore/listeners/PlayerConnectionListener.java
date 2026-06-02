package dev.mika.prisoncore.listeners;

import dev.mika.prisoncore.PrisonCore;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Loads a player's {@link dev.mika.prisoncore.model.PlayerData} on join and
 * flushes it back to the database on quit. Later milestones extend join handling
 * to hand out the prison pickaxe.
 */
public final class PlayerConnectionListener implements Listener {

    private final PrisonCore plugin;

    public PlayerConnectionListener(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(@NotNull PlayerJoinEvent event) {
        plugin.getPlayerDataManager().load(event.getPlayer());
        plugin.getPickaxeFactory().giveIfNeeded(event.getPlayer());
    }

    @EventHandler
    public void onQuit(@NotNull PlayerQuitEvent event) {
        plugin.getPlayerDataManager().unload(event.getPlayer().getUniqueId());
    }
}
