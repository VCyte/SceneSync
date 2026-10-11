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

Frame positions are **offsets in blocks from the actor's starting position, in the actor's starting facing basis**: positive z is forward and positive x is to the actor's right. The server and clients rotate those offsets into world coordinates from the synchronized origin yaw. While a path segment moves, the actor faces its direction of travel; on stationary segments, the frame yaw is relative to the starting view. `pitch` is relative to the starting view. Timeline time is in server ticks (20 ticks/second). Keyframe ticks must strictly increase; the first must be 0. The Paper server remains authoritative for the actual actor position and checks every movement step against world border, height, loaded chunks, and passable body blocks. Clients render the actor, armor, held items, and (for the actor's own client camera) position from the shared synchronized timeline, interpolated between server ticks. This improves visual smoothness but does not make the server hitbox client-authoritative; collision, protection plugins, latency, and other movement plugins can still affect the real server path. An unsafe step ends the scene rather than moving the actor into unloaded or solid space.

Built-in pose IDs in this preview are `idle`, `walk`, `run`, `wave`, `bow`, `spin`, `clap`, and `sit`. `idle` and `walk` retain vanilla locomotion; several other poses adjust humanoid body/limb model parts. The client mixin targets the shared `HumanoidModel` animation hook so the player body, vanilla worn-armor models, and arm-attached held-item rendering receive the same pose. `bow` is currently a brief body-bow gesture, not a weapon-draw animation. Camera yaw/pitch/roll are bounded degrees. Camera shake is a bounded degree amplitude applied only to the local player's camera.

## Commands

- `/scenesync list`
- `/scenesync status [player]`
- `/scenesync play <scene> [player]`
- `/scenesync stop [player]`
- `/scenesync reload`

Play, stop, and reload require `scenesync.admin`, defaulting to operators. Every actor whose server position is controlled needs the client mod handshake. Compatible viewers in the configured radius also receive the pose timeline; clients without the mod see only ordinary server movement.

## Boundaries

The API does not currently model item appearance overrides or arbitrary animation assets; equipped items are not swapped or edited. The code and CI build verify the model hook, but an in-game render test is still needed to confirm armor and held-item appearance in a live client. Third-party mods can change the same player model or camera fields, so load-order and renderer compatibility must be tested per supported modpack. SceneSync does not bypass server protection plugins or guarantee that another plugin will permit scripted movement. Custom pose rendering and camera motion are local visuals; movement remains server-authoritative.
