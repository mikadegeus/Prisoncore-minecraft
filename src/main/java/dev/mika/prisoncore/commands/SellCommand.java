package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.SellGUI;
import dev.mika.prisoncore.managers.SellManager;
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

import java.util.ArrayList;
import java.util.List;

/**
 * Handles {@code /sell} and {@code /sellall}. Bare {@code /sell} opens the
 * {@link SellGUI}; {@code hand}/{@code all} sell directly; {@code auto} toggles
 * autosell. {@code /sellall} is a direct alias for {@code /sell all}.
 */
public final class SellCommand implements CommandExecutor, TabCompleter {

    private static final String AUTOSELL_PERMISSION = "prisoncore.autosell";
    private static final List<String> SUBCOMMANDS = List.of("hand", "all", "auto");

    private final PrisonCore plugin;

    public SellCommand(@NotNull PrisonCore plugin) {
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

        // /sellall, or /sell all
        boolean directAll = command.getName().equalsIgnoreCase("sellall")
                || (args.length >= 1 && args[0].equalsIgnoreCase("all"));
        if (directAll) {
            announce(player, plugin.getSellManager().sellAll(player, data));
            return true;
        }
        if (args.length == 0) {
            player.openInventory(new SellGUI(plugin, player).getInventory());
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "hand" -> announce(player, plugin.getSellManager().sellHand(player, data));
            case "auto" -> toggleAutosell(player, data);
            default -> player.sendMessage(MessageUtil.color(prefix() + "&cGebruik: /sell [hand|all|auto]"));
        }
        return true;
    }

    private void announce(@NotNull Player player, @NotNull SellManager.SaleResult result) {
        if (result.sold()) {
            player.sendMessage(MessageUtil.color(prefix()
                    + MessageUtil.replace(msg("sold"), "amount", currency() + NumberFormat.money(result.earned()))));
        } else {
            player.sendMessage(MessageUtil.color(prefix() + msg("nothing-to-sell")));
        }
    }

    private void toggleAutosell(@NotNull Player player, @NotNull PlayerData data) {
        if (!player.hasPermission(AUTOSELL_PERMISSION)) {
            player.sendMessage(MessageUtil.color(prefix() + msg("no-permission")));
            return;
        }
        boolean enabled = !data.autosell();
        data.setAutosell(enabled);
        player.sendMessage(MessageUtil.color(prefix() + msg(enabled ? "autosell-on" : "autosell-off")));
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
        if (command.getName().equalsIgnoreCase("sell") && args.length == 1) {
            List<String> matches = new ArrayList<>();
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    matches.add(sub);
                }
            }
            return matches;
        }
        return List.of();
    }
}
