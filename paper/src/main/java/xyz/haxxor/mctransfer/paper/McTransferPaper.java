package xyz.haxxor.mctransfer.paper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;
import xyz.haxxor.mctransfer.common.Channel;
import xyz.haxxor.mctransfer.common.Destination;
import xyz.haxxor.mctransfer.common.Message;
import xyz.haxxor.mctransfer.common.MessageCodec;
import xyz.haxxor.mctransfer.common.PortalTracker;

/**
 * Backend half of mc-transfer.
 *
 * <p>Owns this server's portals, decides when one has been stepped on, and asks the proxy to do the
 * actual moving. It never tries to switch a player itself - only the proxy can do that, which is
 * why this talks over a plugin-messaging channel rather than running a command.
 */
public final class McTransferPaper extends JavaPlugin implements PluginMessageListener {

    private final PortalStore portals = new PortalStore();
    private final PortalTracker tracker = new PortalTracker();
    private ArrivalService arrivals;
    private Path portalFile;

    @Override
    public void onEnable() {
        portalFile = getDataFolder().toPath().resolve("portals.yml");
        try {
            portals.load(portalFile);
        } catch (IOException e) {
            // Starting with an empty store would mean the next registration writes the file out and
            // takes the existing portals with it, so refuse to run until a human has looked.
            getSLF4JLogger().error("Could not read {}; not starting so the file is left intact", portalFile, e);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        arrivals = new ArrivalService(this);

        getServer().getMessenger().registerOutgoingPluginChannel(this, Channel.ID);
        getServer().getMessenger().registerIncomingPluginChannel(this, Channel.ID, this);
        getServer().getPluginManager().registerEvents(new PortalListener(this), this);
        getServer().getPluginManager().registerEvents(arrivals, this);

        TransferCommand command = new TransferCommand(this);
        Objects.requireNonNull(getCommand("transfer"), "command 'transfer' is missing from plugin.yml")
                .setExecutor(command);
        Objects.requireNonNull(getCommand("transfer")).setTabCompleter(command);

        getSLF4JLogger().info("mc-transfer ready with {} portal(s)", portals.size());
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player player, byte @NotNull [] data) {
        if (!Channel.ID.equals(channel)) {
            return;
        }
        MessageCodec.decode(data).ifPresent(message -> {
            if (message instanceof Message.PendingTeleport teleport) {
                arrivals.deliver(teleport.player(), teleport.destination());
            } else if (message instanceof Message.ServerListResponse response) {
                showServerList(response);
            }
        });
    }

    /** Asks the proxy to move a player, which is the only way a backend can make that happen. */
    void requestTransfer(Player player, String targetServer, Optional<Destination> destination) {
        send(player, new Message.TransferRequest(player.getUniqueId(), targetServer, destination));
    }

    void requestServerList(Player player) {
        send(player, new Message.ServerListRequest(player.getUniqueId()));
    }

    private void send(Player carrier, Message message) {
        carrier.sendPluginMessage(this, Channel.ID, MessageCodec.encode(message));
    }

    private void showServerList(Message.ServerListResponse response) {
        Player player = Bukkit.getPlayer(response.player());
        if (player == null) {
            return;
        }
        if (response.servers().isEmpty()) {
            player.sendMessage(Component.text("The proxy reported no servers.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Servers: ", NamedTextColor.GRAY)
                .append(Component.text(String.join(", ", response.servers()), NamedTextColor.WHITE)));
    }

    /** Persists the portal list, reporting failure rather than losing it silently. */
    boolean savePortals() {
        try {
            portals.save(portalFile);
            return true;
        } catch (IOException e) {
            getSLF4JLogger().error("Could not write {}", portalFile, e);
            return false;
        }
    }

    PortalStore portals() {
        return portals;
    }

    PortalTracker tracker() {
        return tracker;
    }
}
