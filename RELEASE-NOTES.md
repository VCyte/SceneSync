# SceneSync 0.3.12 actual loaded-chunk camera fallback

- Fixed the root cause of the void-camera fallback: `ClientLevel.hasChunk()` returns `true` in Minecraft 26.2 and cannot be used as a loaded-chunk test.
- FREE_LOOK and LIMITED now query `ClientChunkCache.getChunk(..., ChunkStatus.FULL, false)` and only place the camera in a chunk whose full data was actually received by the client.
- Removed the ineffective detached-camera `updateViewCenter()` call. In 26.2 it only changes two cache-center integers; it does not request chunks from the server and can make actor chunks appear out of range.
- The client now falls back toward the real player eye position whenever the scene pivot or third-person camera destination is not actually present in the client cache.

# SceneSync 0.3.11 detached-camera chunk cache center

- While FREE_LOOK or LIMITED uses a detached camera, the Fabric client now centers `ClientChunkCache` on the actual rendered camera chunk.
- This makes chunks that the server has already sent remain addressable when the camera moves away from the actor, instead of being treated as outside the client cache window and rendered as void.
- The change does not fabricate chunks or bypass server view distance; server-side streaming remains authoritative.
- The update uses the public 26.2 `ClientChunkCache.updateViewCenter(int, int)` method and does not use the crash-prone `@ModifyArgs` injection previously removed.

# SceneSync 0.3.10 loaded camera pivot and diagnostics

- FREE_LOOK first-person and detached third-person cameras now fall back toward the real interpolated eye position when the scene timeline pivot is in an unloaded chunk.
- Detached camera fallback probing increased from 8 to 16 steps.
- Added optional once-per-second diagnostics with `-Dscenesync.debugCamera=true`: scene pivot loading, desired camera loading, final camera-chunk loading, scene/entity distance and camera distance.
- Helps distinguish missing chunk streaming from camera rendering issues during fast dashes and high-latency playback.

# SceneSync 0.3.9 commit final first-person aim

- At scene end, first-person FREE_LOOK and LIMITED commit the independent camera's final yaw/pitch to the player body.
- Prevents the screen and body from snapping back to the pre-scene aim.
- The final LIMITED aim remains inside its configured cone before being committed.
- Animated head/body pose remains independent during playback and transitions cleanly to the final aim at the end.

# SceneSync 0.3.8 limited boundary clamp fix

- LIMITED camera input is now clamped on every mouse event against the current scene-relative cone.
- Excess input at the yaw/pitch edge is discarded instead of being accumulated and wrapped through 180/360 degrees.
- Reversing direction remains responsive from the exact boundary.
- Prevents F3/F5 LIMITED view from jumping to the opposite side.

# SceneSync 0.3.7 limited third-person camera fix

- LIMITED now uses a detached scene pivot and exact vanilla F3/F5 distance in third-person.
- Camera position and camera direction are calculated from the same constrained look, preventing front/back flips at the limit.
- Switching between rear and front third-person preserves the configured yaw/pitch cone.
- First-person LIMITED behavior from 0.3.6 remains unchanged.

# SceneSync 0.3.6 limited camera separation

- LIMITED now uses the same independent camera look accumulator in first- and third-person.
- Mouse movement is clamped to the configured yaw/pitch cone without changing the actor rotation.
- Authored head/body/limb animation remains independent from the LIMITED camera.
- Movement, jump, sprint and sneak remain suppressed during playback.

# SceneSync 0.3.5 wall collision and first-person camera fix

- Translated Bukkit voxel collision boxes from block-local coordinates into world coordinates before path checks; walls and solid blocks can no longer be bypassed due to coordinate mismatch.
- Runtime sub-tick path validation now uses the corrected world collision boxes before each authoritative movement.
- FREE_LOOK first-person now uses the independent camera look accumulator and applies camera rotation at the scene pivot.
- Mouse rotation remains camera-only in FREE_LOOK while the animated actor head stays independent.

# SceneSync 0.3.4 physics collision-shape fix

- Collision planning now uses Bukkit's actual block collision shapes instead of generic block bounding boxes.
- Non-solid, visual-only and interaction-only blocks no longer falsely stop scene playback.
- Support geometry below the player's feet is excluded from obstruction checks, preserving carpets, slabs, dirt paths and stair treads.
- Walls, fences, ceilings and collision shapes that rise into the player's body remain blocked.

# SceneSync 0.3.3 separate camera and animated head

- Restored complete movement-input suppression during every scene mode: WASD, jump, sprint and sneak no longer move the actor.
- FREE_LOOK and LIMITED now move only the camera view; the actor head is no longer driven by mouse/camera yaw or pitch.
- Authored head/body/limb animation remains independent of the camera in first- and third-person views.
- LOCKED retains authored action-camera behavior.

# SceneSync 0.3.2 camera input and collision-shape fix

- FREE_LOOK and LIMITED now preserve normal movement, jump, sprint and sneak input; LOCKED remains the only mode that suppresses the complete controller.
- First-person FREE_LOOK keeps mouse movement available while the animated head/body timeline remains active.
- Replaced block occupancy checks with real Bukkit collision-box overlap checks.
- Slabs, carpets, dirt paths, stairs and other partial-height floor blocks no longer falsely block the authored path; fences and full-height obstructions remain blocked.

# SceneSync 0.3.1 timing and start synchronization

- Added a 150 ms synchronized start lead so the actor and viewers receive frame zero before playback begins.
- Server-authoritative static movement now samples fractional timeline ticks instead of repeating or skipping whole-tick positions.
- Dynamic server movement now interpolates between generated frames for time-proportional speed.
- Retains the v0.3.0 removal of client-generated scene movement packets and Paper authority.

# SceneSync 0.3.0 server-authoritative movement

- Removed the client `sendPosition()` scene spoof. SceneSync no longer manufactures movement packets from the Fabric client.
- Paper applies the authored scene transform once per server tick and remains the authority for collision, gravity suppression during playback, and the final actor position.
- Client movement packets are ignored only while a scene is active; normal movement resumes immediately after stop.
- This removes the `moved too quickly` warnings caused by 18–24 block client packet deltas and prevents client/server physics disagreement.
- Preserved frame-rate scene rendering and loaded-chunk camera fallback from 0.2.0.

# SceneSync 0.2.0 smooth local playback and unloaded-camera safety

- Restore the local player's real physical position immediately after the scene position is used to build a movement packet; gravity and momentum no longer visibly fight the animation.
- Remove the second render-time root filter that added latency and caused F5 judder and an end-of-scene snap.
- Keep the detached camera inside loaded chunks when the server has not streamed the rear camera chunk yet, avoiding full void-chunk rendering; the camera returns to full distance as chunks become available.
- Removed the unstable ClientChunkCache view-center mixin entirely; vanilla/server-authoritative chunk streaming remains intact.

# SceneSync 0.1.9 Minecraft 26.2 crash fix

- Removed the invalid `@ModifyArgs(at = HEAD)` injection from `ClientChunkCacheMixin`; `@ModifyArgs` only targets invocation instructions and caused `InvalidInjectionException` during login.
- Replaced it with two bounded `@ModifyVariable(argsOnly = true)` injections targeting the actual `updateViewCenter(int, int)` arguments.
- Preserved FREE_LOOK camera-centered chunk-cache behavior without breaking client connection initialization.

# SceneSync 0.1.8 detached-camera chunk-cache release

- Keep the client chunk-cache view center aligned with the active FREE_LOOK render camera.
- Prevent already received rear-camera chunks from being discarded solely because the local player remains the scene actor anchor.
- Retain the server-to-client prepare acknowledgement start path and exact vanilla F5 eye-pivot distance from 0.1.7.
- This does not fabricate chunks the server has not sent; server view distance and chunk streaming remain authoritative.

# SceneSync 0.1.7 synchronized-start and camera maintenance release

- Replaced the fixed start delay with a server-to-client prepare acknowledgement and immediate server-authoritative start timestamp.
- The actor is no longer frozen while waiting for synchronization; the final origin is captured after the client confirms receipt, preventing fall-and-rollback teleports.
- F5 FREE_LOOK distance is measured from the vanilla eye pivot, removing the subtle extra distance introduced by measuring from the player feet.
- Added bounded prepare timeout and session/run/entity validation for the acknowledgement path.

# SceneSync 0.1.6 fall-start continuity release

- Freeze vanilla falling velocity during the synchronized start lead.
- Clamp the client pre-roll sample to frame 0 so an airborne player no longer falls and then snaps upward to the captured scene origin.
- Keep scene effects blended from zero while the frame-0 position remains stable, preserving a smooth authored start.
- Added a regression test for pre-roll position continuity.

# SceneSync 0.1.5 camera rendering maintenance release

- FREE_LOOK third-person rendering now anchors the camera directly to the frame-rate scene sample instead of the last network-tick player position.
- Cached vanilla third-person distance prevents F5 camera distance jitter while the actor follows a smooth scene path.
- Camera obstruction is ray-tested from the interpolated scene pivot, keeping the camera out of blocks during fast movement and turns.
- The local actor camera now follows the same smooth render timeline that compatible viewers use; server movement packets remain authoritative and unchanged.

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
