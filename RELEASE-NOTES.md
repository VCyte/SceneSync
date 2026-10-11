# SceneSync 0.1.0 development preview

Initial multi-module release candidate for Minecraft 26.2 / Java 25.

Latest main-branch improvements: protocol v2 replaces per-tick server teleports with client-sent normal movement updates, which Paper validates against the bounded scene path and safe-world checks. Fast segments are swept at <=0.25-block intervals with body-width samples, and unsafe movement events are cancelled. The actor's client locally suppresses movement/jump/sprint/shift inputs during the track. Scenes can preserve free look, lock smoothly to a keyframed camera, or constrain mouse look to a forward cone; actor-relative camera yaw/pitch keyframes drive the actor's view and authored head pose together. Roll and shake are tightly clamped and FOV offsets ease between keyframes. Actor-local coordinates follow the starting facing basis, and viewers share the same interpolated motion timeline while retaining their own cameras. A matching v2 Paper plugin and Fabric mod are both required.

Damage response has a scene fallback (`CANCEL_SCENE` by default) plus per-animation overrides such as `wave: IGNORE_DAMAGE`, switched at the matching animation keyframe. `CANCEL_SCENE` stops the track before the hit proceeds; `IGNORE_DAMAGE` cancels the damage event and keeps the scene running without a hurt reaction. Local camera tracking now offsets from Minecraft's partial-tick-interpolated actor position to avoid mixing render and simulation coordinates.

- Added `scenesync-api` immutable timeline types and Paper service contract.
- Added Paper plugin with YAML scene loading, `/scenesync` commands, path validation without per-tick teleports, bounded plugin messaging, and viewer fan-out.
- Added Fabric client protocol, server-time alignment, bounded pose timeline parsing, built-in player body poses including `lightning_charge`/`lightning_dash`/`lightning_finish`, local movement input suppression, and FREE_LOOK/LOCKED/LIMITED actor camera modes.
- Added handshake session binding, packet-size/rate/nesting bounds, frame/coordinate/FOV limits, and playback cleanup on disconnect, world change, stop, plugin disable, unsafe path, or movement deviation.
- Added API tests, GitHub Actions CI, and tag-triggered release packaging.

This is a development preview, not a claim of universal modpack compatibility. Item appearance overrides, arbitrary animation assets, additional mod loaders, and in-game multiplayer acceptance testing remain future work. Review `docs/PLUGIN-DEVELOPMENT.md` and `docs/PROTOCOL.md` before integrating.
