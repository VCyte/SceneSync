package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow public abstract Entity entity();
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow private float fov;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void scenesync$applyCameraMotion(DeltaTracker tracker, CallbackInfo ci) {
        Entity cameraEntity = ((CameraAccessor)(Object)this).scenesync$getEntity();
        if (cameraEntity == null) return;
        SceneTrack.Sample sample = SceneController.sample(cameraEntity.getId(), System.currentTimeMillis());
        if (sample == null) return;
        if (cameraEntity instanceof LocalPlayer) {
            Vec3 correction = new Vec3(sample.worldX() - cameraEntity.getX(),
                    sample.worldY() - cameraEntity.getY(), sample.worldZ() - cameraEntity.getZ());
            setPosition(((Camera)(Object)this).position().add(correction));
        }
        // Deliberately preserve the player's own look direction. Scene camera rotation/shake
        // values are not applied because forcing them causes motion discomfort and aim drift.
        fov = Math.max(30.0f, Math.min(110.0f, fov + sample.cameraFov()));
    }
}
