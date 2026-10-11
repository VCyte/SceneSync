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

import java.util.concurrent.ConcurrentHashMap;

@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMixin {
    private static final ConcurrentHashMap<Integer, RenderRoot> SMOOTH_ROOTS = new ConcurrentHashMap<>();

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void scenesync$interpolateSceneRender(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        long now = System.currentTimeMillis();
        SceneTrack.Sample sample = SceneController.sample(state.id, now);
        if (sample != null) {
            float vanillaBodyYaw = state.bodyRot;
            float vanillaHeadRelativeYaw = state.yRot;
            float targetHeadRelativeYaw = sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK
                    ? wrapDegrees(vanillaBodyYaw + vanillaHeadRelativeYaw - sample.yaw()) : 0.0f;
            RenderRoot root = smoothRoot(state.id, sample, targetHeadRelativeYaw, now);
            float startT = Math.max(0.0f, Math.min(1.0f, sample.ticks() / 3.0f));
            float rootWeight = startT * startT * (3.0f - 2.0f * startT);
            state.x = lerp(state.x, root.x, rootWeight);
            state.y = lerp(state.y, root.y, rootWeight);
            state.z = lerp(state.z, root.z, rootWeight);
            state.bodyRot = lerpAngle(state.bodyRot, root.bodyYaw, rootWeight);
            state.yRot = lerpAngle(state.yRot, root.headRelativeYaw, rootWeight);
            state.xRot += (root.pitch - state.xRot) * rootWeight;
            return;
        }
        SceneController.VisualFade fade = SceneController.visualFade(state.id, now);
        if (fade == null) {
            SMOOTH_ROOTS.remove(state.id);
            return;
        }
        SceneTrack.Sample last = fade.sample();
        double dx = last.worldX() - state.x, dy = last.worldY() - state.y, dz = last.worldZ() - state.z;
        // Do not interpolate across a server teleport or world change.
        if (dx * dx + dy * dy + dz * dz > 9.0) {
            SMOOTH_ROOTS.remove(state.id);
            return;
        }
        float vanillaBodyYaw = state.bodyRot;
        float vanillaHeadRelativeYaw = state.yRot;
        float targetHeadRelativeYaw = last.cameraMode() == SceneTrack.CameraMode.FREE_LOOK
                ? wrapDegrees(vanillaBodyYaw + vanillaHeadRelativeYaw - last.yaw()) : 0.0f;
        RenderRoot root = smoothRoot(state.id, last, targetHeadRelativeYaw, now);
        float weight = fade.rootWeight();
        state.x += (root.x - state.x) * weight;
        state.y += (root.y - state.y) * weight;
        state.z += (root.z - state.z) * weight;
        state.bodyRot = lerpAngle(state.bodyRot, root.bodyYaw, weight);
        state.yRot = lerpAngle(state.yRot, root.headRelativeYaw, weight);
        state.xRot += (root.pitch - state.xRot) * weight;
    }

    private static RenderRoot smoothRoot(int id, SceneTrack.Sample sample, float headRelativeYaw, long now) {
        RenderRoot target = new RenderRoot(sample.worldX(), sample.worldY(), sample.worldZ(),
                sample.yaw(), headRelativeYaw, sample.pitch(), now);
        return SMOOTH_ROOTS.compute(id, (key, previous) -> {
            if (previous == null || squaredDistance(previous, target) > 16.0) return target;
            double elapsed = Math.max(1.0, Math.min(100.0, now - previous.timeMillis));
            float amount = 1.0f - (float)Math.exp(-elapsed / 45.0);
            return new RenderRoot(
                    lerp(previous.x, target.x, amount),
                    lerp(previous.y, target.y, amount),
                    lerp(previous.z, target.z, amount),
                    lerpAngle(previous.bodyYaw, target.bodyYaw, amount),
                    lerpAngle(previous.headRelativeYaw, target.headRelativeYaw, amount),
                    previous.pitch + (target.pitch - previous.pitch) * amount,
                    now);
        });
    }

    private static double squaredDistance(RenderRoot a, RenderRoot b) {
        double dx = a.x - b.x, dy = a.y - b.y, dz = a.z - b.z;
        return dx * dx + dy * dy + dz * dz;
    }

    private static float lerpAngle(float from, float to, float amount) {
        return from + wrapDegrees(to - from) * amount;
    }

    private static double lerp(double from, double to, float amount) {
        return from + (to - from) * amount;
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }

    private record RenderRoot(double x, double y, double z, float bodyYaw, float headRelativeYaw,
                              float pitch, long timeMillis) { }
}
