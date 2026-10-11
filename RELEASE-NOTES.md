# SceneSync 0.1.0 development preview

Initial multi-module release candidate for Minecraft 26.2 / Java 25.

Latest main-branch improvements: protocol v3 adds bounded generic six-part humanoid pose keyframes supplied by the server plugin. Plugin-owned namespaced animation IDs and new sequences on this rig no longer require a client mod update; only additions to the rig/renderer itself do. Protocol v3 also retains client-sent normal movement updates instead of per-tick server teleports, with Paper path checks and fast-segment sweeps at <=0.25-block intervals, body-width samples, and cancellation of unsafe movement events. The actor's client locally suppresses movement/jump/sprint/shift inputs during the track. Scenes can preserve free look, lock smoothly to a keyframed camera, or constrain mouse look to a forward cone; camera yaw/pitch keyframes drive actor view behavior. Roll/shake are tightly clamped and FOV offsets ease between keyframes. Actor-local coordinates follow starting facing; viewers share the interpolated timeline while retaining their own cameras. A matching v3 Paper plugin and Fabric mod are required.

Damage response has a scene fallback (`CANCEL_SCENE` by default) plus per-animation overrides such as `wave: IGNORE_DAMAGE`, switched at the matching animation keyframe. `CANCEL_SCENE` stops the track before the hit proceeds; `IGNORE_DAMAGE` cancels the damage event and keeps the scene running without a hurt reaction. Local camera tracking now offsets from Minecraft's partial-tick-interpolated actor position to avoid mixing render and simulation coordinates.

- Added `scenesync-api` immutable timeline types and Paper service contract.
- Added Paper plugin with YAML scene loading, `/scenesync` commands, path validation without per-tick teleports, bounded plugin messaging, viewer fan-out, and generic pose serialization.
- Added Fabric client protocol, server-time alignment, bounded generic pose timeline parsing/interpolation, legacy convenience poses, local movement input suppression, and FREE_LOOK/LOCKED/LIMITED actor camera modes.
- Added a Thunderclap-and-Flash YAML sample whose charge/dash/finish transforms use only plugin-namespaced IDs and server-supplied bone keyframes.
- Added handshake session binding, packet-size/rate/nesting bounds, frame/coordinate/FOV limits, and playback cleanup on disconnect, world change, stop, plugin disable, unsafe path, or movement deviation.
- Added API tests, GitHub Actions CI, and tag-triggered release packaging.

This is a development preview, not a claim of universal modpack compatibility. Item appearance overrides, arbitrary animation assets, additional mod loaders, and in-game multiplayer acceptance testing remain future work. Review `docs/PLUGIN-DEVELOPMENT.md` and `docs/PROTOCOL.md` before integrating.
