# SceneSync

**Server-authored cinematic scenes for Minecraft: synchronized character motion, animation, and local camera effects.**

SceneSync is a separate project from SKM. A Paper plugin owns scene definitions, actor selection, timing, and server-authoritative movement. A Fabric client mod renders custom player poses and applies camera motion for the local viewer. Plugins can register scenes through a small Java API.

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
2. Install the Fabric client mod on every player who needs SceneSync visuals.
3. Restart the server and client, then use `/scenesync help`.

The plugin has a `demo` scene in `paper-plugin/src/main/resources/config.yml`. Only operators can start or stop scenes by default.

## Design and safety

- The server validates and owns all scene definitions and real actor position changes; client packets are visual instructions, not authorization to perform gameplay actions.
- Network payloads are versioned, size-bounded, parsed with strict bounds, and rate-limited. Unsupported protocol versions are rejected.
- Scene duration, frame count, coordinates, camera angles, and animation identifiers have explicit limits.
- Starting a scene never writes to player inventory. Cosmetic equipment replacement and cross-mod animation compatibility are not yet advertised as complete features.
- Disconnect, world change, plugin disable, and explicit stop terminate active playback and restore the actor's prior movement state.

## Plugin API sketch

```java
SceneManager scenes = (SceneManager) Bukkit.getServicesManager()
    .load(SceneManager.class);
scenes.register(sceneDefinition);
scenes.play(player, "intro");
```

See `docs/PLUGIN-DEVELOPMENT.md` for the exact current API and limitations.

## Compatibility and roadmap

Initial scope is player actors, keyframed server-side position/orientation, synchronized built-in body poses, and camera effects for the local player. Entity and item rendering overrides, arbitrary third-party animation packs, Forge/NeoForge, cross-version protocol negotiation, and robust compatibility patches for other animation mods require separate implementation and testing. SceneSync deliberately does not promise perfect compatibility with every rendering or server plugin stack.

Report reproducible issues with the exact Minecraft, Paper, Fabric Loader/API, Java, and mod versions.
