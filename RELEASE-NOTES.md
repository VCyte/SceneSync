# SceneSync 0.1.0 development preview

Initial multi-module release candidate for Minecraft 26.2 / Java 25.

- Added `scenesync-api` immutable timeline types and Paper service contract.
- Added Paper plugin with YAML scene loading, `/scenesync` commands, server-controlled keyframe movement, bounded plugin messaging, and viewer fan-out.
- Added Fabric client protocol, server-time alignment, bounded pose timeline parsing, built-in player body poses, and local camera yaw/pitch/roll/shake.
- Added handshake session binding, packet-size/rate/nesting bounds, frame/coordinate/camera limits, and playback cleanup on disconnect, world change, stop, plugin disable, and unsafe destination.
- Added API tests, GitHub Actions CI, and tag-triggered release packaging.

This is a development preview, not a claim of universal modpack compatibility. Item appearance overrides, arbitrary animation assets, additional mod loaders, and in-game multiplayer acceptance testing remain future work. Review `docs/PLUGIN-DEVELOPMENT.md` and `docs/PROTOCOL.md` before integrating.
