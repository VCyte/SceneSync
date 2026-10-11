# SceneSync 0.1.1 corrective development preview

Minecraft 26.2 / Java 25 / Paper 26.2 / Fabric protocol v3. Upgrade the Paper plugin and Fabric client mod together.

## Correctness and motion fixes

- Fixed the main short-dash regression: Paper previously compared fractional-time client positions only with integer-tick path samples at a 0.75-block tolerance. Fast movement between server ticks could be rejected and the server stopped the scene mid-dash. Movement is now matched against fractional points on the server-authored path over a bounded lag window.
- Client and server now evaluate actor-local positions with the same shape-preserving cubic interpolation. Authored keyframe locations remain exact; velocity is eased at scene boundaries and smoothed across path segments. Paper samples the curve at sub-tick intervals and retains quarter-block collision sweeps plus actor-width checks.
- Extended the Thunderclap-and-Flash sample from 12 to 24 blocks over its six-tick dash segment; it remains subject to server collision and world-safety validation.
- Corrected the LIMITED camera's asymmetric offset bounds. Mouse overflow is discarded at the limit, so reversing the mouse responds immediately instead of fighting accumulated hidden input. The sample forward cone is +/-55 degrees yaw and +/-30 degrees pitch.
- Preserved vanilla camera mirroring when SceneSync overrides the rendered camera rotation, fixing third-person-front F5 orientation.
- Generic poses now ease in/out over the scene envelope and crossfade between authored keyframes. Legacy convenience animations also crossfade. Early STOP unlocks movement immediately while the frozen visual pose fades back to vanilla over 200 ms.
- Camera FOV, roll, and shake accents follow the same scene envelope to avoid snapping at entry/exit.

## Verification

`./gradlew clean test build` succeeds. Automated tests cover fractional path matching, actor-local/world conversion, off-path rejection, smooth boundary positions, keyframe crossfades, camera limit reversal, front-camera mirroring, and early-stop pose fading. The Thunderclap YAML sample parses with Paper's SnakeYAML 2.2.

This is still a development preview. These checks do not replace in-game testing with a live Paper server, Fabric client, real latency, F5 perspectives, and representative modpacks. See `docs/TESTING.md` for the manual acceptance matrix.
