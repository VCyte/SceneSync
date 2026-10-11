package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneCameraMath;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
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
            if (cameraEntity instanceof LocalPlayer localPlayer
                    && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                SceneController.VisualFade fade = SceneController.visualFade(
                        cameraEntity.getId(), System.currentTimeMillis());
                SceneController.Look current = fade == null ? null
                        : SceneController.detachedLook(cameraEntity.getId());
                if (fade != null && current != null
                        && fade.sample().cameraMode() == SceneTrack.CameraMode.FREE_LOOK) {
                    float blend = 1.0f - fade.rootWeight();
                    float yaw = lerpAngle(current.yaw(), localPlayer.getYRot(), blend);
                    float pitch = localPlayer.getXRot()
                            + (current.pitch() - localPlayer.getXRot()) * (1.0f - blend);
                    applyDetachedCamera(localPlayer, fade.sample(), tracker, yaw, pitch);
                    return;
                }
            }
            SceneController.clearDetachedLook(cameraEntity.getId());
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

            if (sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK
                    && !Minecraft.getInstance().options.getCameraType().isFirstPerson()) {
                SceneController.beginDetachedLook(cameraEntity.getId(), ((Camera)(Object)this).yRot(),
                        ((Camera)(Object)this).xRot());
                SceneController.Look look = SceneController.detachedLook(cameraEntity.getId());
                if (look != null) applyDetachedCamera(localPlayer, sample, tracker, look.yaw(), look.pitch());
                scenesync$wasLocked = false;
                scenesync$limitedActive = false;
                scenesync$cameraEntityId = cameraEntity.getId();
                fov = Math.max(30.0f, Math.min(110.0f, fov + sample.cameraFov() * sample.sceneBlend()));
                return;
            }

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
                        scenesync$userYawOffset = SceneCameraMath.clampUserOffset(
                                wrapDegrees(localPlayer.getYRot() - limitedTargetYaw), centerYaw, maxYaw);
                        scenesync$userPitchOffset = SceneCameraMath.clampUserOffset(
                                localPlayer.getXRot() - limitedTargetPitch, centerPitch, maxPitch);
                        scenesync$limitedActive = true;
                    } else {
                        scenesync$userYawOffset = SceneCameraMath.clampUserOffset(
                                scenesync$userYawOffset + wrapDegrees(localPlayer.getYRot() - scenesync$lastAppliedYaw),
                                centerYaw, maxYaw);
                        scenesync$userPitchOffset = SceneCameraMath.clampUserOffset(
                                scenesync$userPitchOffset + localPlayer.getXRot() - scenesync$lastAppliedPitch,
                                centerPitch, maxPitch);
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
                boolean mirrored = Minecraft.getInstance().options.getCameraType().isMirrored();
                SceneCameraMath.Look cameraLook = SceneCameraMath.forCamera(yaw, pitch, mirrored);
                float shake = Math.min(1.0f, sample.cameraShake()) * sample.sceneBlend();
                float phase = sample.ticks() * 2.7f + cameraEntity.getId() * 0.17f;
                float shakenYaw = cameraLook.yaw() + (float)Math.sin(phase) * shake * 0.12f;
                float shakenPitch = cameraLook.pitch() + (float)Math.cos(phase * 0.83f) * shake * 0.10f;
                setRotation(shakenYaw, shakenPitch);
                float roll = clamp(sample.cameraRoll(), -6.0f, 6.0f) * sample.sceneBlend();
                if (roll != 0.0f) ((Camera)(Object)this).rotation().rotateZ((float)Math.toRadians(roll));
            } else {
                scenesync$wasLocked = false;
                scenesync$limitedActive = false;
                scenesync$cameraEntityId = cameraEntity.getId();
            }
        }
        // Rotation is only scripted for the local actor; compatible viewers keep their own camera.
        fov = Math.max(30.0f, Math.min(110.0f, fov + sample.cameraFov() * sample.sceneBlend()));
    }

    @Unique private void applyDetachedCamera(LocalPlayer player, SceneTrack.Sample sample,
                                              DeltaTracker tracker, float yaw, float pitch) {
        Vec3 pivot = new Vec3(sample.worldX(), sample.worldY() + player.getEyeHeight(), sample.worldZ());
        Vec3 offset = ((Camera)(Object)this).position().subtract(pivot);
        double distance = Math.max(0.05, offset.length());
        Vec3 forward = Vec3.directionFromRotation(pitch, yaw);
        setPosition(pivot.subtract(forward.scale(distance)));
        boolean mirrored = Minecraft.getInstance().options.getCameraType().isMirrored();
        SceneCameraMath.Look cameraLook = SceneCameraMath.forCamera(yaw, pitch, mirrored);
        setRotation(cameraLook.yaw(), cameraLook.pitch());
    }

    @Unique private static float lerpAngle(float from, float to, float amount) {
        return from + wrapDegrees(to - from) * amount;
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
