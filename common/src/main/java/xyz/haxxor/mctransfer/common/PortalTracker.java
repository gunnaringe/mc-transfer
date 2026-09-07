package xyz.haxxor.mctransfer.common;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Decides when standing on a portal should actually fire it.
 *
 * <p>Portals are edge-triggered: only the transition from "not on this portal" to "on this portal"
 * counts. Continuing to stand on one does nothing, so a player who lands on a portal never bounces
 * straight back out of it.
 *
 * <p>Arrivals reuse the same mechanism rather than a separate immunity timer: {@link
 * #markArrival} records the portal a player landed on as their current position, so the next move
 * is not an edge. Stepping off clears it, and stepping back on is a fresh edge that fires normally.
 * This is deliberately not time-based - a player who lands on a portal and stands there thinking
 * should not be transferred when some cooldown quietly expires underneath them.
 *
 * <p>State is per-player and purely in-memory; losing it on restart only costs a player one
 * suppressed trigger, so it is never persisted.
 */
public final class PortalTracker {

    private final Map<UUID, PortalKey> standingOn = new ConcurrentHashMap<>();

    /**
     * Records a move and reports the portal to fire, if any.
     *
     * @param player      the player who moved
     * @param portalAtFeet the portal at the player's new position, or {@code null} if there is none
     */
    public Optional<Portal> onMove(UUID player, Portal portalAtFeet) {
        Objects.requireNonNull(player, "player");
        PortalKey current = portalAtFeet == null ? null : portalAtFeet.location();
        PortalKey previous = standingOn.get(player);
        if (Objects.equals(previous, current)) {
            return Optional.empty();
        }
        if (current == null) {
            standingOn.remove(player);
            return Optional.empty();
        }
        standingOn.put(player, current);
        return Optional.of(portalAtFeet);
    }

    /**
     * Records where a player landed after being transferred in, suppressing that spot's portal
     * until they step off it.
     *
     * @param arrivedOn the portal they landed on, or {@code null} if they landed on open ground
     */
    public void markArrival(UUID player, PortalKey arrivedOn) {
        Objects.requireNonNull(player, "player");
        if (arrivedOn == null) {
            standingOn.remove(player);
        } else {
            standingOn.put(player, arrivedOn);
        }
    }

    /** Drops a player's state, for when they disconnect. */
    public void forget(UUID player) {
        standingOn.remove(player);
    }
}
