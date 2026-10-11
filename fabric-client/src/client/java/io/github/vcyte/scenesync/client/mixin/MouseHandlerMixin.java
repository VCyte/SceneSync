package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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
        if (!SceneController.blocksLook(player.getId(), now)) return;
        // Do not leave deltas queued: applying them on the first unlocked frame
        // would cause the post-scene head to jump and recreate the old flicker.
        accumulatedDX = 0.0;
        accumulatedDY = 0.0;
        ci.cancel();
    }

    /** Redirect after vanilla has applied sensitivity, inversion and smooth-camera processing. */
    @Redirect(method = "handleAccumulatedMovement()V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/player/LocalPlayer;turn(DD)V"))
    private void scenesync$routeVanillaLook(LocalPlayer player, double yawDelta, double pitchDelta) {
        long now = System.currentTimeMillis();
        var minecraft = Minecraft.getInstance();
        if (SceneController.usesDetachedLook(player.getId(), now)
                && !minecraft.options.getCameraType().isFirstPerson()) {
            SceneController.updateDetachedLook(player.getId(), yawDelta, pitchDelta);
            return;
        }
        SceneTrack.Sample sample = SceneController.sample(player.getId(), now);
        if (sample != null && sample.cameraMode() == SceneTrack.CameraMode.LIMITED) {
            float yaw = clampRelative(sample.yaw(), player.getYRot() + (float)yawDelta,
                    sample.maxYawDegrees());
            float pitch = Math.max(sample.pitch() - sample.maxPitchDegrees(),
                    Math.min(sample.pitch() + sample.maxPitchDegrees(), player.getXRot() + (float)pitchDelta));
            player.setYRot(yaw);
            player.setXRot(pitch);
            return;
        }
        player.turn(yawDelta, pitchDelta);
    }

    private static float clampRelative(float center, float desired, float limit) {
        float relative = desired - center;
        while (relative >= 180.0f) relative -= 360.0f;
        while (relative < -180.0f) relative += 360.0f;
        return center + Math.max(-limit, Math.min(limit, relative));
    }
}
