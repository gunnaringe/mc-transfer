package xyz.haxxor.mctransfer.common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Wire format for {@link Message}.
 *
 * <p>Decoding never throws on malformed input - anything unrecognised comes back as an empty
 * {@link Optional}. Callers are reading bytes off a network channel, so a garbage payload has to be
 * an ignorable non-event rather than something that can take a listener down.
 */
public final class MessageCodec {

    private static final byte TRANSFER_REQUEST = 1;
    private static final byte PENDING_TELEPORT = 2;
    private static final byte SERVER_LIST_REQUEST = 3;
    private static final byte SERVER_LIST_RESPONSE = 4;

    private MessageCodec() {
    }

    public static byte[] encode(Message message) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            if (message instanceof Message.TransferRequest request) {
                out.writeByte(TRANSFER_REQUEST);
                writeUuid(out, request.player());
                out.writeUTF(request.targetServer());
                writeDestination(out, request.destination());
            } else if (message instanceof Message.PendingTeleport teleport) {
                out.writeByte(PENDING_TELEPORT);
                writeUuid(out, teleport.player());
                writeDestination(out, teleport.destination());
            } else if (message instanceof Message.ServerListRequest request) {
                out.writeByte(SERVER_LIST_REQUEST);
                writeUuid(out, request.player());
            } else if (message instanceof Message.ServerListResponse response) {
                out.writeByte(SERVER_LIST_RESPONSE);
                writeUuid(out, response.player());
                out.writeInt(response.servers().size());
                for (String server : response.servers()) {
                    out.writeUTF(server);
                }
            } else {
                throw new IllegalArgumentException("Unsupported message: " + message);
            }
        } catch (IOException e) {
            // ByteArrayOutputStream does not do I/O, so this cannot happen in practice.
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static Optional<Message> decode(byte[] data) {
        if (data == null || data.length == 0) {
            return Optional.empty();
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(data))) {
            return switch (in.readByte()) {
                case TRANSFER_REQUEST -> Optional.of(new Message.TransferRequest(
                        readUuid(in), in.readUTF(), readDestination(in)));
                case PENDING_TELEPORT -> Optional.of(new Message.PendingTeleport(
                        readUuid(in), readDestination(in)));
                case SERVER_LIST_REQUEST -> Optional.of(new Message.ServerListRequest(readUuid(in)));
                case SERVER_LIST_RESPONSE -> Optional.of(readServerListResponse(in));
                default -> Optional.empty();
            };
        } catch (IOException | RuntimeException e) {
            return Optional.empty();
        }
    }

    /** Decodes and narrows in one step, for listeners that only care about one message type. */
    public static <T extends Message> Optional<T> decode(byte[] data, Class<T> type) {
        return decode(data).filter(type::isInstance).map(type::cast);
    }

    private static Message.ServerListResponse readServerListResponse(DataInputStream in) throws IOException {
        UUID player = readUuid(in);
        int count = in.readInt();
        if (count < 0) {
            throw new IOException("Negative server count: " + count);
        }
        // Not preallocated: count comes off the wire and is not trustworthy as a capacity hint.
        List<String> servers = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            servers.add(in.readUTF());
        }
        return new Message.ServerListResponse(player, servers);
    }

    private static void writeUuid(DataOutputStream out, UUID uuid) throws IOException {
        out.writeLong(uuid.getMostSignificantBits());
        out.writeLong(uuid.getLeastSignificantBits());
    }

    private static UUID readUuid(DataInputStream in) throws IOException {
        return new UUID(in.readLong(), in.readLong());
    }

    private static void writeDestination(DataOutputStream out, Optional<Destination> destination) throws IOException {
        out.writeBoolean(destination.isPresent());
        if (destination.isPresent()) {
            Destination d = destination.get();
            out.writeDouble(d.x());
            out.writeDouble(d.y());
            out.writeDouble(d.z());
            out.writeFloat(d.yaw());
            out.writeFloat(d.pitch());
        }
    }

    private static Optional<Destination> readDestination(DataInputStream in) throws IOException {
        if (!in.readBoolean()) {
            return Optional.empty();
        }
        return Optional.of(new Destination(
                in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(), in.readFloat()));
    }
}
