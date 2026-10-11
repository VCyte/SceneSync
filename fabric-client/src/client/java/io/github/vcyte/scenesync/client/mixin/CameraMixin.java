package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneCameraMath;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow public abstract Entity entity();
    @Shadow protected abstract void setPosition(Vec3 position);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow private float fov;

    @Unique private boolean scenesync$wasLocked;
    @Unique private int scenesync$cameraEntityId = -1;
    @Unique private long scenesync$lockStartMillis;
    @Unique private float scenesync$entryYaw;
    @Unique private float scenesync$entryPitch;
    @Unique private boolean scenesync$limitedActive;
    @Unique private float scenesync$userYawOffset;
    @Unique private float scenesync$userPitchOffset;
    @Unique private float scenesync$lastAppliedYaw;
    @Unique private float scenesync$lastAppliedPitch;

    @Inject(method = "update(Lnet/minecraft/client/DeltaTracker;)V", at = @At("TAIL"))
    private void scenesync$applyCameraMotion(DeltaTracker tracker, CallbackInfo ci) {
        Entity cameraEntity = ((CameraAccessor)(Object)this).scenesync$getEntity();
        if (cameraEntity == null) {
            scenesync$wasLocked = false;
            scenesync$limitedActive = false;
            return;
        }
        SceneTrack.Sample sample = SceneController.sample(cameraEntity.getId(), System.currentTimeMillis());
        if (sample == null) {
            scenesync$wasLocked = false;
            scenesync$limitedActive = false;
            scenesync$cameraEntityId = -1;
            return;
        }
        if (cameraEntity instanceof LocalPlayer localPlayer) {
            float partialTick = tracker.getGameTimeDeltaPartialTick(false);
            Vec3 interpolatedEntityPosition = cameraEntity.getPosition(partialTick);
            Vec3 correction = new Vec3(sample.worldX() - interpolatedEntityPosition.x,
                    sample.worldY() - interpolatedEntityPosition.y, sample.worldZ() - interpolatedEntityPosition.z);
            setPosition(((Camera)(Object)this).position().add(correction));

            if (sample.cameraMode() != SceneTrack.CameraMode.FREE_LOOK) {
                float targetYaw = wrapDegrees(sample.yaw() + sample.cameraYaw());
                float targetPitch = clamp(sample.pitch() + sample.cameraPitch(), -90.0f, 90.0f);
                float yaw;
                float pitch;
                if (sample.cameraMode() == SceneTrack.CameraMode.LOCKED) {
                    scenesync$limitedActive = false;
                    long now = System.currentTimeMillis();
                    if (!scenesync$wasLocked || scenesync$cameraEntityId != cameraEntity.getId()) {
                        scenesync$entryYaw = localPlayer.getYRot();
                        scenesync$entryPitch = localPlayer.getXRot();
                        scenesync$lockStartMillis = now;
                        scenesync$cameraEntityId = cameraEntity.getId();
                        scenesync$wasLocked = true;
                    }
                    float blend = clamp((now - scenesync$lockStartMillis) / 300.0f, 0.0f, 1.0f);
                    blend = blend * blend * (3.0f - 2.0f * blend);
                    yaw = wrapDegrees(scenesync$entryYaw + wrapDegrees(targetYaw - scenesync$entryYaw) * blend);
                    pitch = scenesync$entryPitch + (targetPitch - scenesync$entryPitch) * blend;
                } else {
                    scenesync$wasLocked = false;
                    float maxYaw = sample.maxYawDegrees();
                    float maxPitch = sample.maxPitchDegrees();
                    float centerYaw = clamp(sample.cameraYaw(), -maxYaw, maxYaw);
                    float centerPitch = clamp(sample.cameraPitch(), -maxPitch, maxPitch);
                    float limitedTargetYaw = wrapDegrees(sample.yaw() + centerYaw);
                    float limitedTargetPitch = clamp(sample.pitch() + centerPitch, -90.0f, 90.0f);
                    if (!scenesync$limitedActive || scenesync$cameraEntityId != cameraEntity.getId()) {
                        scenesync$userYawOffset = wrapDegrees(localPlayer.getYRot() - limitedTargetYaw);
                        scenesync$userPitchOffset = localPlayer.getXRot() - limitedTargetPitch;
                        scenesync$limitedActive = true;
                    } else {
                        scenesync$userYawOffset = wrapDegrees(scenesync$userYawOffset
                                + wrapDegrees(localPlayer.getYRot() - scenesync$lastAppliedYaw));
                        scenesync$userPitchOffset += localPlayer.getXRot() - scenesync$lastAppliedPitch;
                    }
                    scenesync$cameraEntityId = cameraEntity.getId();
                    SceneCameraMath.Look limited = SceneCameraMath.limitedLook(sample.yaw(), sample.pitch(),
                            centerYaw, centerPitch, scenesync$userYawOffset, scenesync$userPitchOffset,
                            maxYaw, maxPitch);
                    yaw = limited.yaw();
                    pitch = limited.pitch();
                }
                // Persist the constrained rotation on the actor as well as the camera, so neither
                // outgoing movement packets nor ending a scene can snap to an unconstrained aim.
                localPlayer.setYRot(yaw);
                localPlayer.setXRot(pitch);
                scenesync$lastAppliedYaw = yaw;
                scenesync$lastAppliedPitch = pitch;
                float shake = Math.min(1.0f, sample.cameraShake());
                float phase = sample.ticks() * 2.7f + cameraEntity.getId() * 0.17f;
                float shakenYaw = yaw + (float)Math.sin(phase) * shake * 0.12f;
                float shakenPitch = pitch + (float)Math.cos(phase * 0.83f) * shake * 0.10f;
                setRotation(shakenYaw, shakenPitch);
                float roll = clamp(sample.cameraRoll(), -6.0f, 6.0f);
                if (roll != 0.0f) ((Camera)(Object)this).rotation().rotateZ((float)Math.toRadians(roll));
            } else {
                scenesync$wasLocked = false;
                scenesync$limitedActive = false;
                scenesync$cameraEntityId = cameraEntity.getId();
            }
        }
        // Rotation is only scripted for the local actor; compatible viewers keep their own camera.
        fov = Math.max(30.0f, Math.min(110.0f, fov + sample.cameraFov()));
    }

    @Unique private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    @Unique private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }
}
