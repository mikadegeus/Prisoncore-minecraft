package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.MineListGUI;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@code /mine}: opens the mine list, teleports directly to a named mine,
 * or (for admins) forces a reset.
 */
public final class MineCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "prisoncore.admin";

    private final PrisonCore plugin;

    public MineCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reset")) {
            return handleReset(sender, args);
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(message("player-only")));
            return true;
        }
        if (args.length == 0) {
            player.openInventory(new MineListGUI(plugin, player).getInventory());
            return true;
        }
        teleportTo(player, args[0]);
        return true;
    }

    private boolean handleReset(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtil.color(prefix() + message("no-permission")));
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cGebruik: /mine reset <naam>"));
            return true;
        }
        if (plugin.getMineManager().forceReset(args[1])) {
            sender.sendMessage(MessageUtil.color(prefix() + "&aMine &f" + args[1] + " &agereset."));
        } else {
            sender.sendMessage(MessageUtil.color(prefix() + "&cOnbekende mine: &f" + args[1]));
        }
        return true;
    }

    private void teleportTo(@NotNull Player player, @NotNull String name) {
        Mine mine = plugin.getMineManager().getMine(name);
        if (mine == null) {
            player.sendMessage(MessageUtil.color(prefix() + "&cOnbekende mine: &f" + name));
            return;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        int rank = data != null ? data.rankIndex() : 0;
        if (rank < mine.requiredRank()) {
            String raw = MessageUtil.replace(message("mine-locked"), "rank",
                    plugin.getRankManager().displayName(mine.requiredRank()));
            player.sendMessage(MessageUtil.color(prefix() + raw));
            return;
        }
        Location destination = mine.safeDestination();
        if (destination == null) {
            player.sendMessage(MessageUtil.color(prefix() + "&cDeze mine is niet beschikbaar (wereld niet geladen)."));
            return;
        }
        player.teleport(destination);
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
        List<String> suggestions = new ArrayList<>();
        if (args.length == 1) {
            String partial = args[0].toLowerCase();
            if (sender.hasPermission(ADMIN_PERMISSION) && "reset".startsWith(partial)) {
                suggestions.add("reset");
            }
            for (Mine mine : plugin.getMineManager().getAllMines()) {
                if (mine.name().toLowerCase().startsWith(partial)) {
                    suggestions.add(mine.name());
                }
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("reset")
                && sender.hasPermission(ADMIN_PERMISSION)) {
            String partial = args[1].toLowerCase();
            for (Mine mine : plugin.getMineManager().getAllMines()) {
                if (mine.name().toLowerCase().startsWith(partial)) {
                    suggestions.add(mine.name());
                }
            }
        }
        return suggestions;
    }
}
