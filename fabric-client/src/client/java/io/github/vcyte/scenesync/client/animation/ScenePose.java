package io.github.vcyte.scenesync.client.animation;

import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.model.HumanoidModel;

/** Applies one scene pose to player and separately rendered humanoid armor models. */
public final class ScenePose {
    private ScenePose() { }

    public static void apply(HumanoidModel<?> model, SceneTrack.Sample sample) {
        float phase = sample.ticks() * 0.35f;
        // Keep head and scripted action-camera aim in sync. In free-look mode the scene pose owns
        // the head so unrelated mouse movement cannot make it snap independently of the body.
        float headYaw = sample.cameraMode() == SceneTrack.CameraMode.LIMITED
                ? Math.max(-sample.maxYawDegrees(), Math.min(sample.maxYawDegrees(), sample.cameraYaw()))
                : sample.cameraYaw();
        float headPitch = sample.cameraMode() == SceneTrack.CameraMode.LIMITED
                ? Math.max(-sample.maxPitchDegrees(), Math.min(sample.maxPitchDegrees(), sample.cameraPitch()))
                : sample.cameraPitch();
        model.head.yRot = sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK ? 0.0f
                : (float)Math.toRadians(Math.max(-70.0f, Math.min(70.0f, headYaw)));
        model.head.xRot = sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK ? 0.0f
                : (float)Math.toRadians(Math.max(-55.0f, Math.min(55.0f, headPitch)));
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
            case "lightning_charge" -> {
                float pulse = (float)Math.sin(phase * 0.55f) * 0.035f;
                model.body.xRot = 0.42f + pulse;
                model.rightArm.xRot = -0.55f + pulse;
                model.rightArm.yRot = -0.34f;
                model.rightArm.zRot = -0.12f;
                model.leftArm.xRot = -0.42f - pulse;
                model.leftArm.yRot = 0.34f;
                model.leftArm.zRot = 0.12f;
                model.rightLeg.xRot = 0.16f;
                model.leftLeg.xRot = 0.16f;
            }
            case "lightning_dash" -> {
                float pulse = (float)Math.sin(phase * 4.0f) * 0.04f;
                model.body.xRot = 0.58f + pulse;
                model.rightArm.xRot = -1.42f + pulse;
                model.rightArm.yRot = -0.18f;
                model.rightArm.zRot = -0.08f;
                model.leftArm.xRot = 0.78f - pulse;
                model.leftArm.yRot = 0.12f;
                model.leftLeg.xRot = -0.74f - pulse;
                model.rightLeg.xRot = 0.62f + pulse;
            }
            case "lightning_finish" -> {
                model.body.xRot = 0.20f;
                model.rightArm.xRot = -1.72f;
                model.rightArm.yRot = -0.22f;
                model.leftArm.xRot = -0.18f;
                model.leftLeg.xRot = -0.12f;
                model.rightLeg.xRot = 0.12f;
            }
            // idle and walk intentionally preserve vanilla locomotion.
            default -> { }
        }
    }
}
