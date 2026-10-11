package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
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
            double sensitivity = minecraft.options.sensitivity().get();
            double scale = Math.pow(sensitivity * 0.6 + 0.2, 3.0) * 8.0;
            double yaw = accumulatedDX * scale;
            double pitch = accumulatedDY * scale;
            if ((Boolean)minecraft.options.invertMouseX().get()) yaw = -yaw;
            if ((Boolean)minecraft.options.invertMouseY().get()) pitch = -pitch;
            SceneController.updateDetachedLook(player.getId(), yaw, pitch);
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
}
