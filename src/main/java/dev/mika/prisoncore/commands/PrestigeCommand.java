package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.PrestigeGUI;
import dev.mika.prisoncore.managers.PrestigeManager;
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
 * Handles {@code /prestige}. Bare {@code /prestige} opens the {@link PrestigeGUI};
 * {@code /prestige max} climbs and prestiges repeatedly while affordable.
 */
public final class PrestigeCommand implements CommandExecutor, TabCompleter {

    private final PrisonCore plugin;

    public PrestigeCommand(@NotNull PrisonCore plugin) {
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
        if (args.length >= 1 && args[0].equalsIgnoreCase("max")) {
            doPrestigeMax(player, data);
        } else {
            player.openInventory(new PrestigeGUI(plugin, player).getInventory());
        }
        return true;
    }

    private void doPrestigeMax(@NotNull Player player, @NotNull PlayerData data) {
        PrestigeManager prestige = plugin.getPrestigeManager();
        int gained = prestige.prestigeMax(player, data);
        if (gained > 0) {
            String body = MessageUtil.replace(msg("prestige-success"), "prestige", String.valueOf(data.prestige()));
            body = MessageUtil.replace(body, "multiplier",
                    NumberFormat.compact(prestige.sellMultiplier(data.prestige())));
            player.sendMessage(MessageUtil.color(prefix() + body));
        } else {
            player.sendMessage(MessageUtil.color(prefix() + msg("prestige-need-max-rank")));
        }
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
        if (args.length == 1 && "max".startsWith(args[0].toLowerCase())) {
            return List.of("max");
        }
        return Collections.emptyList();
    }
}
