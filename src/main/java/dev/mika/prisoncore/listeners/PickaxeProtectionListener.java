package dev.mika.prisoncore.listeners;

import dev.mika.prisoncore.PrisonCore;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the prison pickaxe with its owner: it cannot be dropped, and on death it
 * is pulled out of the drops and handed back on respawn (unless the server keeps
 * inventories, in which case the pickaxe is already retained).
 */
public final class PickaxeProtectionListener implements Listener {

    private final PrisonCore plugin;
    private final Map<UUID, List<ItemStack>> saved = new ConcurrentHashMap<>();

    public PickaxeProtectionListener(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onDrop(@NotNull PlayerDropItemEvent event) {
        if (plugin.getEnchantManager().isPrisonPickaxe(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(@NotNull PlayerDeathEvent event) {
        if (event.getKeepInventory()) {
            return;
        }
        List<ItemStack> pickaxes = new ArrayList<>();
        Iterator<ItemStack> iterator = event.getDrops().iterator();
        while (iterator.hasNext()) {
            ItemStack drop = iterator.next();
            if (plugin.getEnchantManager().isPrisonPickaxe(drop)) {
                pickaxes.add(drop);
                iterator.remove();
            }
        }
        if (!pickaxes.isEmpty()) {
            saved.put(event.getEntity().getUniqueId(), pickaxes);
        }
    }

    @EventHandler
    public void onRespawn(@NotNull PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        List<ItemStack> pickaxes = saved.remove(player.getUniqueId());
        if (pickaxes != null) {
            pickaxes.forEach(pickaxe -> player.getInventory().addItem(pickaxe));
        }
    }
}
