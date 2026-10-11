package io.github.vcyte.scenesync.api;

import java.util.Set;

/** A keyframe relative to the actor's location and yaw when playback begins. */
public record SceneFrame(
        int tick,
        double x,
        double y,
        double z,
        float yaw,
        float pitch,
        String animation,
        float cameraYaw,
        float cameraPitch,
        float cameraRoll,
        float cameraShake,
        float cameraFov,
        ModelPose pose
) {
    /** Legacy convenience poses. Plugin-owned IDs can instead carry a generic ModelPose per keyframe. */
    public static final Set<String> SUPPORTED_ANIMATIONS = Set.of("idle", "walk", "run", "wave", "bow", "spin", "clap", "sit");
    public SceneFrame {
        if (tick < 0 || tick > 24_000) throw new IllegalArgumentException("tick must be in 0..24000");
        bounded(x, "x"); bounded(y, "y"); bounded(z, "z");
        bounded(yaw, "yaw", 360f); bounded(pitch, "pitch", 90f);
        if (animation == null || !animation.matches("[a-z0-9][a-z0-9._:-]{0,95}"))
            throw new IllegalArgumentException("animation id must be a lowercase namespaced-safe identifier");
        bounded(cameraYaw, "cameraYaw", 45f);
        bounded(cameraPitch, "cameraPitch", 45f);
        bounded(cameraRoll, "cameraRoll", 45f);
        if (!Float.isFinite(cameraShake) || cameraShake < 0 || cameraShake > 10f)
            throw new IllegalArgumentException("cameraShake must be finite and in 0..10");
        bounded(cameraFov, "cameraFov", 25f);
    }

    /** Compatibility overload that leaves the generic skeletal pose unset. */
    public SceneFrame(int tick, double x, double y, double z, float yaw, float pitch, String animation,
                      float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake, float cameraFov) {
        this(tick, x, y, z, yaw, pitch, animation, cameraYaw, cameraPitch, cameraRoll, cameraShake, cameraFov, null);
    }

    /** Compatibility overload that defaults the optional FOV offset to neutral. */
    public SceneFrame(int tick, double x, double y, double z, float yaw, float pitch, String animation,
                      float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake) {
        this(tick, x, y, z, yaw, pitch, animation, cameraYaw, cameraPitch, cameraRoll, cameraShake, 0f, null);
    }

    private static void bounded(double value, String name) {
        if (!Double.isFinite(value) || Math.abs(value) > 128.0)
            throw new IllegalArgumentException(name + " must be finite and within +/-128");
    }
    private static void bounded(float value, String name, float maximumAbs) {
        if (!Float.isFinite(value) || Math.abs(value) > maximumAbs)
            throw new IllegalArgumentException(name + " must be finite and within +/-" + maximumAbs);
    }
}
