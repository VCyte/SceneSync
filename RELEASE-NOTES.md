# SceneSync 0.1.4 security and packaging maintenance release

- Fixed release workflows that referenced obsolete 0.1.0 artifact names.
- Pinned GitHub Actions and Gradle wrapper distribution verification.
- Added GitHub Packages Maven publication for `io.github.vcyte.scenesync:scenesync-api`.
- Documented Maven dependency usage for Paper plugin authors.

# SceneSync 0.1.3 corrective development preview

Minecraft 26.2 / Java 25 / Paper 26.2 / Fabric protocol v3. Replace the Paper plugin and Fabric client mod together.

## Start, facing, and handoff fixes

- **Found a concrete start-of-scene head snap:** play packets carried origin yaw but not origin pitch. The renderer then used a neutral keyframe pitch of zero as an absolute pitch, forcing a player who was looking up or down toward horizontal. The packet now carries `originPitch`; frame pitch is applied as an offset from that initial look. Older protocol-v3 servers that omit the field remain accepted and default it to zero.
- **Removed the avoidable 500 ms countdown.** Scene playback now uses a 100 ms shared start lead. Clients send up to five join-time clock probes and choose the lowest-RTT NTP-style offset estimate; the server returns receive and send timestamps. Previous protocol-v3 handshake responses still work through a fallback.
- Root facing now eases into the initial path direction and interpolates over a short window around direction-changing keyframes, rather than changing body yaw exactly at a keyframe. Existing cubic position and skeletal-pose easing remain in place.
- The render-state mixin used to stop overriding position and rotation at track end in one frame. Early stop and natural completion now blend the final sampled root position, body/head facing, and pitch to vanilla entity state over the existing 200 ms visual tail. Input and scene movement control still end immediately; large teleports/world changes are not blended across.

## Verification and limitation

New tests cover starting-pitch preservation, eased root direction changes, clock offset math and sample selection, and natural-completion visual handoff. Run `./gradlew --no-daemon clean test build` before distribution. Automated tests cannot verify real camera/head rendering, multi-client latency, Paper corrections, or third-party mixin interactions. Please validate in-game with several initial view pitches and client RTTs; see `docs/TESTING.md`.
