# SceneSync Wire Protocol v2

SceneSync uses Paper plugin messaging and matching Fabric custom payload codecs. The Paper byte payload is raw UTF-8 JSON; Fabric codecs intentionally do not prepend a byte-array length prefix. Channel IDs are `scenesync:hello` (both directions), `scenesync:play` (server to client), and `scenesync:stop` (server to client).

## Limits

- Protocol version: `2`; exact match required. Version 2 is incompatible with the teleport-driven v1 movement contract.
- Raw packet body: 1..65,536 bytes.
- JSON structural nesting: at most 16 levels on clients; handshake parser uses a stricter limit.
- Scene frames: 1..128; total duration: 1..24,000 ticks.
- Relative axis offsets: -128..128 blocks; yaw: +/-360 degrees; pitch: +/-90 degrees.
- Legacy camera yaw/pitch/roll and shake fields: accepted but ignored. `cameraFov`: +/-25 degrees, additively applied and clamped to 30..110 degrees.
- Client inbound protocol work: at most 64 packets per 10 seconds per connection.

## Handshake

Client sends on `scenesync:hello`:

```json
{"protocol":2,"clientTime":1780000000000}
```

Server replies on the same channel with the echoed client time, server time, and a fresh per-player 128-bit random session token:

```json
{"protocol":2,"clientTime":1780000000000,"serverTime":1780000000012,"session":"32-lowercase-hex-characters"}
```

The client estimates clock offset from the round trip for a shared start time. The session token rejects packets left over from an earlier connection. It is not a cryptographic server signature; the connected Minecraft server is necessarily the authority for server-to-client scene visuals.

## Play packet

`scenesync:play` includes `protocol`, `session`, `runId`, `entityId`, `scene`, `durationTicks`, `startAtMillis`, the actor origin (`originX`, `originY`, `originZ`, `originYaw`), and `frames`. Each frame contains tick, actor-local x/y/z, yaw/pitch offsets, a built-in animation ID, legacy camera rotation/shake fields (ignored), and optional `cameraFov` (degrees, +/-25). The actor client sends normal movement packets at the shared timeline position; Paper checks that the movement stays on the scene path and stops playback on significant divergence or unsafe conditions. Compatible viewers render from the same synchronized start time. The actor's own camera follows its locally interpolated position, keeps the player's look direction, and may apply only the bounded FOV offset. The protocol never forces camera yaw/pitch/roll.

## Stop packet

`scenesync:stop` contains `protocol`, `session`, `entityId`, and `runId`. A mismatched run ID cannot stop a newer track for the same entity. Tracks also expire locally after their bounded duration and are cleared on disconnect.
