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
            // LivingEntityRenderState.yRot is the head yaw relative to bodyRot.  It is
            // not an absolute yaw. Writing the absolute scene yaw to both fields makes
            // the head turn twice (the classic south/north and 90-degree east/west bug).
            // The scene root owns the body direction; pose/camera code owns head offsets.
            float startT = Math.max(0.0f, Math.min(1.0f, sample.ticks() / 3.0f));
            float rootWeight = startT * startT * (3.0f - 2.0f * startT);
            float vanillaBodyYaw = state.bodyRot;
            float vanillaHeadRelativeYaw = state.yRot;
            float targetHeadRelativeYaw = sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK
                    ? wrapDegrees(vanillaBodyYaw + vanillaHeadRelativeYaw - sample.yaw()) : 0.0f;
            state.x = lerp(state.x, sample.worldX(), rootWeight);
            state.y = lerp(state.y, sample.worldY(), rootWeight);
            state.z = lerp(state.z, sample.worldZ(), rootWeight);
            state.bodyRot = lerpAngle(state.bodyRot, sample.yaw(), rootWeight);
            state.yRot = lerpAngle(state.yRot, targetHeadRelativeYaw, rootWeight);
            state.xRot += (sample.pitch() - state.xRot) * rootWeight;
            return;
        }
        SceneController.VisualFade fade = SceneController.visualFade(state.id, now);
        if (fade == null) return;
        SceneTrack.Sample last = fade.sample();
        double dx = last.worldX() - state.x, dy = last.worldY() - state.y, dz = last.worldZ() - state.z;
        // Do not interpolate across a server teleport or world change.
        if (dx * dx + dy * dy + dz * dz > 9.0) return;
        float weight = fade.rootWeight();
        float vanillaBodyYaw = state.bodyRot;
        float vanillaHeadRelativeYaw = state.yRot;
        float targetHeadRelativeYaw = last.cameraMode() == SceneTrack.CameraMode.FREE_LOOK
                ? wrapDegrees(vanillaBodyYaw + vanillaHeadRelativeYaw - last.yaw()) : 0.0f;
        state.x += dx * weight;
        state.y += dy * weight;
        state.z += dz * weight;
        state.bodyRot = lerpAngle(state.bodyRot, last.yaw(), weight);
        state.yRot = lerpAngle(state.yRot, targetHeadRelativeYaw, weight);
        state.xRot += (last.pitch() - state.xRot) * weight;
    }

    private static float lerpAngle(float from, float to, float amount) {
        float delta = (to - from) % 360.0f;
        if (delta > 180.0f) delta -= 360.0f;
        if (delta < -180.0f) delta += 360.0f;
        return from + delta * amount;
    }

    private static double lerp(double from, double to, float amount) {
        return from + (to - from) * amount;
    }

    private static float lerp(float from, float to, float amount) {
        return from + (to - from) * amount;
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }
}
