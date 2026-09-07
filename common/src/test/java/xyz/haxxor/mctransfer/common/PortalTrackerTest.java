package xyz.haxxor.mctransfer.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PortalTrackerTest {

    private static final UUID LEA = UUID.fromString("00000000-0000-0000-0009-01f5d6885009");
    private static final UUID SOFIE = UUID.fromString("00000000-0000-0000-0009-01f455b557f9");

    private static final Portal TO_LOBBY =
            Portal.toSpawn(new PortalKey("world", -56, 64, 12), "lobby");
    private static final Portal TO_SURVIVAL =
            Portal.to(new PortalKey("world", 10, 70, 10), "survival", Destination.of(0, 65, 0));

    private PortalTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new PortalTracker();
    }

    @Test
    @DisplayName("walking onto a portal fires it")
    void steppingOnFires() {
        assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
    }

    @Test
    @DisplayName("standing on a portal does not fire it again")
    void standingStillDoesNotRefire() {
        tracker.onMove(LEA, TO_LOBBY);

        assertThat(tracker.onMove(LEA, TO_LOBBY)).isEmpty();
        assertThat(tracker.onMove(LEA, TO_LOBBY)).isEmpty();
    }

    @Test
    @DisplayName("stepping off and back on fires again")
    void steppingOffRearms() {
        tracker.onMove(LEA, TO_LOBBY);
        tracker.onMove(LEA, null);

        assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
    }

    @Test
    @DisplayName("stepping straight from one portal onto another fires the second")
    void adjacentPortalsBothFire() {
        assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
        assertThat(tracker.onMove(LEA, TO_SURVIVAL)).contains(TO_SURVIVAL);
    }

    @Test
    void movingOverOpenGroundNeverFires() {
        assertThat(tracker.onMove(LEA, null)).isEmpty();
        assertThat(tracker.onMove(LEA, null)).isEmpty();
    }

    @Test
    void playersAreTrackedIndependently() {
        tracker.onMove(LEA, TO_LOBBY);

        assertThat(tracker.onMove(SOFIE, TO_LOBBY)).contains(TO_LOBBY);
        assertThat(tracker.onMove(LEA, TO_LOBBY)).isEmpty();
    }

    @Test
    void forgettingAPlayerRearmsThem() {
        tracker.onMove(LEA, TO_LOBBY);
        tracker.forget(LEA);

        assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
    }

    @Nested
    @DisplayName("arriving via a transfer")
    class Arrival {

        @Test
        @DisplayName("landing on a portal does not bounce the player straight back")
        void landingOnAPortalDoesNotRefire() {
            tracker.markArrival(LEA, TO_LOBBY.location());

            assertThat(tracker.onMove(LEA, TO_LOBBY)).isEmpty();
        }

        @Test
        @DisplayName("a player who lands on a portal can still leave and use it deliberately")
        void landingThenSteppingOffRearms() {
            tracker.markArrival(LEA, TO_LOBBY.location());
            tracker.onMove(LEA, TO_LOBBY);
            tracker.onMove(LEA, null);

            assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
        }

        @Test
        @DisplayName("suppression never expires on its own while the player stands there")
        void suppressionIsNotTimeBased() {
            tracker.markArrival(LEA, TO_LOBBY.location());

            for (int tick = 0; tick < 1_000; tick++) {
                assertThat(tracker.onMove(LEA, TO_LOBBY)).isEmpty();
            }
        }

        @Test
        @DisplayName("landing on open ground leaves nearby portals armed")
        void landingOnOpenGroundArmsPortals() {
            tracker.markArrival(LEA, null);

            assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
        }

        @Test
        @DisplayName("landing on one portal still allows walking onto a different one")
        void landingSuppressesOnlyTheArrivalPortal() {
            tracker.markArrival(LEA, TO_LOBBY.location());

            assertThat(tracker.onMove(LEA, TO_SURVIVAL)).contains(TO_SURVIVAL);
        }

        @Test
        @DisplayName("the round trip a player would actually make does not loop")
        void thereAndBackAgainTerminates() {
            // Lea walks onto the portal home in leajohanne
            assertThat(tracker.onMove(LEA, TO_LOBBY)).contains(TO_LOBBY);
            // ...and lands in the lobby directly on the return portal
            tracker.markArrival(LEA, TO_SURVIVAL.location());
            // The move events that follow her arrival must not send her back
            assertThat(tracker.onMove(LEA, TO_SURVIVAL)).isEmpty();
            assertThat(tracker.onMove(LEA, TO_SURVIVAL)).isEmpty();
        }
    }
}
