package xyz.haxxor.mctransfer.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import xyz.haxxor.mctransfer.common.Channel;
import xyz.haxxor.mctransfer.common.Message;
import xyz.haxxor.mctransfer.common.MessageCodec;

/**
 * Proxy half of mc-transfer: a stateless relay.
 *
 * <p>Backends ask it to move a player; it performs the server switch through Velocity's ordinary
 * connection request (so {@code ServerPreConnectEvent}, and therefore velocity-access, still gates
 * every transfer - this is not a bypass), then tells the destination backend where to put the
 * player once they land.
 */
public class McTransferVelocity {

    private final ProxyServer proxy;
    private final Logger logger;
    private final MinecraftChannelIdentifier channel =
            MinecraftChannelIdentifier.create(Channel.NAMESPACE, Channel.NAME);

    @Inject
    public McTransferVelocity(ProxyServer proxy, Logger logger) {
        this.proxy = proxy;
        this.logger = logger;
    }

    @Subscribe
    public void onProxyInitialize(ProxyInitializeEvent event) {
        proxy.getChannelRegistrar().register(channel);
        logger.info("mc-transfer listening on {}", channel.getId());
    }

    @Subscribe
    public void onPluginMessage(PluginMessageEvent event) {
        if (!channel.getId().equals(event.getIdentifier().getId())) {
            return;
        }
        // Ours: never let it continue on to a backend or a client.
        event.setResult(PluginMessageEvent.ForwardResult.handled());

        // Only backends may drive this. A message on this channel from a player is a modified
        // client, and honouring it would let anyone shove any other player between servers, since
        // the payload names its subject by UUID rather than being implicitly about the sender.
        if (!(event.getSource() instanceof ServerConnection source)) {
            logger.warn("Ignoring {} traffic from a non-server source", channel.getId());
            return;
        }

        MessageCodec.decode(event.getData()).ifPresent(message -> {
            if (message instanceof Message.TransferRequest request) {
                transfer(request);
            } else if (message instanceof Message.ServerListRequest request) {
                sendServerList(source, request);
            }
            // PendingTeleport and ServerListResponse only ever travel proxy to backend; seeing one
            // here means a backend echoed it back, which is harmless to ignore.
        });
    }

    private void transfer(Message.TransferRequest request) {
        Optional<Player> maybePlayer = proxy.getPlayer(request.player());
        if (maybePlayer.isEmpty()) {
            // They disconnected between stepping on the portal and this arriving.
            return;
        }
        Player player = maybePlayer.get();

        Optional<RegisteredServer> maybeTarget = proxy.getServer(request.targetServer());
        if (maybeTarget.isEmpty()) {
            logger.warn("{} asked to go to unknown server '{}'", player.getUsername(), request.targetServer());
            return;
        }
        RegisteredServer target = maybeTarget.get();

        if (isAlreadyOn(player, target)) {
            // Same server: no switch to make, just reposition them where the trigger asked.
            deliverLandingSpot(target, request);
            return;
        }

        player.createConnectionRequest(target).connect().thenAccept(result -> {
            if (!result.isSuccessful()) {
                // Usually velocity-access declining the destination, which is a normal outcome.
                logger.info("Transfer of {} to {} was not completed",
                        player.getUsername(), target.getServerInfo().getName());
                return;
            }
            deliverLandingSpot(target, request);
        }).exceptionally(throwable -> {
            logger.warn("Transfer of {} to {} failed",
                    player.getUsername(), target.getServerInfo().getName(), throwable);
            return null;
        });
    }

    /**
     * Tells the destination where to place the player.
     *
     * <p>Sent after the switch completes rather than before: a plugin message to a backend rides an
     * existing player connection, so a server with nobody on it has no way to receive one. Once the
     * transferring player is through, that connection is guaranteed to exist.
     */
    private void deliverLandingSpot(RegisteredServer target, Message.TransferRequest request) {
        byte[] payload = MessageCodec.encode(
                new Message.PendingTeleport(request.player(), request.destination()));
        if (!target.sendPluginMessage(channel, payload)) {
            logger.warn("Could not hand a landing spot to {}; player will stay where they landed",
                    target.getServerInfo().getName());
        }
    }

    private void sendServerList(ServerConnection source, Message.ServerListRequest request) {
        List<String> names = proxy.getAllServers().stream()
                .map(server -> server.getServerInfo().getName())
                .sorted(Comparator.naturalOrder())
                .toList();
        source.getServer().sendPluginMessage(
                channel, MessageCodec.encode(new Message.ServerListResponse(request.player(), names)));
    }

    private static boolean isAlreadyOn(Player player, RegisteredServer target) {
        return player.getCurrentServer()
                .map(current -> current.getServerInfo().equals(target.getServerInfo()))
                .orElse(false);
    }
}
