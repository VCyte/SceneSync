# Basic SceneSync API Example

A third-party Paper plugin can register a new animation ID and its skeletal poses through the API. The Fabric client only needs the generic SceneSync v3 mod; it does **not** need an update when `exampleplugin:slash` is introduced.

```java
import io.github.vcyte.scenesync.api.BonePose;
import io.github.vcyte.scenesync.api.ModelPose;
import io.github.vcyte.scenesync.api.SceneDefinition;
import io.github.vcyte.scenesync.api.SceneFrame;
import io.github.vcyte.scenesync.api.SceneManager;

var sceneManager = getServer().getServicesManager().load(SceneManager.class);
if (sceneManager == null) {
    getLogger().warning("SceneSync is not installed");
    return;
}

var windup = ModelPose.builder()
    .body(new BonePose(0, 0, 0, 20, 0, 0))
    .rightArm(new BonePose(0, 0, 0, -35, -20, -8))
    .build();
var strike = ModelPose.builder()
    .body(new BonePose(0, 0, 0, 42, 0, 0))
    .rightArm(new BonePose(0, 0, 0, -125, -12, -25))
    .leftArm(new BonePose(0, 0, 0, 35, 8, 12))
    .leftLeg(new BonePose(0, 0, 0, -30, 0, 0))
    .rightLeg(new BonePose(0, 0, 0, 25, 0, 0))
    .build();

var scene = new SceneDefinition("exampleplugin_slash", 12, java.util.List.of(
    new SceneFrame(0, 0, 0, 0, 0, 0, "exampleplugin:windup", 0, 0, 0, 0, 0, windup),
    new SceneFrame(5, 0, 0, 0, 0, 0, "exampleplugin:slash", 0, 0, 0, 0, 0, strike),
    new SceneFrame(12, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0)
));
sceneManager.register(scene);
sceneManager.play(player, scene.id());
```

`BonePose` translations are bounded model-pixel offsets; rotations are bounded degrees. Missing bones from the builder remain neutral. Frames are interpolated by the client, and the same humanoid transforms are applied to vanilla armor and held items attached to the arms. Add `compileOnly` dependencies on the SceneSync API and Paper API. See `docs/PLUGIN-DEVELOPMENT.md` for lifecycle, protocol limits, and safety details.
