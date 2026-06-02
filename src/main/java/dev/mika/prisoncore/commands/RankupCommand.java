package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.RankupGUI;
import dev.mika.prisoncore.managers.RankManager;
import dev.mika.prisoncore.model.PlayerData;
import dev.mika.prisoncore.util.MessageUtil;
import dev.mika.prisoncore.util.NumberFormat;
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
 * Handles {@code /rankup} and {@code /rankupmax}. Bare {@code /rankup} opens the
 * {@link RankupGUI}; {@code /rankup max} or {@code /rankupmax} ranks up as far as
 * the player can afford in one go.
 */
public final class RankupCommand implements CommandExecutor, TabCompleter {

    private final PrisonCore plugin;

    public RankupCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return true;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        if (data == null) {
            player.sendMessage(MessageUtil.color(prefix() + "&cJe profiel laadt nog, probeer het zo opnieuw."));
            return true;
        }

        boolean max = command.getName().equalsIgnoreCase("rankupmax")
                || (args.length >= 1 && args[0].equalsIgnoreCase("max"));
        if (max) {
            doRankupMax(player, data);
        } else {
            player.openInventory(new RankupGUI(plugin, player).getInventory());
        }
        return true;
    }

    private void doRankupMax(@NotNull Player player, @NotNull PlayerData data) {
        RankManager ranks = plugin.getRankManager();
        double nextCost = ranks.nextCost(data.rankIndex());
        int gained = ranks.attemptRankupMax(player, data);
        if (gained > 0) {
            player.sendMessage(MessageUtil.color(prefix() + "&aGerankt naar &f"
                    + ranks.displayName(data.rankIndex()) + " &a(+" + gained + " ranks)."));
        } else if (ranks.isMaxRank(data.rankIndex())) {
            player.sendMessage(MessageUtil.color(prefix() + msg("rankup-max-rank")));
        } else {
            String raw = MessageUtil.replace(msg("not-enough-money"), "cost",
                    currency() + NumberFormat.money(nextCost));
            player.sendMessage(MessageUtil.color(prefix() + raw));
        }
    }

    @NotNull
    private String currency() {
        return plugin.getConfig().getString("economy.currency-symbol", "$");
    }

    @NotNull
    private String prefix() {
        return plugin.getConfig().getString("messages.prefix", "");
    }

    @NotNull
    private String msg(@NotNull String key) {
        return plugin.getConfig().getString("messages." + key, "");
    }

    @Override
    @Nullable
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (command.getName().equalsIgnoreCase("rankup") && args.length == 1
                && "max".startsWith(args[0].toLowerCase())) {
            return List.of("max");
        }
        return Collections.emptyList();
    }
}
