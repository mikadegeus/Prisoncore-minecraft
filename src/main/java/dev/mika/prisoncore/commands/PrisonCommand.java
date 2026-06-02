package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.PrisonHubGUI;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Handles {@code /prison}: opens the central hub menu, with an admin
 * {@code reload} sub-command.
 */
public final class PrisonCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "prisoncore.admin";

    private final PrisonCore plugin;

    public PrisonCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            return handleReload(sender);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(message("player-only")));
            return true;
        }
        player.openInventory(new PrisonHubGUI(plugin).getInventory());
        return true;
    }

    private boolean handleReload(@NotNull CommandSender sender) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtil.color(prefix() + message("no-permission")));
            return true;
        }
        plugin.reloadConfig();
        plugin.getRankManager().load();
        plugin.getSellManager().load();
        plugin.getEnchantManager().load();
        sender.sendMessage(MessageUtil.color(prefix() + message("reloaded")));
        return true;
    }

    @NotNull
    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    @NotNull
    private String message(@NotNull String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission(ADMIN_PERMISSION)
                && "reload".startsWith(args[0].toLowerCase())) {
            return List.of("reload");
        }
        return Collections.emptyList();
    }
}
