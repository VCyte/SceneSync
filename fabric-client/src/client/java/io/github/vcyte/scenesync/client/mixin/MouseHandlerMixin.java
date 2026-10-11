package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures vanilla mouse rotation once, then applies scene camera rules without re-scaling it. */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    private boolean scenesync$captured;
    private int scenesync$entityId = -1;
    private SceneTrack.CameraMode scenesync$cameraMode;
    private float scenesync$beforeYaw;
    private float scenesync$beforePitch;

    @Inject(method = "handleAccumulatedMovement()V", at = @At("HEAD"))
    private void scenesync$captureVanillaLook(CallbackInfo ci) {
        scenesync$captured = false;
        var player = Minecraft.getInstance().player;
        if (player == null) return;
        SceneTrack.Sample sample = SceneController.sample(player.getId(), System.currentTimeMillis());
        if (sample == null) return;
        scenesync$captured = true;
        scenesync$entityId = player.getId();
        scenesync$cameraMode = sample.cameraMode();
        scenesync$beforeYaw = player.getYRot();
        scenesync$beforePitch = player.getXRot();
    }

    @Inject(method = "handleAccumulatedMovement()V", at = @At("TAIL"))
    private void scenesync$applySceneLook(CallbackInfo ci) {
        if (!scenesync$captured) return;
        scenesync$captured = false;
        var player = Minecraft.getInstance().player;
        if (player == null || player.getId() != scenesync$entityId) return;

        float vanillaYaw = player.getYRot();
        float vanillaPitch = player.getXRot();
        if (scenesync$cameraMode == SceneTrack.CameraMode.FREE_LOOK
                && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
            // Vanilla already applied the configured sensitivity, smoothing and inversion.
            // Keep that exact delta for the detached camera, then leave the actor head fixed.
            SceneController.updateDetachedLook(player.getId(),
                    wrapDegrees(vanillaYaw - scenesync$beforeYaw), vanillaPitch - scenesync$beforePitch);
            player.setYRot(scenesync$beforeYaw);
            player.setXRot(scenesync$beforePitch);
            return;
        }
        if (scenesync$cameraMode == SceneTrack.CameraMode.LIMITED) {
            SceneTrack.Sample sample = SceneController.sample(player.getId(), System.currentTimeMillis());
            if (sample != null) {
                player.setYRot(clampRelative(sample.yaw(), vanillaYaw, sample.maxYawDegrees()));
                player.setXRot(Math.max(sample.pitch() - sample.maxPitchDegrees(),
                        Math.min(sample.pitch() + sample.maxPitchDegrees(), vanillaPitch)));
            }
            return;
        }
        if (scenesync$cameraMode == SceneTrack.CameraMode.LOCKED) {
            // Consume vanilla input, but do not let it fight the scripted camera.
            player.setYRot(scenesync$beforeYaw);
            player.setXRot(scenesync$beforePitch);
        }
    }

    private static float clampRelative(float center, float desired, float limit) {
        float relative = wrapDegrees(desired - center);
        return center + Math.max(-limit, Math.min(limit, relative));
    }

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }
}
