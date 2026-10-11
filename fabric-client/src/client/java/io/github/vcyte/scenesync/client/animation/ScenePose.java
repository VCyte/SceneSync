package io.github.vcyte.scenesync.client.animation;

import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;

/** Applies one scene pose to player and separately rendered humanoid armor models. */
public final class ScenePose {
    private ScenePose() { }

    public static void apply(HumanoidModel<?> model, SceneTrack.Sample sample) {
        float vanillaHeadPitch = model.head.xRot;
        float vanillaHeadYaw = model.head.yRot;
        float vanillaHeadRoll = model.head.zRot;
        float envelope = clamp(sample.sceneBlend(), 0.0f, 1.0f);
        if (sample.pose() != null) applyPose(model, sample.pose(), envelope);

        float transition = clamp(sample.animationBlend(), 0.0f, 1.0f);
        boolean sameAnimation = sample.animation().equals(sample.nextAnimation());
        float firstWeight = envelope * (sameAnimation ? 1.0f : 1.0f - transition);
        float nextWeight = envelope * (sameAnimation ? 0.0f : transition);
        if (!sample.fromHasPose()) applyLegacyPose(model, sample.animation(), sample.ticks(), firstWeight);
        if (!sample.toHasPose()) applyLegacyPose(model, sample.nextAnimation(), sample.ticks(), nextWeight);

        // Vanilla setupAnim has already produced the base pose. Script transforms are overlays,
        // then the action camera adds only its own head aim; each weight returns to that base pose.
        applyCameraAim(model, sample, envelope);
        // A plugin head pose and the action-camera offset are both overlays. Clamp their sum,
        // rather than each independently, so they cannot combine into a neck flip.
        model.head.xRot = HeadPoseMath.clampOverlay(vanillaHeadPitch, model.head.xRot, HeadPoseMath.MAX_PITCH_RADIANS);
        model.head.yRot = HeadPoseMath.clampOverlay(vanillaHeadYaw, model.head.yRot, HeadPoseMath.MAX_YAW_RADIANS);
        model.head.zRot = HeadPoseMath.clampOverlay(vanillaHeadRoll, model.head.zRot, HeadPoseMath.MAX_ROLL_RADIANS);
    }

    private static void applyLegacyPose(HumanoidModel<?> model, String animation, float ticks, float weight) {
        if (weight <= 0.0f) return;
        float phase = ticks * 0.35f;
        switch (animation) {
            case "wave" -> {
                model.rightArm.xRot += (-1.55f + (float)Math.sin(phase * 2.0f) * 0.42f) * weight;
                model.rightArm.yRot -= 0.18f * weight;
            }
            case "bow" -> {
                model.body.xRot += 0.34f * weight;
                model.head.xRot += 0.28f * weight;
                model.rightArm.xRot -= 0.45f * weight;
                model.leftArm.xRot -= 0.45f * weight;
            }
            case "spin" -> model.body.yRot += (float)Math.sin(phase) * 0.45f * weight;
            case "clap" -> {
                model.rightArm.xRot -= 1.25f * weight;
                model.leftArm.xRot -= 1.25f * weight;
                model.rightArm.yRot -= 0.12f * weight;
                model.leftArm.yRot += 0.12f * weight;
            }
            case "sit" -> {
                model.rightLeg.xRot -= 1.35f * weight;
                model.leftLeg.xRot -= 1.35f * weight;
                model.rightLeg.yRot += 0.12f * weight;
                model.leftLeg.yRot -= 0.12f * weight;
                model.rightArm.xRot -= 0.45f * weight;
                model.leftArm.xRot -= 0.45f * weight;
            }
            case "run" -> {
                model.body.xRot += 0.15f * weight;
                model.rightArm.xRot -= 0.25f * weight;
                model.leftArm.xRot -= 0.25f * weight;
            }
            // idle and walk intentionally preserve vanilla locomotion.
            default -> { }
        }
    }

    private static void applyPose(HumanoidModel<?> model, SceneTrack.Pose pose, float weight) {
        apply(model.head, pose.head(), weight);
        apply(model.body, pose.body(), weight);
        apply(model.leftArm, pose.leftArm(), weight);
        apply(model.rightArm, pose.rightArm(), weight);
        apply(model.leftLeg, pose.leftLeg(), weight);
        apply(model.rightLeg, pose.rightLeg(), weight);
    }

    private static void apply(ModelPart part, SceneTrack.Bone pose, float weight) {
        part.x += pose.x() * weight;
        part.y += pose.y() * weight;
        part.z += pose.z() * weight;
        part.xRot += (float)Math.toRadians(pose.pitch()) * weight;
        part.yRot += (float)Math.toRadians(pose.yaw()) * weight;
        part.zRot += (float)Math.toRadians(pose.roll()) * weight;
    }

    private static void applyCameraAim(HumanoidModel<?> model, SceneTrack.Sample sample, float weight) {
        if (sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK) return;
        float yaw = sample.cameraMode() == SceneTrack.CameraMode.LIMITED
                ? Math.max(-sample.maxYawDegrees(), Math.min(sample.maxYawDegrees(), sample.cameraYaw()))
                : sample.cameraYaw();
        float pitch = sample.cameraMode() == SceneTrack.CameraMode.LIMITED
                ? Math.max(-sample.maxPitchDegrees(), Math.min(sample.maxPitchDegrees(), sample.cameraPitch()))
                : sample.cameraPitch();
        model.head.yRot += (float)Math.toRadians(Math.max(-70.0f, Math.min(70.0f, yaw))) * weight;
        model.head.xRot += (float)Math.toRadians(Math.max(-55.0f, Math.min(55.0f, pitch))) * weight;
    }

    private static float clamp(float value, float min, float max) { return Math.max(min, Math.min(max, value)); }
}
