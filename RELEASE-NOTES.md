# SceneSync 0.1.2 corrective development preview

Minecraft 26.2 / Java 25 / Paper 26.2 / Fabric protocol v3. Replace the Paper plugin and Fabric client mod together.

## Visual and movement corrections

- Scripted head-bone rotations and camera aim were accumulating independently. The renderer now clamps their combined overlay relative to the vanilla head pose: at most 55° pitch, 65° yaw, and 35° roll. Normal vanilla head look is preserved; extreme custom keyframes cannot turn into a neck flip.
- Different movement packet timing could trigger a false stop: Paper checked the straight line from its possibly delayed `from` location to the current `to` location, including a 16-block maximum. That segment does not describe the shared cubic scene curve. Scene packets are now validated against the planned timeline; path safety is checked on the scene curve itself.
- Paper now checks an actor's entire fixed path against collision geometry before broadcasting the play packet. When a static obstruction is found, it publishes a per-actor safe-prefix track that holds before the wall, braces, and returns toward a neutral pose. The client is no longer instructed to dash through a known wall and then corrected backward. Viewers who join late now receive the original anchored origin as well.
- The existing runtime world/path checks remain as a guard against a route becoming unsafe after startup.

## Verification

This patch adds deterministic tests for combined head-angle bounds, last-safe path holding, and brace/recovery timeline construction. Run `./gradlew --no-daemon clean test build` before distribution. Automated checks cannot replace live testing of Paper movement events, different ping/TPS conditions, actual block collision shapes, head rendering, and third-party mods; see `docs/TESTING.md` for that acceptance matrix.
