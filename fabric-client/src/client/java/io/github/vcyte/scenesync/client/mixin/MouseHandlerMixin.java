package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Handles scene camera input without redirecting version-sensitive player methods. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Shadow private double accumulatedDX;
    @Shadow private double accumulatedDY;

    @Inject(method = "handleAccumulatedMovement()V", at = @At("HEAD"), cancellable = true)
    private void scenesync$handleSceneLook(CallbackInfo ci) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null) return;
        long now = System.currentTimeMillis();

        if (SceneController.usesDetachedLook(player.getId(), now)
                && !minecraft.options.getCameraType().isFirstPerson()) {
            double[] delta = scenesync$vanillaDelta(minecraft);
            SceneController.updateDetachedLook(player.getId(), delta[0], delta[1]);
            scenesync$clearAccumulated();
            ci.cancel();
            return;
        }

        SceneTrack.Sample sample = SceneController.sample(player.getId(), now);
        if (sample != null && sample.cameraMode() == SceneTrack.CameraMode.LIMITED) {
            double[] delta = scenesync$vanillaDelta(minecraft);
            float yaw = clampRelative(sample.yaw(), player.getYRot() + (float)delta[0],
                    sample.maxYawDegrees());
            float pitch = Math.max(sample.pitch() - sample.maxPitchDegrees(),
                    Math.min(sample.pitch() + sample.maxPitchDegrees(), player.getXRot() + (float)delta[1]));
            player.setYRot(yaw);
            player.setXRot(pitch);
            scenesync$clearAccumulated();
            ci.cancel();
            return;
        }

        if (SceneController.blocksLook(player.getId(), now)) {
            // Discard input while locked so it cannot be applied after the scene ends.
            scenesync$clearAccumulated();
            ci.cancel();
        }
    }

    /** Same sensitivity/inversion formula as MouseHandler.turnPlayer in Minecraft 26.2. */
    private double[] scenesync$vanillaDelta(Minecraft minecraft) {
        double sensitivity = minecraft.options.sensitivity().get();
        double scale = Math.pow(sensitivity * 0.6 + 0.2, 3.0) * 8.0;
        double yaw = accumulatedDX * scale;
        double pitch = accumulatedDY * scale;
        if ((Boolean)minecraft.options.invertMouseX().get()) yaw = -yaw;
        if ((Boolean)minecraft.options.invertMouseY().get()) pitch = -pitch;
        return new double[] { yaw, pitch };
    }

    private void scenesync$clearAccumulated() {
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
    }

    private static float clampRelative(float center, float desired, float limit) {
        float relative = desired - center;
        while (relative >= 180.0f) relative -= 360.0f;
        while (relative < -180.0f) relative += 360.0f;
        return center + Math.max(-limit, Math.min(limit, relative));
    }
}
