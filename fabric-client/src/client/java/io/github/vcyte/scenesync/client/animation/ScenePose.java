package io.github.vcyte.scenesync.client.animation;

import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.model.HumanoidModel;

/** Applies one scene pose to player and separately rendered humanoid armor models. */
public final class ScenePose {
    private ScenePose() { }

    public static void apply(HumanoidModel<?> model, SceneTrack.Sample sample) {
        float phase = sample.ticks() * 0.35f;
        // Don't let the actor's live mouse aim make the head snap independently of the scene body.
        model.head.yRot = 0.0f;
        model.head.xRot = 0.0f;
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
}
