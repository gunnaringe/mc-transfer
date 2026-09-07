package xyz.haxxor.mctransfer.paper;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import xyz.haxxor.mctransfer.common.Portal;

/** Fires portals as players walk into them. */
final class PortalListener implements Listener {

    private final McTransferPaper plugin;

    PortalListener(McTransferPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        // Looking around and shuffling within a block raise move events constantly; only a change
        // of block can be a step onto or off a portal.
        if (PortalKeys.sameBlock(event.getFrom(), to)) {
            return;
        }
        Player player = event.getPlayer();
        Portal atFeet = plugin.portals().at(PortalKeys.at(to)).orElse(null);
        plugin.tracker()
                .onMove(player.getUniqueId(), atFeet)
                .ifPresent(portal -> plugin.requestTransfer(player, portal.targetServer(), portal.destination()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.tracker().forget(event.getPlayer().getUniqueId());
    }
}
