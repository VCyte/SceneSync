package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import io.github.vcyte.scenesync.client.animation.ScenePose;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HumanoidModel.class)
public abstract class HumanoidModelMixin {
    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/HumanoidRenderState;)V", at = @At("TAIL"))
    private void scenesync$applyScenePose(HumanoidRenderState state, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState avatarState)) return;
        SceneTrack.Sample sample = SceneController.sample(avatarState.id, System.currentTimeMillis());
        if (sample != null) ScenePose.apply((HumanoidModel<?>)(Object)this, sample);
    }
}
