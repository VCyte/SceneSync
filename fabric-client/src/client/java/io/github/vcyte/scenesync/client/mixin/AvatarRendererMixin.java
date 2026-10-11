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
        long now = System.currentTimeMillis();
        SceneTrack.Sample sample = SceneController.sample(state.id, now);
        if (sample != null) {
            state.x = sample.worldX();
            state.y = sample.worldY();
            state.z = sample.worldZ();
            state.yRot = sample.yaw();
            state.bodyRot = sample.yaw();
            state.xRot = sample.pitch();
            return;
        }
        SceneController.VisualFade fade = SceneController.visualFade(state.id, now);
        if (fade == null) return;
        SceneTrack.Sample last = fade.sample();
        double dx = last.worldX() - state.x, dy = last.worldY() - state.y, dz = last.worldZ() - state.z;
        // Do not interpolate across a server teleport or world change.
        if (dx * dx + dy * dy + dz * dz > 9.0) return;
        float weight = fade.rootWeight();
        state.x += dx * weight;
        state.y += dy * weight;
        state.z += dz * weight;
        state.yRot = lerpAngle(state.yRot, last.yaw(), weight);
        state.bodyRot = lerpAngle(state.bodyRot, last.yaw(), weight);
        state.xRot += (last.pitch() - state.xRot) * weight;
    }

    private static float lerpAngle(float from, float to, float amount) {
        float delta = (to - from) % 360.0f;
        if (delta > 180.0f) delta -= 360.0f;
        if (delta < -180.0f) delta += 360.0f;
        return from + delta * amount;
    }
}
