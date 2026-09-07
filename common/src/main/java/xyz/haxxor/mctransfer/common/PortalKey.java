package xyz.haxxor.mctransfer.common;

import java.util.Objects;

/** Identifies the exact block that acts as a portal, on the server that owns it. */
public record PortalKey(String world, int x, int y, int z) {

    public PortalKey {
        Objects.requireNonNull(world, "world");
    }
}
