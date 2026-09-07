package xyz.haxxor.mctransfer.paper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.TreeSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xyz.haxxor.mctransfer.common.Destination;
import xyz.haxxor.mctransfer.common.Portal;
import xyz.haxxor.mctransfer.common.PortalKey;

/**
 * The {@code /transfer} command.
 *
 * <p>Managing portals needs {@code mctransfer.manage} (op by default), while triggering a transfer
 * needs only {@code mctransfer.send}, which everyone has. That split is what lets a button wired to
 * a command block work for any player without also letting them build new portals.
 */
final class TransferCommand implements CommandExecutor, TabCompleter {

    private static final String MANAGE = "mctransfer.manage";
    private static final String SEND = "mctransfer.send";

    private final McTransferPaper plugin;

    TransferCommand(McTransferPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String @NotNull [] args) {
        if (args.length == 0) {
            return false;
        }
        try {
            return switch (args[0].toLowerCase(Locale.ROOT)) {
                case "register" -> register(sender, args);
                case "unregister" -> unregister(sender);
                case "list" -> list(sender);
                case "listservers" -> listServers(sender);
                case "send" -> send(sender, args);
                default -> false;
            };
        } catch (IllegalArgumentException e) {
            error(sender, e.getMessage());
            return true;
        }
    }

    private boolean register(CommandSender sender, String[] args) {
        if (!allowed(sender, MANAGE)) {
            return true;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (args.length < 2) {
            error(sender, "Usage: /transfer register <server> [x y z [yaw pitch]]");
            return true;
        }
        String target = args[1];
        Optional<Destination> destination = parseDestination(args, 2);

        PortalKey here = PortalKeys.at(player.getLocation());
        plugin.portals().put(new Portal(here, target, destination));
        if (!plugin.savePortals()) {
            error(sender, "Portal registered, but saving it failed - check the server log.");
            return true;
        }
        sender.sendMessage(Component.text("Portal here now sends to ", NamedTextColor.GREEN)
                .append(Component.text(target, NamedTextColor.WHITE))
                .append(Component.text(describe(destination), NamedTextColor.GRAY)));
        return true;
    }

    private boolean unregister(CommandSender sender) {
        if (!allowed(sender, MANAGE)) {
            return true;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        if (!plugin.portals().remove(PortalKeys.at(player.getLocation()))) {
            error(sender, "There is no portal here.");
            return true;
        }
        if (!plugin.savePortals()) {
            error(sender, "Portal removed, but saving that failed - check the server log.");
            return true;
        }
        sender.sendMessage(Component.text("Portal removed.", NamedTextColor.GREEN));
        return true;
    }

    private boolean list(CommandSender sender) {
        if (!allowed(sender, MANAGE)) {
            return true;
        }
        if (plugin.portals().all().isEmpty()) {
            sender.sendMessage(Component.text("No portals registered here.", NamedTextColor.GRAY));
            return true;
        }
        sender.sendMessage(Component.text("Portals on this server:", NamedTextColor.GRAY));
        for (Portal portal : plugin.portals().all()) {
            PortalKey at = portal.location();
            sender.sendMessage(Component.text("  %d %d %d".formatted(at.x(), at.y(), at.z()), NamedTextColor.WHITE)
                    .append(Component.text(" -> ", NamedTextColor.GRAY))
                    .append(Component.text(portal.targetServer(), NamedTextColor.AQUA))
                    .append(Component.text(describe(portal.destination()), NamedTextColor.GRAY)));
        }
        return true;
    }

    private boolean listServers(CommandSender sender) {
        if (!allowed(sender, MANAGE)) {
            return true;
        }
        Player player = asPlayer(sender);
        if (player == null) {
            return true;
        }
        // Only the proxy knows what servers exist, so the reply arrives asynchronously.
        plugin.requestServerList(player);
        return true;
    }

    private boolean send(CommandSender sender, String[] args) {
        if (!allowed(sender, SEND)) {
            return true;
        }
        if (args.length < 3) {
            error(sender, "Usage: /transfer send <target> <server> [x y z [yaw pitch]]");
            return true;
        }
        List<Player> targets = resolveTargets(sender, args[1]);
        if (targets.isEmpty()) {
            error(sender, "No matching players.");
            return true;
        }
        String server = args[2];
        Optional<Destination> destination = parseDestination(args, 3);
        for (Player target : targets) {
            plugin.requestTransfer(target, server, destination);
        }
        return true;
    }

    private List<Player> resolveTargets(CommandSender sender, String selector) {
        if (!selector.startsWith("@")) {
            Player named = Bukkit.getPlayerExact(selector);
            return named == null ? List.of() : List.of(named);
        }
        try {
            return Bukkit.selectEntities(sender, selector).stream()
                    .filter(Player.class::isInstance)
                    .map(Player.class::cast)
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Not a valid target selector: " + selector);
        }
    }

    /**
     * Reads an optional landing spot from trailing arguments.
     *
     * <p>Nothing means "use the destination server's world spawn"; three arguments set a position;
     * five also set the direction the player faces on arrival.
     */
    private static Optional<Destination> parseDestination(String[] args, int from) {
        int count = args.length - from;
        return switch (count) {
            case 0 -> Optional.empty();
            case 3 -> Optional.of(Destination.of(
                    number(args[from], "x"), number(args[from + 1], "y"), number(args[from + 2], "z")));
            case 5 -> Optional.of(new Destination(
                    number(args[from], "x"),
                    number(args[from + 1], "y"),
                    number(args[from + 2], "z"),
                    (float) number(args[from + 3], "yaw"),
                    (float) number(args[from + 4], "pitch")));
            default -> throw new IllegalArgumentException(
                    "Give either no coordinates, three (x y z), or five (x y z yaw pitch).");
        };
    }

    private static double number(String raw, String name) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'%s' is not a valid %s.".formatted(raw, name));
        }
    }

    private static String describe(Optional<Destination> destination) {
        return destination
                .map(spot -> " at %.1f %.1f %.1f".formatted(spot.x(), spot.y(), spot.z()))
                .orElse(" at its world spawn");
    }

    private static Player asPlayer(CommandSender sender) {
        if (sender instanceof Player player) {
            return player;
        }
        error(sender, "That has to be run by a player standing where you want the portal.");
        return null;
    }

    private static boolean allowed(CommandSender sender, String permission) {
        if (sender.hasPermission(permission)) {
            return true;
        }
        error(sender, "You are not allowed to do that.");
        return false;
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(Component.text(message, NamedTextColor.RED));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
            @NotNull String label, String @NotNull [] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>();
            if (sender.hasPermission(MANAGE)) {
                options.addAll(List.of("register", "unregister", "list", "listservers"));
            }
            if (sender.hasPermission(SEND)) {
                options.add("send");
            }
            return matching(options, args[0]);
        }
        String subcommand = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && subcommand.equals("register")) {
            return matching(knownServers(), args[1]);
        }
        if (args.length == 2 && subcommand.equals("send")) {
            List<String> options = new ArrayList<>(List.of("@p", "@s", "@a", "@r"));
            Bukkit.getOnlinePlayers().forEach(player -> options.add(player.getName()));
            return matching(options, args[1]);
        }
        if (args.length == 3 && subcommand.equals("send")) {
            return matching(knownServers(), args[2]);
        }
        return List.of();
    }

    /** Best effort: the servers this world already points at, since only the proxy knows them all. */
    private List<String> knownServers() {
        return new ArrayList<>(new TreeSet<>(
                plugin.portals().all().stream().map(Portal::targetServer).toList()));
    }

    private static List<String> matching(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }
}
