package io.github.vcyte.scenesync.client.animation;

/** Bounds scripted head offsets relative to the vanilla look pose, not the player's actual look. */
public final class HeadPoseMath {
    public static final float MAX_PITCH_RADIANS = (float)Math.toRadians(55.0);
    public static final float MAX_YAW_RADIANS = (float)Math.toRadians(65.0);
    public static final float MAX_ROLL_RADIANS = (float)Math.toRadians(35.0);

    private HeadPoseMath() { }

    public static float clampOverlay(float vanillaAngle, float animatedAngle, float maxOffsetRadians) {
        if (!Float.isFinite(vanillaAngle) || !Float.isFinite(animatedAngle) || !Float.isFinite(maxOffsetRadians))
            return vanillaAngle;
        float offset = animatedAngle - vanillaAngle;
        return vanillaAngle + Math.max(-maxOffsetRadians, Math.min(maxOffsetRadians, offset));
    }
}
