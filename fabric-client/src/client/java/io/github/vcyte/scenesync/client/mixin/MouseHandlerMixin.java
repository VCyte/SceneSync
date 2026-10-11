package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents vanilla mouse input from fighting SceneSync's LOCKED camera. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement()V", at = @At("HEAD"), cancellable = true)
    private void scenesync$discardLockedLookInput(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        long now = System.currentTimeMillis();
        var minecraft = Minecraft.getInstance();
        if (SceneController.usesDetachedLook(player.getId(), now)
                && !minecraft.options.getCameraType().isFirstPerson()) {
            double[] lookDelta = scenesync$lookDelta(minecraft);
            double yaw = lookDelta[0];
            double pitch = lookDelta[1];
            SceneController.updateDetachedLook(player.getId(), yaw, pitch);
            accumulatedDX = 0.0;
            accumulatedDY = 0.0;
            ci.cancel();
            return;
        }
        SceneTrack.Sample sample = SceneController.sample(player.getId(), now);
        if (sample != null && sample.cameraMode() == SceneTrack.CameraMode.LIMITED) {
            double[] lookDelta = scenesync$lookDelta(minecraft);
            float yaw = clampRelative(sample.yaw(), player.getYRot() + (float)lookDelta[0],
                    sample.maxYawDegrees());
            float pitch = Math.max(sample.pitch() - sample.maxPitchDegrees(),
                    Math.min(sample.pitch() + sample.maxPitchDegrees(), player.getXRot() + (float)lookDelta[1]));
            // Apply only the portion inside the cone. Do not let vanilla apply the
            // rejected remainder on the next tick, which used to cause edge jitter.
            player.setYRot(yaw);
            player.setXRot(pitch);
            accumulatedDX = 0.0;
            accumulatedDY = 0.0;
            ci.cancel();
            return;
        }
        if (!SceneController.blocksLook(player.getId(), now)) return;
        // Do not leave deltas queued: applying them on the first unlocked frame
        // would cause the post-scene head to jump and recreate the old flicker.
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        ci.cancel();
    }

    private double[] scenesync$lookDelta(Minecraft minecraft) {
        double sensitivity = minecraft.options.sensitivity().get();
        double scale = Math.pow(sensitivity * 0.6 + 0.2, 3.0) * 8.0;
        double yaw = accumulatedDX * scale;
        double pitch = accumulatedDY * scale;
        if ((Boolean)minecraft.options.invertMouseX().get()) yaw = -yaw;
        if ((Boolean)minecraft.options.invertMouseY().get()) pitch = -pitch;
        return new double[] { yaw, pitch };
    }

    private static float clampRelative(float center, float desired, float limit) {
        float relative = desired - center;
        while (relative >= 180.0f) relative -= 360.0f;
        while (relative < -180.0f) relative += 360.0f;
        return center + Math.max(-limit, Math.min(limit, relative));
    }
}
