# SceneSync Plugin Development

## Dependency

```gradle
repositories {
    maven {
        url = uri('https://maven.pkg.github.com/VCyte/SceneSync')
        credentials {
            username = System.getenv('GITHUB_ACTOR')
            password = System.getenv('GITHUB_TOKEN')
        }
    }
}

dependencies {
    compileOnly 'io.github.vcyte.scenesync:scenesync-api:0.2.0'
    compileOnly 'io.papermc.paper:paper-api:26.2.build.124-stable'
}
```

Download `scenesync-api-0.2.0.jar` from the matching SceneSync GitHub Release into your plugin project's `libs/` directory. For a local multi-project build, use `implementation project(':scenesync-api')`.

## Register and play a scene

The Paper plugin registers `SceneManager` with Bukkit's ServicesManager during enable:

```java
SceneManager manager = getServer().getServicesManager().load(SceneManager.class);
if (manager == null) throw new IllegalStateException("SceneSync is not installed");

SceneDefinition intro = new SceneDefinition("myplugin.intro", 40, List.of(
    new SceneFrame(0, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0),
    new SceneFrame(20, 0, 0, 2, 0, 0, "walk", 0, 0, 0, 0),
    new SceneFrame(40, 0, 0, 2, 0, 0, "wave", 0, 0, 0, 0)
));
manager.register(intro);
manager.play(player, intro.id());
```

All `SceneManager` calls must run on the Paper server thread. Register scenes during plugin enable and unregister them during disable if the registering plugin may reload independently. A later registration with the same ID replaces the previous API registration. API scenes override a configured scene with the same ID. There is a cap of 128 API scenes.

## Coordinates and time

Frame positions are **offsets in blocks from the actor's starting position, in the actor's starting facing basis**: positive z is forward and positive x is to the actor's right. The server and clients rotate those offsets into world coordinates from the synchronized origin yaw and evaluate x/y/z with the same shape-preserving cubic curve. This preserves authored keyframe positions while smoothing velocity at corners and at scene entry/exit. While a path segment moves, the actor faces its direction of travel; on stationary segments, the frame yaw is relative to the starting view. `pitch` is relative to the starting view. Timeline time is in server ticks (20 ticks/second), with a short shared countdown; keyframe ticks must strictly increase and the first must be 0. The actor's client mod sends ordinary player movement packets at the timeline position instead of having Paper teleport the player every tick. While a track is active, movement, jump, sprint, and shift inputs are suppressed locally. Paper checks movement against fractional points on the bounded-latency path window—not just integer server ticks—and sweeps the curve in sub-tick increments, through world safety and actor width. Unsafe paths, significant deviation, external teleport, or competing movement ends playback. Compatible clients render actor, armor, held items, and actor camera position from the same timeline. This avoids the server teleport-correction loop, but collision, protection plugins, latency, and other movement plugins can still affect playback.

## Camera control

Camera control is local to the **scene actor**; viewers retain their own camera. The scene-level `camera` setting defaults to `FREE_LOOK` for comfort and backwards compatibility:

- `FREE_LOOK`: preserve the actor's own mouse look. The client renders the local camera directly from the frame-rate scene sample, caches the vanilla third-person distance, and performs a short pivot obstruction trace; this avoids F5 judder caused by network-tick player position updates.
 Scene camera yaw/pitch offsets do not control the view.
- `LOCKED`: follow the actor's facing plus the keyframed `camera-yaw`/`camera-pitch` offsets. Mouse look is locked during the scene, with a 300 ms eased entry so the view does not snap at start.
- `LIMITED`: follow those same authored camera offsets while retaining mouse control inside a forward-facing cone. The sample config uses +/-55 degrees horizontally and +/-30 vertically; configure per scene as needed. The view is clamped relative to the actor's facing, excess mouse travel is discarded at the edge, and reversing direction responds immediately. Clamp bounds accept 0..100 horizontal and 0..70 vertical degrees.

Set `camera.mode`, `camera.max-yaw-degrees`, and `camera.max-pitch-degrees` under the scene in YAML, or pass `SceneCameraSettings` to the six-argument `SceneDefinition` constructor. In `LOCKED` and `LIMITED`, the actor's head pose follows the authored camera yaw/pitch offsets; keyframe offsets are interpolated over time. Camera roll is limited to +/-6 degrees and shake to a subtle maximum, while additive FOV remains bounded to +/-25 degrees. All these effects default to neutral. Camera control does not alter viewer cameras.

## Damage policy

Each scene selects a fallback `DamagePolicy`; the default for both YAML and the backwards-compatible API constructor is `CANCEL_SCENE`. Optional per-animation overrides take precedence while that animation is active:

- `CANCEL_SCENE`: on positive final damage, SceneSync sends the stop signal first, then allows normal Minecraft damage and its hurt animation to occur.
- `IGNORE_DAMAGE`: SceneSync cancels the damage event and leaves the scene running. Because damage is cancelled server-side, the actor does not lose health or receive the corresponding hurt reaction/knockback.

For YAML, set the fallback `damage-policy`, then optionally map animation IDs under `animation-damage-policies` (for example, `wave: IGNORE_DAMAGE`). Keys must be animations used by that scene; unlisted animations inherit the fallback. The policy switches at the same keyframe boundary as the animation. For API-created scenes, use the five-argument `SceneDefinition(id, duration, frames, fallbackPolicy, animationPolicyMap)` constructor; the three- and four-argument constructors remain and default to no per-animation overrides. Other plugins' already-cancelled damage events are left alone and do not stop the scene.

### Plugin-authored animations (no client update per animation)

Protocol v3 sends a bounded generic humanoid pose in each timeline frame. `animation` is now a namespaced label owned by your plugin (for example `myplugin:slash`), not a client-side animation enum. Supply a `ModelPose` with the frame; SceneSync's existing client mod validates, interpolates, and applies it. Your next animation can use a new label and different bone transforms without adding a client pose switch or publishing another mod build. Server and client must both use protocol v3 because the wire format changed.

```java
import io.github.vcyte.scenesync.api.BonePose;
import io.github.vcyte.scenesync.api.ModelPose;
import io.github.vcyte.scenesync.api.SceneDefinition;
import io.github.vcyte.scenesync.api.SceneFrame;

var windup = ModelPose.builder()
    .body(new BonePose(0, 0, 0, 20, 0, 0))
    .rightArm(new BonePose(0, 0, 0, -35, -20, -8))
    .build(); // unspecified bones stay neutral
var slash = ModelPose.builder()
    .body(new BonePose(0, 0, 0, 42, 0, 0))
    .rightArm(new BonePose(0, 0, 0, -125, -12, -25))
    .leftArm(new BonePose(0, 0, 0, 35, 8, 12))
    .leftLeg(new BonePose(0, 0, 0, -30, 0, 0))
    .rightLeg(new BonePose(0, 0, 0, 25, 0, 0))
    .build();

var scene = new SceneDefinition("myplugin_slash", 12, java.util.List.of(
    new SceneFrame(0, 0, 0, 0, 0, 0, "myplugin:windup", 0, 0, 0, 0, 0, windup),
    new SceneFrame(5, 0, 0, 0, 0, 0, "myplugin:slash", 0, 0, 0, 0, 0, slash),
    new SceneFrame(12, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0)
));
sceneManager.register(scene);
sceneManager.play(player, scene.id());
```

## Live dynamic scenes

For skills whose destination is calculated from a moving target, use `DynamicScene` instead of
pre-authoring all keyframes. The provider runs on the Paper main thread once per server tick and
returns one frame in the actor's **starting-position / starting-facing coordinate basis**. SceneSync
streams a bounded rolling window to compatible clients, interpolates between received frames, checks
each new segment for collision, and validates movement packets against the latest dynamic path.

```java
SceneManager manager = Bukkit.getServicesManager().load(SceneManager.class);
Player target = Bukkit.getPlayerExact("Target");

DynamicScene attack = new DynamicScene("myplugin:homing-strike", 30, context -> {
    Player actor = context.actor();
    Location origin = context.origin();
    Location targetLocation = target.getLocation();
    Vector toTarget = targetLocation.toVector().subtract(actor.getLocation().toVector());
    Vector behind = targetLocation.toVector().subtract(targetLocation.getDirection().normalize().multiply(2.0));
    Vector relative = behind.subtract(origin.toVector());

    // Convert world delta into the actor's starting-facing local basis.
    double radians = Math.toRadians(origin.getYaw());
    double x = relative.getX() * Math.cos(radians) + relative.getZ() * Math.sin(radians);
    double z = -relative.getX() * Math.sin(radians) + relative.getZ() * Math.cos(radians);
    float yaw = (float) Math.toDegrees(Math.atan2(-toTarget.getX(), toTarget.getZ())) - origin.getYaw();
    return new SceneFrame(context.tick(), x, relative.getY(), z, yaw, 0,
            "myplugin:homing_strike", 0, 0, 0, 0, 0, slashPose);
});

DynamicSceneHandle handle = manager.playDynamic(actor, attack);
// Later, from the Paper thread: handle.stop();
```

The provider must be fast and must return a frame whose `tick()` equals `context.tick()`.
Do not perform asynchronous Bukkit access or expensive pathfinding inside it; calculate expensive
data elsewhere and read a safe snapshot in the provider. A dynamic scene still has the normal
duration, camera, damage, coordinate, pose, packet-size, and collision bounds. If the provider
throws, returns an invalid frame, or the newly generated segment enters a blocked space, playback
stops safely and viewers receive the normal stop/fade signal.

Translations are bounded model-pixel offsets (each axis +/-8); rotations are degrees (+/-180). The client eases pose transitions, interpolates shortest-path Euler rotations, and returns to the current vanilla pose on scene end; early STOP and natural completion fade the rendered root to vanilla over 200 ms while movement/input control ends immediately. Frame yaw/pitch are offsets relative to the actor's initial facing and look pitch. Head overlays are additionally clamped after combining the animation and action-camera offsets (55° pitch, 65° yaw, 35° roll) relative to the vanilla look pose; oversized head keyframes are therefore safely limited at render time. Root facing eases into the first segment and across path corners. The six vanilla humanoid parts are head, body, arms and legs; their vanilla armor models and arm-attached held items inherit the transforms. This is a generic humanoid rig, not arbitrary mesh/bone/texture loading. Built-in convenience IDs (`idle`, `walk`, `run`, `wave`, `bow`, `spin`, `clap`, `sit`) remain optional legacy poses; new plugin motions should use namespaced IDs and provide pose data.

The Thunderclap sample is available as `/scenesync play thunderclap-flash [player]`: it charges for 16 ticks, then moves 24 blocks over 6 ticks (4 blocks per server tick at peak), holds a finishing pose, and eases camera/FOV accents around the dash. Its charge/dash/finish transforms use `scenesync:...` IDs and generic pose payloads—not client-side lightning code. It uses `LOCKED` camera control and is cancelled by damage. Paper preflights the full actor-specific curve, including body-width and sub-tick collision samples, before sending it. If a wall intersects the path, clients receive a shortened safe track with a brace/recovery pose; path packets are matched to the shared curve rather than compared to lag-dependent straight-line movement deltas. This is a visual scripted move, not a damage/hitbox/combat system.

In `FREE_LOOK`, head direction remains locked to the scene pose rather than live mouse aim; in camera-controlled modes, authored camera yaw/pitch offsets also turn the head when using legacy poses. `camera-fov` is an additive degree offset, bounded to +/-25 and clamped to 30..110; it defaults to 0. Camera yaw/pitch/roll/shake keyframes affect the actor's view only in `LOCKED` or `LIMITED` mode, with roll/shake comfort clamps.

## Commands

- `/scenesync list`
- `/scenesync status [player]`
- `/scenesync play <scene> [player]`
- `/scenesync stop [player]`
- `/scenesync reload`

Play, stop, and reload require `scenesync.admin`, defaulting to operators. Every actor whose client movement follows the scene needs the client mod handshake. Compatible viewers in the configured radius also receive the pose timeline; clients without the mod see ordinary server-synchronized movement only.

## Boundaries

The API does not currently model item appearance overrides or arbitrary animation assets; equipped items are not swapped or edited. The code and CI build verify input hooks and movement plumbing, but an in-game test is still required for packet behavior, smoothness, camera comfort, armor, and held items. Third-party mods can change the same player model/input fields, so load-order and renderer compatibility must be tested per supported modpack. SceneSync does not bypass server protection plugins or guarantee that another plugin will permit scripted movement. The server checks and can terminate the path; the client mod supplies each normal movement update to avoid forced teleports.
