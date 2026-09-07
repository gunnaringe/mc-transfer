# mc-transfer

Portal and button driven transfers between the backend servers behind a Velocity proxy.

Replaces [`asnover/Transfer-Service`](https://github.com/asnover/Transfer-Service), which loads on
current Velocity builds but never finishes enabling (no exception at INFO level - the proxy log
just shows `Loaded plugin transferservice` and stops). The proxy log also shows Velocity's internal
Netty channel initializers being replaced via reflection at roughly that point in startup, which
points to that plugin hooking Velocity internals directly rather than using its public
plugin-messaging API - a technique that breaks as those internals change release to release. This
plugin only uses the public API on both sides.

## How it works

Two jars, one per side:

- **`mc-transfer-velocity`** - runs on the proxy. Stateless relay: a backend asks it to move a
  player, it performs the switch through Velocity's normal connection request (so
  `ServerPreConnectEvent` - and therefore any access-control plugin listening on it - still gates
  every transfer; this is not a bypass), then hands the destination backend a landing spot.
- **`mc-transfer-paper`** - runs on every backend. Owns that server's portals (`/transfer`
  command), fires them as players walk in, and applies a landing spot when the proxy delivers one
  on arrival.

Both talk over one plugin-messaging channel, `mctransfer:main`. The wire format
(`common/.../MessageCodec`) never throws on malformed input - a corrupt or unexpected payload
decodes to nothing rather than taking a listener down, since these bytes come off a live network
channel.

### Why command blocks can't just run `/server`

`/server <name>` only exists in Velocity's own command dispatcher, reached by Velocity intercepting
the *client's* outgoing command packet before it hits a backend. A command block executes directly
against the backend's command dispatcher instead - that packet path is never involved, so from
Paper's point of view `/server` doesn't exist. A button wired to a command block needs a command
that *does* exist on the backend (`/transfer send`, below), which asks the proxy to do the actual
switch over the plugin-messaging channel.

### Loop prevention

Portals are edge-triggered (`common/.../PortalTracker`): only the transition from "not standing on
this portal" to "standing on it" fires a transfer. Standing still never re-fires, and - critically -
neither does *landing* on a portal after being transferred in. Without that second part, a portal
whose destination happens to be another portal's exact coordinates would bounce a player back and
forth forever. See `PortalTrackerTest` for the scenario this specifically guards against.

### Permissions

- `mctransfer.manage` (op by default) - register, unregister, and list portals.
- `mctransfer.send` (everyone by default) - trigger a transfer, including from a command block
  behind a button. Deliberately open: unlike the plugin this replaces, whose non-player-sender
  permission skip was a bug, this is intentional - anyone can press an already-built button, but
  only an op can place the command block or portal in the first place.

## Commands

```
/transfer register <server> [x y z [yaw pitch]]   Register the block you're standing on as a
                                                     portal. Omit the coordinate to land arrivals
                                                     on the destination's world spawn instead.
/transfer unregister                                Remove the portal at your current location.
/transfer list                                      List portals on this server.
/transfer listservers                               List servers the proxy knows about.
/transfer send <target> <server> [x y z [yaw pitch]] Transfer <target> (a player name or selector)
                                                     now. What a button's command block runs.
```

## Data

- **Portals** - `plugins/mc-transfer/portals.yml` on each backend, a flat file (see
  `PortalStore`). A world holds a handful of portals; a database would be overkill, and being able
  to read or hand-edit the file is worth more here than anything a database would add.
- **Pending teleports** (the landing spot in flight to a destination after a switch) - in-memory
  only, keyed by player UUID, consumed once on join. Not persisted: worst case on a mistimed
  restart is landing at spawn instead of the exact spot, not data loss.
- **The proxy plugin** - stateless. Nothing to persist.

## Building

Requires JDK 25 - `paper-api` ships Java 25 class files, so nothing older can even load it as a
dependency (this is also why CI pins `java-version: "25"` rather than something lower).

```
mvn verify
```

Produces `velocity/target/mc-transfer-velocity.jar` and `paper/target/mc-transfer-paper.jar`.
Tagging `vX.Y.Z` and pushing builds and releases both as separate assets on that tag
(`.github/workflows/release.yml`).

## Deploying

Point each backend's `deployment.yaml` `initContainer` at the `mc-transfer-paper.jar` release
asset, and Velocity's at `mc-transfer-velocity.jar` - same `test -f ... || wget ...`
presence-only-check shape already used for every other plugin in `clusters/kate/minecraft`. Not
wired up yet; see [`clusters`](https://github.com/gunnaringe/clusters)' `kate/MINECRAFT.md` for the
rest of that namespace.
