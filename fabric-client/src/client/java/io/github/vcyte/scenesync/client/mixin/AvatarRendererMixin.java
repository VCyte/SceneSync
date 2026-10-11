package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void scenesync$interpolateSceneRender(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        SceneTrack.Sample sample = SceneController.sample(state.id, System.currentTimeMillis());
        if (sample == null) return;
        state.x = sample.worldX();
        state.y = sample.worldY();
        state.z = sample.worldZ();
        state.yRot = sample.yaw();
        state.bodyRot = sample.yaw();
        state.xRot = sample.pitch();
    }
}
