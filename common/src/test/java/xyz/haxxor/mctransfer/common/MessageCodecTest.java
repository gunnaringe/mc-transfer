package xyz.haxxor.mctransfer.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class MessageCodecTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0009-01f5d6885009");

    @Test
    @DisplayName("transfer request survives a round trip with an explicit destination")
    void transferRequestWithDestination() {
        Message.TransferRequest original = new Message.TransferRequest(
                PLAYER, "survival", Optional.of(new Destination(100.5, 65.0, -200.5, 90.0f, -12.5f)));

        assertThat(MessageCodec.decode(MessageCodec.encode(original))).contains(original);
    }

    @Test
    @DisplayName("transfer request survives a round trip when it defers to world spawn")
    void transferRequestWithoutDestination() {
        Message.TransferRequest original =
                new Message.TransferRequest(PLAYER, "lobby", Optional.empty());

        Optional<Message> decoded = MessageCodec.decode(MessageCodec.encode(original));

        assertThat(decoded).contains(original);
        assertThat(decoded.map(m -> (Message.TransferRequest) m).flatMap(Message.TransferRequest::destination))
                .isEmpty();
    }

    @Test
    void pendingTeleportRoundTrips() {
        Message.PendingTeleport withSpot =
                new Message.PendingTeleport(PLAYER, Optional.of(Destination.of(1.0, 2.0, 3.0)));
        Message.PendingTeleport toSpawn = new Message.PendingTeleport(PLAYER, Optional.empty());

        assertThat(MessageCodec.decode(MessageCodec.encode(withSpot))).contains(withSpot);
        assertThat(MessageCodec.decode(MessageCodec.encode(toSpawn))).contains(toSpawn);
    }

    @Test
    void serverListRoundTrips() {
        Message.ServerListRequest request = new Message.ServerListRequest(PLAYER);
        Message.ServerListResponse response = new Message.ServerListResponse(
                PLAYER, List.of("lobby", "creative", "lisasofie", "leajohanne", "survival"));

        assertThat(MessageCodec.decode(MessageCodec.encode(request))).contains(request);
        assertThat(MessageCodec.decode(MessageCodec.encode(response))).contains(response);
    }

    @Test
    void emptyServerListRoundTrips() {
        Message.ServerListResponse response = new Message.ServerListResponse(PLAYER, List.of());

        assertThat(MessageCodec.decode(MessageCodec.encode(response))).contains(response);
    }

    @Test
    @DisplayName("yaw and pitch are preserved, not silently dropped")
    void preservesOrientation() {
        Destination facing = new Destination(0.5, 64.0, 0.5, 177.25f, -33.75f);
        Message.PendingTeleport original = new Message.PendingTeleport(PLAYER, Optional.of(facing));

        Message.PendingTeleport decoded =
                (Message.PendingTeleport) MessageCodec.decode(MessageCodec.encode(original)).orElseThrow();

        assertThat(decoded.destination()).contains(facing);
    }

    @Test
    void typedDecodeNarrowsToTheRequestedType() {
        byte[] encoded = MessageCodec.encode(new Message.ServerListRequest(PLAYER));

        assertThat(MessageCodec.decode(encoded, Message.ServerListRequest.class)).isPresent();
        assertThat(MessageCodec.decode(encoded, Message.TransferRequest.class)).isEmpty();
    }

    @Test
    @DisplayName("garbage off the wire decodes to empty rather than throwing")
    void malformedInputIsIgnored() {
        assertThat(MessageCodec.decode(null)).isEmpty();
        assertThat(MessageCodec.decode(new byte[0])).isEmpty();
        assertThat(MessageCodec.decode(new byte[] {99})).isEmpty();
        assertThat(MessageCodec.decode(new byte[] {1, 2, 3})).isEmpty();
        assertThat(MessageCodec.decode("not a message at all".getBytes())).isEmpty();
    }

    @Test
    @DisplayName("a truncated but otherwise valid message decodes to empty")
    void truncatedInputIsIgnored() {
        byte[] full = MessageCodec.encode(
                new Message.TransferRequest(PLAYER, "survival", Optional.of(Destination.of(1, 2, 3))));

        for (int length = 1; length < full.length; length++) {
            assertThat(MessageCodec.decode(Arrays.copyOf(full, length)))
                    .as("truncated to %d of %d bytes", length, full.length)
                    .isEmpty();
        }
    }

    @Test
    @DisplayName("a bogus server count does not blow up the decoder")
    void negativeServerCountIsIgnored() {
        // type=SERVER_LIST_RESPONSE, uuid, then a negative length
        byte[] hostile = new byte[] {
            4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF
        };

        assertThat(MessageCodec.decode(hostile)).isEmpty();
    }
}
