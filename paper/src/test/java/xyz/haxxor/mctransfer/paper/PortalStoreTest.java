package xyz.haxxor.mctransfer.paper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import xyz.haxxor.mctransfer.common.Destination;
import xyz.haxxor.mctransfer.common.Portal;
import xyz.haxxor.mctransfer.common.PortalKey;

class PortalStoreTest {

    @TempDir
    Path dir;

    @Test
    void loadingAMissingFileLeavesTheStoreEmpty() throws IOException {
        PortalStore store = new PortalStore();

        store.load(dir.resolve("portals.yml"));

        assertThat(store.all()).isEmpty();
    }

    @Test
    void portalsWithAnExplicitDestinationSurviveASaveAndLoad() throws IOException {
        Path file = dir.resolve("portals.yml");
        PortalStore store = new PortalStore();
        Portal portal = Portal.to(
                new PortalKey("world", -56, 64, 12), "survival", new Destination(0.5, 65.0, 0.5, 90f, 0f));
        store.put(portal);

        store.save(file);
        PortalStore reloaded = new PortalStore();
        reloaded.load(file);

        assertThat(reloaded.all()).containsExactly(portal);
    }

    @Test
    void portalsWithoutADestinationSurviveASaveAndLoad() throws IOException {
        Path file = dir.resolve("portals.yml");
        PortalStore store = new PortalStore();
        Portal portal = Portal.toSpawn(new PortalKey("world", 10, 70, 10), "lobby");
        store.put(portal);

        store.save(file);
        PortalStore reloaded = new PortalStore();
        reloaded.load(file);

        assertThat(reloaded.all()).containsExactly(portal);
        assertThat(reloaded.all().iterator().next().destination()).isEmpty();
    }

    @Test
    void removingAPortalDropsItFromTheNextSave() throws IOException {
        Path file = dir.resolve("portals.yml");
        PortalStore store = new PortalStore();
        PortalKey location = new PortalKey("world", 1, 2, 3);
        store.put(Portal.toSpawn(location, "creative"));
        store.save(file);

        store.remove(location);
        store.save(file);

        PortalStore reloaded = new PortalStore();
        reloaded.load(file);
        assertThat(reloaded.all()).isEmpty();
    }

    @Test
    void savingCreatesMissingParentDirectories() throws IOException {
        Path file = dir.resolve("nested/deeper/portals.yml");
        PortalStore store = new PortalStore();
        store.put(Portal.toSpawn(new PortalKey("world", 0, 0, 0), "lobby"));

        store.save(file);

        assertThat(file).exists();
    }

    @Test
    void multiplePortalsAllSurvive() throws IOException {
        Path file = dir.resolve("portals.yml");
        PortalStore store = new PortalStore();
        store.put(Portal.toSpawn(new PortalKey("world", 1, 2, 3), "lobby"));
        store.put(Portal.to(new PortalKey("world", 4, 5, 6), "survival", Destination.of(10, 20, 30)));
        store.put(Portal.toSpawn(new PortalKey("nether", 7, 8, 9), "creative"));

        store.save(file);
        PortalStore reloaded = new PortalStore();
        reloaded.load(file);

        assertThat(reloaded.all()).hasSize(3);
    }

    @Test
    @DisplayName("a portal entry missing required fields fails loudly")
    void aPortalEntryMissingRequiredFieldsFailsLoudly() throws IOException {
        Path file = dir.resolve("portals.yml");
        Files.writeString(file, """
                portals:
                  - world: "world"
                    x: 1
                    y: 2
                    # z is missing
                    target: "lobby"
                """);
        PortalStore store = new PortalStore();

        assertThatThrownBy(() -> store.load(file)).isInstanceOf(IOException.class);
    }

    @Test
    void aNonMappingPortalsSectionFailsLoudly() throws IOException {
        Path file = dir.resolve("portals.yml");
        Files.writeString(file, "portals: \"not a list\"\n");
        PortalStore store = new PortalStore();

        assertThatThrownBy(() -> store.load(file)).isInstanceOf(IOException.class);
    }
}
