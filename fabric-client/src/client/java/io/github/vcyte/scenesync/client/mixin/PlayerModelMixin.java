package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void scenesync$applyScenePose(AvatarRenderState state, CallbackInfo ci) {
        SceneTrack.Sample sample = SceneController.sample(state.id, System.currentTimeMillis());
        if (sample == null) return;
        PlayerModel model = (PlayerModel)(Object)this;
        float phase = sample.ticks() * 0.35f;
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
            // idle and walk intentionally preserve the vanilla locomotion pose.
            default -> { }
        }
    }
}
