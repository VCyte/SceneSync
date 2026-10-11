# SceneSync Wire Protocol v1

SceneSync uses Paper plugin messaging and matching Fabric custom payload codecs. The Paper byte payload is raw UTF-8 JSON; Fabric codecs intentionally do not prepend a byte-array length prefix. Channel IDs are `scenesync:hello` (both directions), `scenesync:play` (server to client), and `scenesync:stop` (server to client).

## Limits

- Protocol version: `1`; exact match required.
- Raw packet body: 1..65,536 bytes.
- JSON structural nesting: at most 16 levels on clients; handshake parser uses a stricter limit.
- Scene frames: 1..128; total duration: 1..24,000 ticks.
- Relative axis offsets: -128..128 blocks; yaw: +/-360 degrees; pitch: +/-90 degrees.
- Camera yaw/pitch/roll: +/-45 degrees; shake: 0..10 degrees.
- Client inbound protocol work: at most 64 packets per 10 seconds per connection.

## Handshake

Client sends on `scenesync:hello`:

```json
{"protocol":1,"clientTime":1780000000000}
```

Server replies on the same channel with the echoed client time, server time, and a fresh per-player 128-bit random session token:

```json
{"protocol":1,"clientTime":1780000000000,"serverTime":1780000000012,"session":"32-lowercase-hex-characters"}
```

The client estimates clock offset from the round trip for a shared start time. The session token rejects packets left over from an earlier connection. It is not a cryptographic server signature; the connected Minecraft server is necessarily the authority for server-to-client scene visuals.

## Play packet

`scenesync:play` includes `protocol`, `session`, `runId`, `entityId`, `scene`, `durationTicks`, `startAtMillis`, the actor origin (`originX`, `originY`, `originZ`, `originYaw`), and `frames`. Each frame contains tick, actor-local x/y/z, yaw/pitch offsets, a built-in animation ID, and camera yaw/pitch/roll/shake. Clients rotate actor-local offsets into world space and render all viewers against the shared start time; the actor's own client camera follows that same interpolated location. This is render smoothing, not client authority: Paper still controls server position, collisions and protection checks.

## Stop packet

`scenesync:stop` contains `protocol`, `session`, `entityId`, and `runId`. A mismatched run ID cannot stop a newer track for the same entity. Tracks also expire locally after their bounded duration and are cleared on disconnect.
