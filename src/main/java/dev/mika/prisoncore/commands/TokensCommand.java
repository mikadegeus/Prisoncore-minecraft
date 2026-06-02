package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
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
 * Handles {@code /tokens}: view your balance, pay another player, or (for admins)
 * grant tokens.
 */
public final class TokensCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "prisoncore.admin";

    private final PrisonCore plugin;

    public TokensCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            return showBalance(sender);
        }
        return switch (args[0].toLowerCase()) {
            case "pay" -> handlePay(sender, args);
            case "give" -> handleGive(sender, args);
            default -> showBalance(sender);
        };
    }

    private boolean showBalance(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return true;
        }
        PlayerData data = plugin.getPlayerDataManager().get(player);
        long tokens = data != null ? data.tokens() : 0L;
        player.sendMessage(MessageUtil.color(prefix() + "&eJe hebt &f"
                + NumberFormat.money(tokens) + " &etokens."));
        return true;
    }

    private boolean handlePay(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return true;
        }
        if (args.length < 3) {
            player.sendMessage(MessageUtil.color(prefix() + "&cGebruik: /tokens pay <speler> <aantal>"));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        Long amount = parsePositive(args[2]);
        if (target == null) {
            player.sendMessage(MessageUtil.color(prefix() + "&cSpeler niet gevonden of niet online."));
            return true;
        }
        if (amount == null) {
            player.sendMessage(MessageUtil.color(prefix() + "&cOngeldig aantal."));
            return true;
        }
        PlayerData from = plugin.getPlayerDataManager().get(player);
        PlayerData to = plugin.getPlayerDataManager().get(target);
        if (from == null || to == null) {
            player.sendMessage(MessageUtil.color(prefix() + "&cProfiel nog niet geladen."));
            return true;
        }
        if (from.tokens() < amount) {
            player.sendMessage(MessageUtil.color(prefix() + msg("not-enough-tokens")
                    .replace("{cost}", NumberFormat.money(amount) + " tokens")));
            return true;
        }
        from.addTokens(-amount);
        to.addTokens(amount);
        player.sendMessage(MessageUtil.color(prefix() + "&aJe gaf &f" + NumberFormat.money(amount)
                + " &atokens aan &f" + target.getName() + "&a."));
        target.sendMessage(MessageUtil.color(prefix() + "&aJe ontving &f" + NumberFormat.money(amount)
                + " &atokens van &f" + player.getName() + "&a."));
        return true;
    }

    private boolean handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtil.color(prefix() + msg("no-permission")));
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cGebruik: /tokens give <speler> <aantal>"));
            return true;
        }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        Long amount = parsePositive(args[2]);
        if (target == null) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cSpeler niet gevonden of niet online."));
            return true;
        }
        if (amount == null) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cOngeldig aantal."));
            return true;
        }
        PlayerData data = plugin.getPlayerDataManager().get(target);
        if (data == null) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cProfiel nog niet geladen."));
            return true;
        }
        data.addTokens(amount);
        sender.sendMessage(MessageUtil.color(prefix() + "&aGaf &f" + NumberFormat.money(amount)
                + " &atokens aan &f" + target.getName() + "&a."));
        return true;
    }

    @Nullable
    private Long parsePositive(@NotNull String raw) {
        try {
            long value = Long.parseLong(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
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
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("pay", "give")) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
        } else if (args.length == 2) {
            for (Player online : plugin.getServer().getOnlinePlayers()) {
                if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                    out.add(online.getName());
                }
            }
        }
        return out;
    }
}
