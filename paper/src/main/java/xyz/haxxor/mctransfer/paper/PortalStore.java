package xyz.haxxor.mctransfer.paper;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import xyz.haxxor.mctransfer.common.Destination;
import xyz.haxxor.mctransfer.common.Portal;
import xyz.haxxor.mctransfer.common.PortalKey;

/**
 * The portals registered on this server, backed by {@code portals.yml}.
 *
 * <p>Deliberately a flat file rather than a database: a world holds a handful of portals, and being
 * able to read and hand-edit the file is worth more here than anything a database would add.
 */
public final class PortalStore {

    private final Map<PortalKey, Portal> portals = new ConcurrentHashMap<>();

    public void put(Portal portal) {
        portals.put(portal.location(), portal);
    }

    public boolean remove(PortalKey location) {
        return portals.remove(location) != null;
    }

    public Optional<Portal> at(PortalKey location) {
        return Optional.ofNullable(portals.get(location));
    }

    public Collection<Portal> all() {
        return List.copyOf(portals.values());
    }

    public int size() {
        return portals.size();
    }

    /**
     * Replaces the contents of this store with what is on disk.
     *
     * <p>A missing file is not an error - that is simply a server with no portals yet. Anything
     * present but unreadable throws, so a typo in a hand-edited file surfaces loudly instead of
     * quietly presenting as "all your portals are gone".
     */
    public void load(Path file) throws IOException {
        if (!Files.exists(file)) {
            portals.clear();
            return;
        }
        Map<String, Object> root;
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            root = new Yaml().load(reader);
        } catch (RuntimeException e) {
            throw new IOException("Could not parse " + file, e);
        }

        Map<PortalKey, Portal> loaded = new LinkedHashMap<>();
        for (Object entry : entries(root, file)) {
            Portal portal = readPortal(entry, file);
            loaded.put(portal.location(), portal);
        }
        portals.clear();
        portals.putAll(loaded);
    }

    /** Writes the store out, replacing the file atomically so a crash cannot truncate it. */
    public void save(Path file) throws IOException {
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Portal portal : portals.values()) {
            entries.add(writePortal(portal));
        }
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("portals", entries);

        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);

        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            new Yaml(options).dump(root, writer);
        }
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // Some network filesystems cannot do this; a plain replace is still better than nothing.
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static List<?> entries(Map<String, Object> root, Path file) throws IOException {
        if (root == null || root.get("portals") == null) {
            return List.of();
        }
        if (!(root.get("portals") instanceof List<?> list)) {
            throw new IOException("Expected 'portals' to be a list in " + file);
        }
        return list;
    }

    private static Portal readPortal(Object entry, Path file) throws IOException {
        if (!(entry instanceof Map<?, ?> map)) {
            throw new IOException("Expected a portal entry to be a mapping in " + file);
        }
        PortalKey location = new PortalKey(
                string(map, "world", file),
                (int) number(map, "x", file),
                (int) number(map, "y", file),
                (int) number(map, "z", file));
        String target = string(map, "target", file);

        Object destination = map.get("destination");
        if (destination == null) {
            return Portal.toSpawn(location, target);
        }
        if (!(destination instanceof Map<?, ?> spot)) {
            throw new IOException("Expected 'destination' to be a mapping in " + file);
        }
        return Portal.to(location, target, new Destination(
                number(spot, "x", file),
                number(spot, "y", file),
                number(spot, "z", file),
                (float) optionalNumber(spot, "yaw"),
                (float) optionalNumber(spot, "pitch")));
    }

    private static Map<String, Object> writePortal(Portal portal) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("world", portal.location().world());
        entry.put("x", portal.location().x());
        entry.put("y", portal.location().y());
        entry.put("z", portal.location().z());
        entry.put("target", portal.targetServer());
        portal.destination().ifPresent(destination -> {
            Map<String, Object> spot = new LinkedHashMap<>();
            spot.put("x", destination.x());
            spot.put("y", destination.y());
            spot.put("z", destination.z());
            spot.put("yaw", (double) destination.yaw());
            spot.put("pitch", (double) destination.pitch());
            entry.put("destination", spot);
        });
        return entry;
    }

    private static String string(Map<?, ?> map, String key, Path file) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IOException("Missing '" + key + "' in " + file);
        }
        return text;
    }

    private static double number(Map<?, ?> map, String key, Path file) throws IOException {
        Object value = map.get(key);
        if (!(value instanceof Number n)) {
            throw new IOException("Missing or non-numeric '" + key + "' in " + file);
        }
        return n.doubleValue();
    }

    private static double optionalNumber(Map<?, ?> map, String key) {
        return map.get(key) instanceof Number n ? n.doubleValue() : 0d;
    }
}
