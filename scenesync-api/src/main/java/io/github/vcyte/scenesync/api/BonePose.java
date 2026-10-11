package io.github.vcyte.scenesync.api;

/** Per-bone transform; translation is in model pixels and rotations are degrees. */
public record BonePose(float x, float y, float z, float pitch, float yaw, float roll) {
    public static final BonePose ZERO = new BonePose(0, 0, 0, 0, 0, 0);

    public BonePose {
        bounded(x, "x", 8.0f);
        bounded(y, "y", 8.0f);
        bounded(z, "z", 8.0f);
        bounded(pitch, "pitch", 180.0f);
        bounded(yaw, "yaw", 180.0f);
        bounded(roll, "roll", 180.0f);
    }

    private static void bounded(float value, String name, float bound) {
        if (!Float.isFinite(value) || Math.abs(value) > bound)
            throw new IllegalArgumentException(name + " must be finite and within +/-" + bound);
    }
}
