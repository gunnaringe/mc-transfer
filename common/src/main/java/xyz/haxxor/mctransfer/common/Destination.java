package xyz.haxxor.mctransfer.common;

/**
 * A landing spot on the destination server, in that server's primary world.
 *
 * <p>Positions are world-less on purpose: a transfer always lands in the destination server's
 * primary world, so carrying a world name across the wire would imply a cross-world capability
 * that does not exist.
 */
public record Destination(double x, double y, double z, float yaw, float pitch) {

    public static Destination of(double x, double y, double z) {
        return new Destination(x, y, z, 0f, 0f);
    }
}
