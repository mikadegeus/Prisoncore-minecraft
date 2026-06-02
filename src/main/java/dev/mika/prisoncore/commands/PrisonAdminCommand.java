package dev.mika.prisoncore.commands;

import dev.mika.prisoncore.PrisonCore;
import dev.mika.prisoncore.gui.MineAdminGUI;
import dev.mika.prisoncore.model.Mine;
import dev.mika.prisoncore.model.MinePalette;
import dev.mika.prisoncore.model.PaletteEntry;
import dev.mika.prisoncore.util.Cuboid;
import dev.mika.prisoncore.util.MessageUtil;
import org.bukkit.Location;
import org.bukkit.Material;
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
 * Admin command {@code /pa}: hands out the selection wand, creates and edits mines
 * from a selection, manages palettes and reset rules, and reloads configuration.
 */
public final class PrisonAdminCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "prisoncore.admin";
    private static final List<String> SUBCOMMANDS = List.of(
            "wand", "gui", "create", "delete", "settp", "setrank",
            "setreset", "addblock", "clearpalette", "reset", "reload");

    private static final double DEFAULT_PALETTE_WEIGHT = 100.0;
    private static final int DEFAULT_RESET_SECONDS = 300;
    private static final int DEFAULT_RESET_PERCENTAGE = 25;

    private final PrisonCore plugin;

    public PrisonAdminCommand(@NotNull PrisonCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(ADMIN_PERMISSION)) {
            sender.sendMessage(MessageUtil.color(prefix() + msg("no-permission")));
            return true;
        }
        if (args.length == 0) {
            sendUsage(sender);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "wand" -> giveWand(sender);
            case "gui" -> openGui(sender);
            case "reload" -> reload(sender);
            case "create" -> createMine(sender, args);
            case "delete" -> deleteMine(sender, args);
            case "settp" -> setTeleport(sender, args);
            case "setrank" -> setRank(sender, args);
            case "setreset" -> setReset(sender, args);
            case "addblock" -> addBlock(sender, args);
            case "clearpalette" -> clearPalette(sender, args);
            case "reset" -> resetMine(sender, args);
            default -> sendUsage(sender);
        }
        return true;
    }

    private void giveWand(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return;
        }
        player.getInventory().addItem(plugin.getSelectionManager().createWand());
        send(player, "&aJe hebt de Mine Wand gekregen.");
    }

    private void openGui(@NotNull CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return;
        }
        player.openInventory(new MineAdminGUI(plugin).getInventory());
    }

    private void reload(@NotNull CommandSender sender) {
        plugin.reloadConfig();
        plugin.getRankManager().load();
        plugin.getSellManager().load();
        plugin.getEnchantManager().load();
        send(sender, msg("reloaded"));
    }

    private void createMine(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return;
        }
        if (args.length < 2) {
            send(player, "&cGebruik: /pa create <naam>");
            return;
        }
        String name = args[1];
        if (plugin.getMineManager().getMine(name) != null) {
            send(player, "&cEr bestaat al een mine met die naam.");
            return;
        }
        Cuboid selection = plugin.getSelectionManager().selectionOf(player.getUniqueId());
        if (selection == null) {
            send(player, "&cSelecteer eerst beide hoeken met de Mine Wand (/pa wand).");
            return;
        }
        MinePalette palette = new MinePalette(List.of(new PaletteEntry(Material.STONE, DEFAULT_PALETTE_WEIGHT)));
        Mine mine = new Mine(name, selection, palette, 0,
                DEFAULT_RESET_SECONDS, DEFAULT_RESET_PERCENTAGE, false, 0, 0, 0, 0, 0);
        plugin.getMineManager().put(mine);
        plugin.getSelectionManager().clear(player.getUniqueId());
        send(player, "&aMine &f" + name + " &aaangemaakt met een STONE-palette. "
                + "Gebruik /pa addblock en /pa settp om hem af te maken.");
    }

    private void deleteMine(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            send(sender, "&cGebruik: /pa delete <naam>");
            return;
        }
        if (plugin.getMineManager().remove(args[1])) {
            send(sender, "&aMine &f" + args[1] + " &averwijderd.");
        } else {
            send(sender, "&cOnbekende mine: &f" + args[1]);
        }
    }

    private void setTeleport(@NotNull CommandSender sender, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(MessageUtil.color(msg("player-only")));
            return;
        }
        Mine mine = requireMine(player, args);
        if (mine == null) {
            return;
        }
        Location loc = player.getLocation();
        plugin.getMineManager().put(mine.withTeleport(loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch()));
        send(player, "&aTeleport van mine &f" + mine.name() + " &agezet op je locatie.");
    }

    private void setRank(@NotNull CommandSender sender, @NotNull String[] args) {
        Mine mine = requireMine(sender, args);
        if (mine == null) {
            return;
        }
        if (args.length < 3) {
            send(sender, "&cGebruik: /pa setrank <naam> <rank-index>");
            return;
        }
        Integer rank = parseInt(args[2]);
        if (rank == null || rank < 0) {
            send(sender, "&cOngeldige rank-index.");
            return;
        }
        plugin.getMineManager().put(mine.withRequiredRank(rank));
        send(sender, "&aVereiste rank van &f" + mine.name() + " &agezet op &f"
                + plugin.getRankManager().displayName(rank) + "&a.");
    }

    private void setReset(@NotNull CommandSender sender, @NotNull String[] args) {
        Mine mine = requireMine(sender, args);
        if (mine == null) {
            return;
        }
        if (args.length < 3) {
            send(sender, "&cGebruik: /pa setreset <naam> <seconden> [percentage]");
            return;
        }
        Integer seconds = parseInt(args[2]);
        if (seconds == null || seconds < 1) {
            send(sender, "&cOngeldig aantal seconden.");
            return;
        }
        int percentage = mine.resetPercentage();
        if (args.length >= 4) {
            Integer parsed = parseInt(args[3]);
            if (parsed != null) {
                percentage = parsed;
            }
        }
        plugin.getMineManager().put(mine.withReset(seconds, percentage));
        send(sender, "&aReset van &f" + mine.name() + " &agezet op &f" + seconds + "s&a.");
    }

    private void addBlock(@NotNull CommandSender sender, @NotNull String[] args) {
        Mine mine = requireMine(sender, args);
        if (mine == null) {
            return;
        }
        if (args.length < 4) {
            send(sender, "&cGebruik: /pa addblock <naam> <materiaal> <gewicht>");
            return;
        }
        Material material = Material.matchMaterial(args[2]);
        if (material == null || !material.isBlock()) {
            send(sender, "&cOnbekend of niet-plaatsbaar materiaal: &f" + args[2]);
            return;
        }
        Double weight = parseDouble(args[3]);
        if (weight == null || weight <= 0) {
            send(sender, "&cOngeldig gewicht.");
            return;
        }
        List<PaletteEntry> entries = new ArrayList<>(mine.palette().entries());
        entries.add(new PaletteEntry(material, weight));
        plugin.getMineManager().put(mine.withPalette(new MinePalette(entries)));
        send(sender, "&aToegevoegd aan &f" + mine.name() + "&a: &f" + material + " &7(gewicht " + weight + ")");
    }

    private void clearPalette(@NotNull CommandSender sender, @NotNull String[] args) {
        Mine mine = requireMine(sender, args);
        if (mine == null) {
            return;
        }
        plugin.getMineManager().put(mine.withPalette(new MinePalette(List.of())));
        send(sender, "&aPalette van &f" + mine.name() + " &ageleegd.");
    }

    private void resetMine(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            send(sender, "&cGebruik: /pa reset <naam>");
            return;
        }
        if (plugin.getMineManager().forceReset(args[1])) {
            send(sender, "&aMine &f" + args[1] + " &agereset.");
        } else {
            send(sender, "&cOnbekende mine: &f" + args[1]);
        }
    }

    @Nullable
    private Mine requireMine(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length < 2) {
            send(sender, "&cGeef een mine-naam op.");
            return null;
        }
        Mine mine = plugin.getMineManager().getMine(args[1]);
        if (mine == null) {
            send(sender, "&cOnbekende mine: &f" + args[1]);
        }
        return mine;
    }

    private void sendUsage(@NotNull CommandSender sender) {
        send(sender, "&7/pa &f" + String.join(", ", SUBCOMMANDS));
    }

    @Nullable
    private Integer parseInt(@NotNull String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Nullable
    private Double parseDouble(@NotNull String raw) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void send(@NotNull CommandSender sender, @NotNull String body) {
        sender.sendMessage(MessageUtil.color(prefix() + body));
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
        if (args.length == 1) {
            for (String sub : SUBCOMMANDS) {
                if (sub.startsWith(args[0].toLowerCase())) {
                    out.add(sub);
                }
            }
            return out;
        }
        if (args.length == 2 && needsMineName(args[0])) {
            String partial = args[1].toLowerCase();
            plugin.getMineManager().getAllMines().forEach(mine -> {
                if (mine.name().toLowerCase().startsWith(partial)) {
                    out.add(mine.name());
                }
            });
        }
        return out;
    }

    private boolean needsMineName(@NotNull String sub) {
        return switch (sub.toLowerCase()) {
            case "delete", "settp", "setrank", "setreset", "addblock", "clearpalette", "reset" -> true;
            default -> false;
        };
    }
}
