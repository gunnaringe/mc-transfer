package xyz.haxxor.mctransfer.common;

/** The plugin-messaging channel the backends and the proxy talk over. */
public final class Channel {

    public static final String NAMESPACE = "mctransfer";
    public static final String NAME = "main";
    public static final String ID = NAMESPACE + ":" + NAME;

    private Channel() {
    }
}
