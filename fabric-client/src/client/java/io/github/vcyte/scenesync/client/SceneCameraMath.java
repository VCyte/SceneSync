package io.github.vcyte.scenesync.client;

/** Pure camera math shared by the local camera controller and deterministic unit tests. */
public final class SceneCameraMath {
    private SceneCameraMath() { }

    public static Look limitedLook(float actorYaw, float actorPitch, float scriptedYawOffset,
                                   float scriptedPitchOffset, float userYawOffset, float userPitchOffset,
                                   float maxYaw, float maxPitch) {
        float centerYaw = clamp(scriptedYawOffset, -maxYaw, maxYaw);
        float centerPitch = clamp(scriptedPitchOffset, -maxPitch, maxPitch);
        float yaw = wrap(actorYaw + centerYaw + clampUserOffset(userYawOffset, centerYaw, maxYaw));
        float pitch = clamp(actorPitch + centerPitch + clampUserOffset(userPitchOffset, centerPitch, maxPitch), -90, 90);
        return new Look(yaw, pitch);
    }

    /** Clamp and discard excess mouse travel, so reversing direction immediately moves off the edge. */
    public static float clampUserOffset(float userOffset, float scriptedCenter, float limit) {
        float center = clamp(scriptedCenter, -limit, limit);
        return clamp(userOffset, -limit - center, limit - center);
    }

    /** CameraType.THIRD_PERSON_FRONT mirrors vanilla's camera about the actor's look direction. */
    public static Look forCamera(float actorYaw, float actorPitch, boolean mirrored) {
        return mirrored ? new Look(wrap(actorYaw + 180.0f), -actorPitch) : new Look(actorYaw, actorPitch);
    }

    public static float wrap(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public record Look(float yaw, float pitch) { }
}
