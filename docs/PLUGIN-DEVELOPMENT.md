# SceneSync Plugin Development

## Dependency

```gradle
dependencies {
    compileOnly files('libs/scenesync-api-0.1.0.jar')
    compileOnly 'io.papermc.paper:paper-api:26.2.build.124-stable'
}
```

Download `scenesync-api-0.1.0.jar` from the matching SceneSync GitHub Release into your plugin project's `libs/` directory. For a local multi-project build, use `implementation project(':scenesync-api')`.

## Register and play a scene

The Paper plugin registers `SceneManager` with Bukkit's ServicesManager during enable:

```java
SceneManager manager = getServer().getServicesManager().load(SceneManager.class);
if (manager == null) throw new IllegalStateException("SceneSync is not installed");

SceneDefinition intro = new SceneDefinition("myplugin.intro", 40, List.of(
    new SceneFrame(0, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0),
    new SceneFrame(20, 0, 0, 2, 0, 0, "walk", 0, 0, 0, 0),
    new SceneFrame(40, 0, 0, 2, 0, 0, "wave", 0, 0, 0, 0)
));
manager.register(intro);
manager.play(player, intro.id());
```

All `SceneManager` calls must run on the Paper server thread. Register scenes during plugin enable and unregister them during disable if the registering plugin may reload independently. A later registration with the same ID replaces the previous API registration. API scenes override a configured scene with the same ID. There is a cap of 128 API scenes.

## Coordinates and time

Frame positions are **offsets in blocks from the actor's starting position, in the actor's starting facing basis**: positive z is forward and positive x is to the actor's right. The server and clients rotate those offsets into world coordinates from the synchronized origin yaw. While a path segment moves, the actor faces its direction of travel; on stationary segments, the frame yaw is relative to the starting view. `pitch` is relative to the starting view. Timeline time is in server ticks (20 ticks/second), with a short shared countdown; keyframe ticks must strictly increase and the first must be 0. The actor's client mod sends ordinary player movement packets at the timeline position instead of having Paper teleport the player every tick. While a track is active, movement, jump, sprint, and shift inputs are suppressed locally; mouse look remains available. Paper checks the trajectory, world safety, and movement packet deviation and ends playback on an unsafe path, significant deviation, external teleport, or competing movement. Compatible clients render actor, armor, held items, and actor camera position from the same timeline. This avoids the server teleport-correction loop, but collision, protection plugins, latency, and other movement plugins can still affect playback.

Built-in pose IDs in this preview are `idle`, `walk`, `run`, `wave`, `bow`, `spin`, `clap`, and `sit`. `idle` and `walk` retain vanilla locomotion; several other poses adjust humanoid body/limb model parts. The shared `HumanoidModel` hook applies the same pose to player, vanilla armor, and arm-attached held-item rendering. Head direction is locked to the scene pose, not mouse aim. `bow` is a brief body-bow gesture, not a weapon-draw animation. `camera-fov` is an additive degree offset, bounded to +/-25 and clamped to 30..110; it defaults to 0. Legacy camera yaw/pitch/roll/shake inputs are accepted for compatibility but ignored, preserving the player's own look direction.

## Commands

- `/scenesync list`
- `/scenesync status [player]`
- `/scenesync play <scene> [player]`
- `/scenesync stop [player]`
- `/scenesync reload`

Play, stop, and reload require `scenesync.admin`, defaulting to operators. Every actor whose client movement follows the scene needs the client mod handshake. Compatible viewers in the configured radius also receive the pose timeline; clients without the mod see ordinary server-synchronized movement only.

## Boundaries

The API does not currently model item appearance overrides or arbitrary animation assets; equipped items are not swapped or edited. The code and CI build verify input hooks and movement plumbing, but an in-game test is still required for packet behavior, smoothness, camera comfort, armor, and held items. Third-party mods can change the same player model/input fields, so load-order and renderer compatibility must be tested per supported modpack. SceneSync does not bypass server protection plugins or guarantee that another plugin will permit scripted movement. The server checks and can terminate the path; the client mod supplies each normal movement update to avoid forced teleports.
