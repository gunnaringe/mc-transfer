package xyz.haxxor.mctransfer.common;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Everything that crosses the {@link Channel} between a backend and the proxy. */
public sealed interface Message {

    /** Backend to proxy: please move this player to another server. */
    record TransferRequest(UUID player, String targetServer, Optional<Destination> destination)
            implements Message {

        public TransferRequest {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(targetServer, "targetServer");
            Objects.requireNonNull(destination, "destination");
        }
    }

    /**
     * Proxy to destination backend: this player is on their way, put them here on arrival.
     *
     * <p>An empty {@code destination} means "use your own world spawn".
     */
    record PendingTeleport(UUID player, Optional<Destination> destination) implements Message {

        public PendingTeleport {
            Objects.requireNonNull(player, "player");
            Objects.requireNonNull(destination, "destination");
        }
    }

    /** Backend to proxy: what servers can I send people to? */
    record ServerListRequest(UUID player) implements Message {

        public ServerListRequest {
            Objects.requireNonNull(player, "player");
        }
    }

    /** Proxy to backend: the answer, to be shown to the player who asked. */
    record ServerListResponse(UUID player, List<String> servers) implements Message {

        public ServerListResponse {
            Objects.requireNonNull(player, "player");
            servers = List.copyOf(servers);
        }
    }
}
