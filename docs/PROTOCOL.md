# SceneSync Wire Protocol v3

SceneSync uses Paper plugin messaging and matching Fabric custom payload codecs. The Paper byte payload is raw UTF-8 JSON; Fabric codecs intentionally do not prepend a byte-array length prefix. Channel IDs are `scenesync:hello` (both directions), `scenesync:play` (server to client), and `scenesync:stop` (server to client).

## Limits

- Protocol version: `3`; exact match required. Version 3 is incompatible with protocol v2 because scene frames now carry generic server-authored skeleton poses.
- Raw packet body: 1..65,536 bytes.
- JSON structural nesting: at most 16 levels on clients; handshake parser uses a stricter limit.
- Scene frames: 1..128; total duration: 1..24,000 ticks.
- Relative axis offsets: -128..128 blocks; yaw: +/-360 degrees; pitch: +/-90 degrees.
- Camera mode: `FREE_LOOK`, `LOCKED`, or `LIMITED`; yaw cone 0..100 degrees and pitch cone 0..70 degrees. Omitted camera settings default to `FREE_LOOK`.
- Frame camera yaw/pitch: actor-relative offsets in -45..45 degrees; roll is applied at at most +/-6 degrees and shake at at most 1 degree. `cameraFov`: +/-25 degrees, additively applied and clamped to 30..110 degrees.
- Animation label: 1..96 lowercase characters matching `[a-z0-9][a-z0-9._:-]{0,95}`; third-party plugins should namespace IDs (for example `myplugin:slash`). A non-legacy ID must have a `pose` on every frame where it is selected.
- Generic pose: six nested arrays ordered `head`, `body`, `left-arm`, `right-arm`, `left-leg`, `right-leg`; each part is `[x,y,z,pitch,yaw,roll]`. Translation offsets are model pixels in -8..8; rotations are degrees in -180..180. Missing transforms are neutral on the API/YAML side; serialized v3 poses include all parts. The client interpolates translations and shortest-path rotations. Head rotation offsets are combined with action-camera offsets, then clamped around the vanilla look pose to +/-55 degrees pitch, +/-65 yaw, and +/-35 roll.
- Client inbound protocol work: at most 64 packets per 10 seconds per connection.

## Handshake

Client sends on `scenesync:hello`:

```json
{"protocol":3,"clientTime":1780000000000}
```

Server replies on the same channel with the echoed client time, server time, and a fresh per-player 128-bit random session token:

```json
{"protocol":3,"clientTime":1780000000000,"serverTime":1780000000012,"session":"32-lowercase-hex-characters"}
```

The client estimates clock offset from the round trip for a shared start time. The session token rejects packets left over from an earlier connection. It is not a cryptographic server signature; the connected Minecraft server is necessarily the authority for server-to-client scene visuals.

## Play packet

`scenesync:play` includes `protocol`, `session`, `runId`, `entityId`, `scene`, `durationTicks`, `startAtMillis`, the actor origin (`originX`, `originY`, `originZ`, `originYaw`), `cameraControl`, and `frames`. Before broadcast, Paper preflights the actor-specific fixed path against world collision. A blocked path is replaced by a safe prefix and short generic brace/recovery timeline; all viewers receive that same planned origin and curve. The actor client samples actor-local positions using the same shape-preserving cubic interpolation as Paper, and sends ordinary movement packets. Paper matches packets against fractional points in a bounded-latency window rather than sweeping potentially lag-separated `from` and `to` locations; it still checks the planned route at sub-tick resolution and actor width. Significant deviation or unsafe geometry stops playback. Compatible viewers render from the same synchronized start time.

## Stop packet

`scenesync:stop` contains `protocol`, `session`, `entityId`, and `runId`. A mismatched run ID cannot stop a newer track for the same entity. Tracks also expire locally after their bounded duration and are cleared on disconnect.
