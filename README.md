# SceneSync

**Server-authored cinematic scenes for Minecraft: synchronized character motion, animation, and local camera effects.**

SceneSync is a separate project from SKM. A Paper plugin owns scene definitions, scheduling, animation labels and per-keyframe humanoid pose data, plus path/collision validation. The actor's Fabric client follows the shared timeline using ordinary movement packets rather than a server teleport every tick; compatible viewers render the actor, worn armor, held items, and animation from that same timeline. The client contains a generic six-part humanoid pose renderer, so a plugin can add a new animation ID and bone keyframes without publishing a new client mod for each animation. Movement, jump, sprint, and shift inputs are blocked locally during playback, while mouse look remains free. Paths use the actor's initial facing basis and moving actors face their travel direction. Plugins can register scenes through the Java API.

> **Current status: 0.1.2 development preview.** This is a buildable foundation, not a claim of universal mod compatibility or production readiness. The initial target is Minecraft 26.2, Java 25, Paper 26.2, Fabric Loader 0.19.5+, and Fabric API 0.161.0+26.2. Every viewer who should see custom poses or camera effects needs the client mod. Vanilla clients still receive ordinary server movement but cannot render SceneSync-specific animation or camera effects.

## Project modules

- `scenesync-api`: immutable, bounded scene timeline models and the plugin-facing `SceneManager` contract.
- `paper-plugin`: Paper runtime, permission-checked commands, bounded/versioned plugin-message bridge, actor movement, and lifecycle cleanup.
- `fabric-client`: client-only Fabric mod for protocol handling, synchronized pose playback, and local camera motion.

## Build

Requires JDK 25. The Gradle wrapper is included.

```bash
./gradlew clean test build
```

Artifacts are written to:

- `paper-plugin/build/libs/SceneSync-Paper-0.1.2.jar`
- `fabric-client/build/libs/scenesync-client-26.2-0.1.2.jar`
- `scenesync-api/build/libs/scenesync-api-0.1.2.jar`

## Install

1. Install the Paper plugin on the server.
2. Install the matching protocol-v3 Fabric client mod on every player who should act in scenes or see SceneSync visuals.
3. Restart the server and client, then use `/scenesync help`.

The plugin has a `demo` scene and a `thunderclap-flash` example in `paper-plugin/src/main/resources/config.yml`. Start it with `/scenesync play thunderclap-flash [player]`; only operators can start or stop scenes by default. Charge, dash and finish poses are supplied as YAML bone keyframes under namespaced animation labels, demonstrating that these motions are not compiled into the mod. The example locks the actor's view, dashes 24 blocks over 6 ticks and then holds the finishing pose. If you already have `plugins/SceneSync/config.yml`, add/copy the new scene there before `/scenesync reload`—existing configs are not overwritten.

## Design and safety

- Before playback starts, Paper checks the actor's complete fixed path against world collision and samples fast segments with body-width checks. If a static obstruction intersects the route, the exact safe path is shortened for that actor and includes a short brace/recovery pose before any client is told to dash; it does not intentionally send the player through the wall and then roll them back. Movement packets are checked against the same planned timeline, not a straight-line sweep between potentially lag-separated server positions.
- Each scene can preserve free look, smoothly lock to a keyframed action camera, or clamp mouse look to a forward cone. The included LIMITED demo uses +/-55° yaw and +/-30° pitch; excess mouse travel is discarded at the boundary, so reversing the mouse responds immediately. The client preserves vanilla F5 front-camera mirroring. Camera yaw/pitch keyframes move the actor's view and authored head pose together; only the actor's camera is controlled, never viewers' cameras. Roll and shake are optional and tightly clamped; FOV is bounded and defaults to neutral.
- Scene damage behavior is configurable per animation: the scene fallback is `CANCEL_SCENE` by default, and entries such as `wave: IGNORE_DAMAGE` can override individual animation IDs.
- Network payloads are versioned (protocol 3), size-bounded, parsed with strict bounds, and rate-limited. Generic pose packets are restricted to six known humanoid parts with bounded transforms; unsupported protocol versions are rejected.
- Scene duration, frame count, coordinates, additive FOV offsets, namespaced animation labels, and bone transforms have explicit limits.
- Starting a scene never writes to player inventory. Cosmetic equipment replacement and cross-mod animation compatibility are not yet advertised as complete features.
- Client/server path interpolation uses the same shape-preserving cubic curve and fractional-time path checks. Head animation and camera aim are combined then clamped to bounded offsets around the vanilla head pose (55° pitch, 65° yaw, 35° roll) to prevent additive neck flips. Generic and legacy poses ease/crossfade; early STOP fades visuals to vanilla over 200 ms while input suppression ends immediately.
- Disconnect, world change, plugin disable, and explicit stop terminate active playback. Movement, jump, sprint, and shift input suppression ends immediately when the local scene track stops.

## Plugin API sketch

```java
SceneManager scenes = (SceneManager) Bukkit.getServicesManager()
    .load(SceneManager.class);
scenes.register(sceneDefinition);
scenes.play(player, "intro");
```

See `docs/PLUGIN-DEVELOPMENT.md` for the exact current API and limitations, including per-animation damage policy.

## Compatibility and roadmap

Initial scope is player actors, keyframed client-driven normal movement packets with Paper path/collision validation, shared-time client interpolation of plugin-supplied generic humanoid poses, configurable local camera behavior, and per-animation damage policy. New animation sequences on the supported six-part humanoid rig do not require client-mod updates. Arbitrary custom rigs, meshes/textures, Forge/NeoForge, cross-version protocol negotiation, and robust compatibility patches for other animation mods require separate implementation and testing. Compilation and CI do not replace live in-game compatibility/smoothness testing; SceneSync does not promise perfect compatibility with every rendering or server plugin stack.

Report reproducible issues with the exact Minecraft, Paper, Fabric Loader/API, Java, and mod versions.
