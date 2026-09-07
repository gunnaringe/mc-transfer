package xyz.haxxor.mctransfer.paper;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import xyz.haxxor.mctransfer.common.Destination;
import xyz.haxxor.mctransfer.common.PortalKey;

/**
 * Puts arriving players where the trigger that sent them asked.
 *
 * <p>The proxy sends the landing spot once the switch has completed, which usually means the player
 * is already here - but the message and the join can land in either order, so a spot that arrives
 * early is held until the player shows up.
 */
final class ArrivalService implements Listener {

    /** Long enough to cover a slow join, short enough that a stale spot cannot surprise anyone. */
    private static final Duration HOLD = Duration.ofSeconds(30);

    private final McTransferPaper plugin;
    private final Map<UUID, Held> waiting = new ConcurrentHashMap<>();

    ArrivalService(McTransferPaper plugin) {
        this.plugin = plugin;
    }

    /** Called when the proxy tells us where a player should land. */
    void deliver(UUID player, Optional<Destination> destination) {
        Player online = Bukkit.getPlayer(player);
        if (online != null) {
            place(online, destination);
            return;
        }
        sweep();
        waiting.put(player, new Held(destination, Instant.now().plus(HOLD)));
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Held held = waiting.remove(event.getPlayer().getUniqueId());
        if (held != null && Instant.now().isBefore(held.expiresAt())) {
            place(event.getPlayer(), held.destination());
        }
    }

    private void place(Player player, Optional<Destination> destination) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location target = resolve(player, destination);

            // Arm the tracker before moving the player, not after. The teleport itself raises a
            // move event, so if the tracker still believed they were elsewhere when that arrived,
            // landing on a portal would read as a fresh step onto it and fire it - which is exactly
            // the bounce this is here to prevent.
            PortalKey landing = PortalKeys.at(target);
            plugin.tracker().markArrival(
                    player.getUniqueId(), plugin.portals().at(landing).isPresent() ? landing : null);

            player.teleportAsync(target);
        });
    }

    private Location resolve(Player player, Optional<Destination> destination) {
        World world = primaryWorld(player);
        return destination
                .map(spot -> new Location(world, spot.x(), spot.y(), spot.z(), spot.yaw(), spot.pitch()))
                .orElseGet(world::getSpawnLocation);
    }

    /**
     * Transfers always land in the server's primary world, which is what the destination
     * coordinates are relative to. Falls back to the player's current world only if the server
     * somehow has none, which cannot happen on a running server.
     */
    private static World primaryWorld(Player player) {
        return Bukkit.getWorlds().isEmpty() ? player.getWorld() : Bukkit.getWorlds().get(0);
    }

    private void sweep() {
        Instant now = Instant.now();
        Iterator<Map.Entry<UUID, Held>> entries = waiting.entrySet().iterator();
        while (entries.hasNext()) {
            if (now.isAfter(entries.next().getValue().expiresAt())) {
                entries.remove();
            }
        }
    }

    private record Held(Optional<Destination> destination, Instant expiresAt) {
    }
}
