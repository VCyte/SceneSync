# SceneSync

**Server-authored cinematic scenes for Minecraft: synchronized character motion, animation, and local camera effects.**

SceneSync is a separate project from SKM. A Paper plugin owns scene definitions, scheduling, and path/collision validation. The actor's Fabric client follows the shared timeline using ordinary movement packets rather than a server teleport every tick; compatible viewers render the actor, worn armor, held items, and animation from that same timeline. Movement, jump, sprint, and shift inputs are blocked locally during playback, while mouse look remains free. Paths use the actor's initial facing basis and moving actors face their travel direction. Plugins can register scenes through a small Java API.

> **Current status: 0.1.0 development preview.** This is a buildable foundation, not a claim of universal mod compatibility or production readiness. The initial target is Minecraft 26.2, Java 25, Paper 26.2, Fabric Loader 0.19.5+, and Fabric API 0.161.0+26.2. Every viewer who should see custom poses or camera effects needs the client mod. Vanilla clients still receive ordinary server movement but cannot render SceneSync-specific animation or camera effects.

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

- `paper-plugin/build/libs/SceneSync-Paper-0.1.0.jar`
- `fabric-client/build/libs/scenesync-client-26.2-0.1.0.jar`
- `scenesync-api/build/libs/scenesync-api-0.1.0.jar`

## Install

1. Install the Paper plugin on the server.
2. Install the matching protocol-v2 Fabric client mod on every player who should act in scenes or see SceneSync visuals.
3. Restart the server and client, then use `/scenesync help`.

The plugin has a `demo` scene and a `thunderclap-flash` example in `paper-plugin/src/main/resources/config.yml`. Start it with `/scenesync play thunderclap-flash [player]`; only operators can start or stop scenes by default. The example charges for 16 ticks, locks the actor's view, dashes 12 blocks over 6 ticks, then holds a finishing pose. If you already have `plugins/SceneSync/config.yml`, add/copy the new scene there before `/scenesync reload`—existing configs are not overwritten.

## Design and safety

- The server validates scene paths and collision safety; fast segments are sampled at <=0.25-block intervals with body-width checks, and unsafe movement events are cancelled. The actor mod sends normal movement packets from the timeline. Significant divergence or unsafe paths stop playback instead of causing a teleport-correction loop.
- Each scene can preserve free look, smoothly lock to a keyframed action camera, or constrain mouse look to a forward cone (default limits: +/-80° yaw and +/-45° pitch). Camera yaw/pitch keyframes move the actor's view and authored head pose together; only the actor's camera is controlled, never viewers' cameras. Roll and shake are optional and tightly clamped; FOV is bounded and defaults to neutral.
- Scene damage behavior is configurable per animation: the scene fallback is `CANCEL_SCENE` by default, and entries such as `wave: IGNORE_DAMAGE` can override individual animation IDs.
- Network payloads are versioned, size-bounded, parsed with strict bounds, and rate-limited. Unsupported protocol versions are rejected.
- Scene duration, frame count, coordinates, additive FOV offsets, and animation identifiers have explicit limits.
- Starting a scene never writes to player inventory. Cosmetic equipment replacement and cross-mod animation compatibility are not yet advertised as complete features.
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

Initial scope is player actors, keyframed client-driven normal movement packets with Paper path/collision validation, shared-time client render interpolation, synchronized built-in body poses (including a charge/dash/finish sword example), configurable local camera behavior, and per-animation damage policy. This build has passed compilation and CI; a live in-game smoothness/compatibility acceptance test is still required. Arbitrary third-party animation packs, Forge/NeoForge, cross-version protocol negotiation, and robust compatibility patches for other animation mods require separate implementation and testing. SceneSync deliberately does not promise perfect compatibility with every rendering or server plugin stack.

Report reproducible issues with the exact Minecraft, Paper, Fabric Loader/API, Java, and mod versions.
