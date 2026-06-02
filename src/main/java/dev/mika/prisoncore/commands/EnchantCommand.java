package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.EnchantGUI;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Handles {@code /enchant}: opens the enchant menu for the pickaxe the player is
 * holding.
 */
public final class EnchantCommand implements CommandExecutor {

    private final PrisonCore plugin;

    public EnchantCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(
                    plugin.getConfig().getString("messages.player-only", "")));
            return true;
        }
        player.openInventory(new EnchantGUI(plugin, player).getInventory());
        return true;
    }
}
