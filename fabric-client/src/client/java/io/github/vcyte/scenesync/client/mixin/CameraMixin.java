package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.joml.Quaternionf;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow public abstract Entity entity();

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void scenesync$applyCameraMotion(DeltaTracker tracker, CallbackInfo ci) {
        Entity cameraEntity = ((CameraAccessor)(Object)this).scenesync$getEntity();
        if (cameraEntity == null) return;
        SceneTrack.Sample sample = SceneController.sample(cameraEntity.getId(), System.currentTimeMillis());
        if (sample == null) return;
        float shake = sample.cameraShake();
        float phase = sample.ticks() * 1.73f;
        float shakeYaw = (float)Math.sin(phase) * shake;
        float shakePitch = (float)Math.cos(phase * 1.31f) * shake;
        Quaternionf rotation = ((CameraAccessor)(Object)this).scenesync$getRotation();
        rotation.rotateLocalY((float)Math.toRadians(sample.cameraYaw() + shakeYaw));
        rotation.rotateLocalX((float)Math.toRadians(sample.cameraPitch() + shakePitch));
        rotation.rotateLocalZ((float)Math.toRadians(sample.cameraRoll()));
    }
}
