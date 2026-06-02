package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.model.Booster;
import dev.mika.prisoncore.model.BoosterType;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Handles {@code /booster}: list your active boosters, or (for admins) start a
 * new sell or token booster for a player or the whole server.
 */
public final class BoosterCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "prisoncore.admin";

    private final PrisonCore plugin;

    public BoosterCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            return handleGive(sender, args);
        }
        return showActive(sender);
    }

    private boolean showActive(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return true;
        }
        List<Booster> boosters = plugin.getBoosterManager().activeFor(player.getUniqueId());
        if (boosters.isEmpty()) {
            player.sendMessage(MessageUtil.color(prefix() + "&7Er zijn geen actieve boosters."));
            return true;
        }
        long now = System.currentTimeMillis();
        player.sendMessage(MessageUtil.color("&b&lActieve boosters"));
        for (Booster booster : boosters) {
            String scope = booster.isGlobal() ? "&dServer" : "&aPersoonlijk";
            player.sendMessage(MessageUtil.color("&7- " + scope + " &7"
                    + booster.type().name().toLowerCase() + " &fx" + booster.multiplier()
                    + " &7(" + formatTime(booster.secondsLeft(now)) + ")"));
        }
        return true;
    }

    private boolean handleGive(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtil.color(prefix() + msg("no-permission")));
            return true;
        }
        if (args.length < 5) {
            sender.sendMessage(MessageUtil.color(prefix()
                    + "&cGebruik: /booster give <speler|global> <sell|token> <multiplier> <minuten>"));
            return true;
        }
        UUID owner;
        if (args[1].equalsIgnoreCase("global")) {
            owner = null;
        } else {
            Player target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(MessageUtil.color(prefix() + "&cSpeler niet gevonden of niet online."));
                return true;
            }
            owner = target.getUniqueId();
        }
        BoosterType type = BoosterType.fromString(args[2]);
        Double multiplier = parsePositiveDouble(args[3]);
        Integer minutes = parsePositiveInt(args[4]);
        if (type == null) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cType moet 'sell' of 'token' zijn."));
            return true;
        }
        if (multiplier == null || minutes == null) {
            sender.sendMessage(MessageUtil.color(prefix() + "&cOngeldige multiplier of duur."));
            return true;
        }
        plugin.getBoosterManager().addBooster(owner, type, multiplier, minutes * 60L);
        String scope = owner == null ? "de server" : args[1];
        sender.sendMessage(MessageUtil.color(prefix() + "&aBooster gestart voor &f" + scope + "&a: &f"
                + type.name().toLowerCase() + " x" + multiplier + " &avoor &f" + minutes + " min."));
        if (owner == null) {
            plugin.getServer().broadcast(MessageUtil.color(prefix() + "&dEen &f" + type.name().toLowerCase()
                    + " x" + multiplier + " &dbooster is actief voor iedereen!"));
        }
        return true;
    }

    @NotNull
    private String formatTime(long seconds) {
        long minutes = seconds / 60;
        long secs = seconds % 60;
        return minutes + "m " + secs + "s";
    }

    @Nullable
    private Double parsePositiveDouble(@NotNull String raw) {
        try {
            double value = Double.parseDouble(raw);
            return value > 0 ? value : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Integer parsePositiveInt(@NotNull String raw) {
        try {
            int value = Integer.parseInt(raw);
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
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            return out;
        }
        switch (args.length) {
            case 1 -> {
                if ("give".startsWith(args[0].toLowerCase())) {
                    out.add("give");
                }
            }
            case 2 -> {
                if ("global".startsWith(args[1].toLowerCase())) {
                    out.add("global");
                }
                for (Player online : plugin.getServer().getOnlinePlayers()) {
                    if (online.getName().toLowerCase().startsWith(args[1].toLowerCase())) {
                        out.add(online.getName());
                    }
                }
            }
            case 3 -> {
                for (String type : List.of("sell", "token")) {
                    if (type.startsWith(args[2].toLowerCase())) {
                        out.add(type);
                    }
                }
            }
            default -> {
                // No suggestions for multiplier/minutes.
            }
        }
        return out;
    }
}
