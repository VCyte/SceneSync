package io.github.vcyte.scenesync.api;

import java.util.Objects;

/** Camera control defaults and the allowed mouse-look cone for one scene. */
public record SceneCameraSettings(CameraMode mode, float maxYawDegrees, float maxPitchDegrees) {
    public static final SceneCameraSettings DEFAULT = new SceneCameraSettings(CameraMode.FREE_LOOK, 80.0f, 45.0f);

    public SceneCameraSettings {
        Objects.requireNonNull(mode, "mode");
        if (!Float.isFinite(maxYawDegrees) || maxYawDegrees < 0 || maxYawDegrees > 100)
            throw new IllegalArgumentException("maxYawDegrees must be finite and in 0..100");
        if (!Float.isFinite(maxPitchDegrees) || maxPitchDegrees < 0 || maxPitchDegrees > 70)
            throw new IllegalArgumentException("maxPitchDegrees must be finite and in 0..70");
    }
}
