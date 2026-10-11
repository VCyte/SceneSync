package io.github.vcyte.scenesync.client.animation;

import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Applies one scene pose to player and separately rendered humanoid armor models. */
public final class ScenePose {
    private ScenePose() { }

    public static void apply(HumanoidModel<?> model, SceneTrack.Sample sample) {
        if (sample.pose() != null) {
            applyPose(model, sample.pose());
            applyCameraAim(model, sample);
            return;
        }
        float phase = sample.ticks() * 0.35f;
        // Keep head and scripted action-camera aim in sync. In free-look mode the scene pose owns
        // the head so unrelated mouse movement cannot make it snap independently of the body.
        model.head.yRot = 0.0f;
        model.head.xRot = 0.0f;
        applyCameraAim(model, sample);
        switch (sample.animation()) {
            case "wave" -> {
                model.rightArm.xRot = -1.55f + (float)Math.sin(phase * 2.0f) * 0.42f;
                model.rightArm.yRot = -0.18f;
            }
            case "bow" -> {
                model.body.xRot = 0.34f;
                model.head.xRot += 0.28f;
                model.rightArm.xRot = -0.45f;
                model.leftArm.xRot = -0.45f;
            }
            case "spin" -> model.body.yRot += (float)Math.sin(phase) * 0.45f;
            case "clap" -> {
                model.rightArm.xRot = -1.25f;
                model.leftArm.xRot = -1.25f;
                model.rightArm.yRot = -0.12f;
                model.leftArm.yRot = 0.12f;
            }
            case "sit" -> {
                model.rightLeg.xRot = -1.35f;
                model.leftLeg.xRot = -1.35f;
                model.rightLeg.yRot = 0.12f;
                model.leftLeg.yRot = -0.12f;
                model.rightArm.xRot = -0.45f;
                model.leftArm.xRot = -0.45f;
            }
            case "run" -> {
                model.body.xRot = 0.15f;
                model.rightArm.xRot -= 0.25f;
                model.leftArm.xRot -= 0.25f;
            }
            // idle and walk intentionally preserve vanilla locomotion.
            default -> { }
        }
    }

    private static void applyPose(HumanoidModel<?> model, SceneTrack.Pose pose) {
        apply(model.head, pose.head());
        apply(model.body, pose.body());
        apply(model.leftArm, pose.leftArm());
        apply(model.rightArm, pose.rightArm());
        apply(model.leftLeg, pose.leftLeg());
        apply(model.rightLeg, pose.rightLeg());
    }

    private static void apply(ModelPart part, SceneTrack.Bone pose) {
        part.x += pose.x();
        part.y += pose.y();
        part.z += pose.z();
        part.xRot += (float)Math.toRadians(pose.pitch());
        part.yRot += (float)Math.toRadians(pose.yaw());
        part.zRot += (float)Math.toRadians(pose.roll());
    }

    private static void applyCameraAim(HumanoidModel<?> model, SceneTrack.Sample sample) {
        if (sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK) return;
        float yaw = sample.cameraMode() == SceneTrack.CameraMode.LIMITED
                ? Math.max(-sample.maxYawDegrees(), Math.min(sample.maxYawDegrees(), sample.cameraYaw()))
                : sample.cameraYaw();
        float pitch = sample.cameraMode() == SceneTrack.CameraMode.LIMITED
                ? Math.max(-sample.maxPitchDegrees(), Math.min(sample.maxPitchDegrees(), sample.cameraPitch()))
                : sample.cameraPitch();
        model.head.yRot += (float)Math.toRadians(Math.max(-70.0f, Math.min(70.0f, yaw)));
        model.head.xRot += (float)Math.toRadians(Math.max(-55.0f, Math.min(55.0f, pitch)));
    }
}
