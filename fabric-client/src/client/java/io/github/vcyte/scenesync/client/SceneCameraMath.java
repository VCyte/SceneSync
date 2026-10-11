package io.github.vcyte.scenesync.client;

/** Pure camera math shared by the local camera controller and deterministic unit tests. */
public final class SceneCameraMath {
    private SceneCameraMath() { }

    public static Look limitedLook(float actorYaw, float actorPitch, float scriptedYawOffset,
                                   float scriptedPitchOffset, float userYawOffset, float userPitchOffset,
                                   float maxYaw, float maxPitch) {
        float centerYaw = clamp(scriptedYawOffset, -maxYaw, maxYaw);
        float centerPitch = clamp(scriptedPitchOffset, -maxPitch, maxPitch);
        float minUserYaw = Math.max(-maxYaw, -maxYaw - centerYaw);
        float maxUserYaw = Math.min(maxYaw, maxYaw - centerYaw);
        float minUserPitch = Math.max(-maxPitch, -maxPitch - centerPitch);
        float maxUserPitch = Math.min(maxPitch, maxPitch - centerPitch);
        float yaw = wrap(actorYaw + centerYaw + clamp(userYawOffset, minUserYaw, maxUserYaw));
        float pitch = clamp(actorPitch + centerPitch + clamp(userPitchOffset, minUserPitch, maxUserPitch), -90, 90);
        return new Look(yaw, pitch);
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
