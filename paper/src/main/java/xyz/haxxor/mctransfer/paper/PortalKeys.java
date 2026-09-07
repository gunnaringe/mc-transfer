package xyz.haxxor.mctransfer.paper;

import org.bukkit.Location;
import xyz.haxxor.mctransfer.common.PortalKey;

/**
 * Maps between Bukkit locations and portal identities.
 *
 * <p>A portal is the block a player's feet occupy, so registering one is just "stand where the
 * portal should be" and triggering it is "walk into that block".
 */
final class PortalKeys {

    private PortalKeys() {
    }

    static PortalKey at(Location location) {
        String world = location.getWorld() == null ? "" : location.getWorld().getName();
        return new PortalKey(world, location.getBlockX(), location.getBlockY(), location.getBlockZ());
    }

    static boolean sameBlock(Location a, Location b) {
        return a.getBlockX() == b.getBlockX()
                && a.getBlockY() == b.getBlockY()
                && a.getBlockZ() == b.getBlockZ()
                && a.getWorld() != null
                && a.getWorld().equals(b.getWorld());
    }
}
