package io.github.vcyte.scenesync.client;

import java.util.List;

public record SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis,
                         double originX, double originY, double originZ, float originYaw,
                         CameraControl cameraControl, List<Frame> frames) {
    public SceneTrack { frames = List.copyOf(frames); }

    public SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis,
                      double originX, double originY, double originZ, float originYaw, List<Frame> frames) {
        this(runId, entityId, durationTicks, localStartMillis, originX, originY, originZ, originYaw,
                CameraControl.DEFAULT, frames);
    }

    public Sample sample(long now) {
        double ticks = (now - localStartMillis) / 50.0;
        if (ticks < 0 || ticks > durationTicks) return null;
        Frame a = frames.getFirst();
        Frame b = a;
        for (int i = 1; i < frames.size(); i++) {
            if (ticks < frames.get(i).tick()) { b = frames.get(i); break; }
            a = frames.get(i);
            b = a;
        }
        double span = Math.max(1, b.tick() - a.tick());
        float t = (float)Math.max(0, Math.min(1, (ticks - a.tick()) / span));
        float eased = t * t * (3.0f - 2.0f * t);
        Pose pose = a.pose() == null && b.pose() == null ? null
                : Pose.interpolate(a.pose() == null ? Pose.NEUTRAL : a.pose(),
                        b.pose() == null ? Pose.NEUTRAL : b.pose(), t);
        double x = lerp(a.x(), b.x(), t), y = lerp(a.y(), b.y(), t), z = lerp(a.z(), b.z(), t);
        double radians = Math.toRadians(originYaw);
        double worldX = originX + x * Math.cos(radians) - z * Math.sin(radians);
        double worldZ = originZ + x * Math.sin(radians) + z * Math.cos(radians);
        float yaw;
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        if (Math.abs(dx) + Math.abs(dz) > 0.0001) {
            double worldDx = dx * Math.cos(radians) - dz * Math.sin(radians);
            double worldDz = dx * Math.sin(radians) + dz * Math.cos(radians);
            yaw = (float)Math.toDegrees(Math.atan2(-worldDx, worldDz));
        } else yaw = originYaw + lerp(a.yaw(), b.yaw(), t);
        return new Sample(worldX, originY + y, worldZ, wrapDegrees(yaw),
                lerp(a.pitch(), b.pitch(), t), a.animation(), (float)ticks,
                lerp(a.cameraYaw(), b.cameraYaw(), t), lerp(a.cameraPitch(), b.cameraPitch(), t),
                lerp(a.cameraRoll(), b.cameraRoll(), t), lerp(a.cameraShake(), b.cameraShake(), t),
                lerp(a.cameraFov(), b.cameraFov(), eased), cameraControl.mode(),
                cameraControl.maxYawDegrees(), cameraControl.maxPitchDegrees(), pose);
    }

    private static double lerp(double a, double b, double t) { return a + (b - a) * t; }
    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    private static float wrapDegrees(float degrees) {
        degrees %= 360f;
        if (degrees >= 180f) degrees -= 360f;
        if (degrees < -180f) degrees += 360f;
        return degrees;
    }

    public record Frame(int tick, double x, double y, double z, float yaw, float pitch, String animation,
                        float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake, float cameraFov,
                        Pose pose) {
        public Frame(int tick, double x, double y, double z, float yaw, float pitch, String animation,
                     float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake, float cameraFov) {
            this(tick, x, y, z, yaw, pitch, animation, cameraYaw, cameraPitch, cameraRoll, cameraShake, cameraFov, null);
        }
        public Frame(int tick, double x, double y, double z, float yaw, float pitch, String animation,
                     float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake) {
            this(tick, x, y, z, yaw, pitch, animation, cameraYaw, cameraPitch, cameraRoll, cameraShake, 0f, null);
        }
    }
    public enum CameraMode { FREE_LOOK, LOCKED, LIMITED }

    public record CameraControl(CameraMode mode, float maxYawDegrees, float maxPitchDegrees) {
        public static final CameraControl DEFAULT = new CameraControl(CameraMode.FREE_LOOK, 80, 45);
        public CameraControl {
            if (mode == null || !Float.isFinite(maxYawDegrees) || maxYawDegrees < 0 || maxYawDegrees > 100
                    || !Float.isFinite(maxPitchDegrees) || maxPitchDegrees < 0 || maxPitchDegrees > 70)
                throw new IllegalArgumentException("invalid scene camera control settings");
        }
    }

    public record Sample(double worldX, double worldY, double worldZ, float yaw, float pitch,
                         String animation, float ticks, float cameraYaw, float cameraPitch,
                         float cameraRoll, float cameraShake, float cameraFov,
                         CameraMode cameraMode, float maxYawDegrees, float maxPitchDegrees, Pose pose) { }

    public record Bone(float x, float y, float z, float pitch, float yaw, float roll) {
        public Bone {
            if (!Float.isFinite(x) || Math.abs(x) > 8 || !Float.isFinite(y) || Math.abs(y) > 8
                    || !Float.isFinite(z) || Math.abs(z) > 8 || !Float.isFinite(pitch) || Math.abs(pitch) > 180
                    || !Float.isFinite(yaw) || Math.abs(yaw) > 180 || !Float.isFinite(roll) || Math.abs(roll) > 180)
                throw new IllegalArgumentException("invalid bone transform");
        }

        private static Bone interpolate(Bone a, Bone b, float t) {
            return new Bone(lerp(a.x(), b.x(), t), lerp(a.y(), b.y(), t), lerp(a.z(), b.z(), t),
                    lerpAngle(a.pitch(), b.pitch(), t), lerpAngle(a.yaw(), b.yaw(), t),
                    lerpAngle(a.roll(), b.roll(), t));
        }
        private static float lerpAngle(float a, float b, float t) { return a + wrapDegrees(b - a) * t; }
    }

    public record Pose(Bone head, Bone body, Bone leftArm, Bone rightArm, Bone leftLeg, Bone rightLeg) {
        private static final Bone ZERO = new Bone(0, 0, 0, 0, 0, 0);
        public static final Pose NEUTRAL = new Pose(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO);

        public Pose {
            if (head == null || body == null || leftArm == null || rightArm == null || leftLeg == null || rightLeg == null)
                throw new IllegalArgumentException("pose bones cannot be null");
        }

        private static Pose interpolate(Pose a, Pose b, float t) {
            return new Pose(Bone.interpolate(a.head(), b.head(), t), Bone.interpolate(a.body(), b.body(), t),
                    Bone.interpolate(a.leftArm(), b.leftArm(), t), Bone.interpolate(a.rightArm(), b.rightArm(), t),
                    Bone.interpolate(a.leftLeg(), b.leftLeg(), t), Bone.interpolate(a.rightLeg(), b.rightLeg(), t));
        }
    }
}
