package xyz.haxxor.mctransfer.common;

import java.util.Objects;
import java.util.Optional;

/**
 * A registered portal: stepping onto {@code location} sends the player to {@code targetServer}.
 *
 * <p>An empty {@code destination} means the destination server drops the player at its own world
 * spawn, resolved on arrival rather than baked in here.
 */
public record Portal(PortalKey location, String targetServer, Optional<Destination> destination) {

    public Portal {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(targetServer, "targetServer");
        Objects.requireNonNull(destination, "destination");
    }

    public static Portal toSpawn(PortalKey location, String targetServer) {
        return new Portal(location, targetServer, Optional.empty());
    }

    public static Portal to(PortalKey location, String targetServer, Destination destination) {
        return new Portal(location, targetServer, Optional.of(destination));
    }
}
