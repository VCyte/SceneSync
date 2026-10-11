package io.github.vcyte.scenesync.client;

import java.util.List;

public record SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis,
                         double originX, double originY, double originZ, float originYaw, float originPitch,
                         CameraControl cameraControl, List<Frame> frames) {
    public SceneTrack { frames = List.copyOf(frames); }

    public SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis,
                      double originX, double originY, double originZ, float originYaw, List<Frame> frames) {
        this(runId, entityId, durationTicks, localStartMillis, originX, originY, originZ, originYaw, 0.0f,
                CameraControl.DEFAULT, frames);
    }

    public SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis,
                      double originX, double originY, double originZ, float originYaw,
                      CameraControl cameraControl, List<Frame> frames) {
        this(runId, entityId, durationTicks, localStartMillis, originX, originY, originZ, originYaw,
                0.0f, cameraControl, frames);
    }

    public Sample sample(long now) {
        double ticks = (now - localStartMillis) / 50.0;
        if (ticks < 0 || ticks > durationTicks) return null;
        Frame a = frames.getFirst();
        Frame b = a;
        int segment = 0;
        for (int i = 1; i < frames.size(); i++) {
            if (ticks < frames.get(i).tick()) { b = frames.get(i); segment = i - 1; break; }
            a = frames.get(i);
            b = a;
            segment = i;
        }
        double span = Math.max(1, b.tick() - a.tick());
        float t = (float)Math.max(0, Math.min(1, (ticks - a.tick()) / span));
        float eased = smoothstep(t);
        float sceneBlend = sceneEnvelope(ticks, durationTicks);
        Pose pose = a.pose() == null && b.pose() == null ? null
                : Pose.interpolate(a.pose() == null ? Pose.NEUTRAL : a.pose(),
                        b.pose() == null ? Pose.NEUTRAL : b.pose(), eased);
        double x = smoothCoordinate(frames, segment, ticks, 0);
        double y = smoothCoordinate(frames, segment, ticks, 1);
        double z = smoothCoordinate(frames, segment, ticks, 2);
        double radians = Math.toRadians(originYaw);
        double worldX = originX + x * Math.cos(radians) - z * Math.sin(radians);
        double worldZ = originZ + x * Math.sin(radians) + z * Math.cos(radians);
        float yaw = facingYaw(frames, segment, ticks, a, b, originYaw, radians);
        float pitch = Math.max(-90.0f, Math.min(90.0f, originPitch + lerp(a.pitch(), b.pitch(), eased)));
        return new Sample(worldX, originY + y, worldZ, wrapDegrees(yaw), pitch, a.animation(), (float)ticks,
                lerp(a.cameraYaw(), b.cameraYaw(), eased), lerp(a.cameraPitch(), b.cameraPitch(), eased),
                lerp(a.cameraRoll(), b.cameraRoll(), eased), lerp(a.cameraShake(), b.cameraShake(), eased),
                lerp(a.cameraFov(), b.cameraFov(), eased), cameraControl.mode(),
                cameraControl.maxYawDegrees(), cameraControl.maxPitchDegrees(), pose,
                b.animation(), eased, sceneBlend, a.pose() != null, b.pose() != null);
    }

    private static double smoothCoordinate(List<Frame> frames, int segment, double tick, int axis) {
        int last = frames.size() - 1;
        if (segment >= last || frames.size() == 1) return coordinate(frames.get(last), axis);
        Frame a = frames.get(segment), b = frames.get(segment + 1);
        double h = b.tick() - a.tick();
        double delta = (coordinate(b, axis) - coordinate(a, axis)) / h;
        double m0 = tangent(frames, segment, axis), m1 = tangent(frames, segment + 1, axis);
        double t = Math.max(0.0, Math.min(1.0, (tick - a.tick()) / h));
        double t2 = t * t, t3 = t2 * t;
        return (2*t3 - 3*t2 + 1) * coordinate(a, axis)
                + (t3 - 2*t2 + t) * h * m0
                + (-2*t3 + 3*t2) * coordinate(b, axis)
                + (t3 - t2) * h * m1;
    }

    /** PCHIP tangents keep authored path points exact while removing velocity jumps and overshoot. */
    private static double tangent(List<Frame> frames, int index, int axis) {
        int last = frames.size() - 1;
        if (last == 0) return 0.0;
        if (index == 0 || index == last) return 0.0;
        double before = secant(frames, index - 1, index, axis);
        double after = secant(frames, index, index + 1, axis);
        if (before == 0.0 || after == 0.0 || Math.signum(before) != Math.signum(after)) return 0.0;
        double hBefore = frames.get(index).tick() - frames.get(index - 1).tick();
        double hAfter = frames.get(index + 1).tick() - frames.get(index).tick();
        double w1 = 2.0 * hAfter + hBefore, w2 = hAfter + 2.0 * hBefore;
        return (w1 + w2) / (w1 / before + w2 / after);
    }

    private static double secant(List<Frame> frames, int from, int to, int axis) {
        return (coordinate(frames.get(to), axis) - coordinate(frames.get(from), axis))
                / (frames.get(to).tick() - frames.get(from).tick());
    }

    private static double coordinate(Frame frame, int axis) {
        return switch (axis) { case 0 -> frame.x(); case 1 -> frame.y(); default -> frame.z(); };
    }

    private static float facingYaw(List<Frame> frames, int segment, double tick, Frame a, Frame b,
                                   float originYaw, double originRadians) {
        float current = segmentFacing(frames, segment, tick, originYaw, originRadians);
        if (segment == 0 && frames.size() > 1) {
            float ramp = Math.min(3.0f, Math.max(1.0f, b.tick() - a.tick()));
            float entry = smoothstep((float)Math.max(0.0, Math.min(1.0, tick / ramp)));
            current = lerpAngle(originYaw, current, entry);
        }
        for (int i = 1; i < frames.size() - 1; i++) {
            double keyTick = frames.get(i).tick();
            double halfWindow = Math.min(1.5, Math.min(keyTick - frames.get(i - 1).tick(),
                    frames.get(i + 1).tick() - keyTick) * 0.5);
            if (tick < keyTick - halfWindow || tick > keyTick + halfWindow) continue;
            float before = segmentFacing(frames, i - 1, keyTick - 1.0e-6, originYaw, originRadians);
            float after = segmentFacing(frames, i, keyTick + 1.0e-6, originYaw, originRadians);
            float turn = smoothstep((float)((tick - (keyTick - halfWindow)) / (2.0 * halfWindow)));
            return lerpAngle(before, after, turn);
        }
        return current;
    }

    private static float segmentFacing(List<Frame> frames, int segment, double tick,
                                       float originYaw, double originRadians) {
        if (frames.size() == 1) return originYaw + frames.getFirst().yaw();
        if (segment >= frames.size() - 1) segment = frames.size() - 2;
        Frame a = frames.get(segment), b = frames.get(segment + 1);
        double dx = b.x() - a.x(), dz = b.z() - a.z();
        if (Math.abs(dx) + Math.abs(dz) > 0.0001) {
            double worldDx = dx * Math.cos(originRadians) - dz * Math.sin(originRadians);
            double worldDz = dx * Math.sin(originRadians) + dz * Math.cos(originRadians);
            return (float)Math.toDegrees(Math.atan2(-worldDx, worldDz));
        }
        float t = (float)Math.max(0.0, Math.min(1.0,
                (tick - a.tick()) / Math.max(1.0, b.tick() - a.tick())));
        return originYaw + lerp(a.yaw(), b.yaw(), smoothstep(t));
    }

    private static float lerpAngle(float from, float to, float t) {
        return from + wrapDegrees(to - from) * t;
    }

    private static float smoothstep(float t) { return t * t * (3.0f - 2.0f * t); }

    private static float sceneEnvelope(double ticks, int duration) {
        double ramp = Math.min(6.0, Math.max(0.5, duration / 2.0));
        double in = smoothstep((float)Math.max(0.0, Math.min(1.0, ticks / ramp)));
        double out = smoothstep((float)Math.max(0.0, Math.min(1.0, (duration - ticks) / ramp)));
        return (float)Math.min(in, out);
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
                         CameraMode cameraMode, float maxYawDegrees, float maxPitchDegrees, Pose pose,
                         String nextAnimation, float animationBlend, float sceneBlend,
                         boolean fromHasPose, boolean toHasPose) {
        public Sample withSceneBlend(float blend) {
            return new Sample(worldX, worldY, worldZ, yaw, pitch, animation, ticks, cameraYaw, cameraPitch,
                    cameraRoll, cameraShake, cameraFov, cameraMode, maxYawDegrees, maxPitchDegrees, pose,
                    nextAnimation, animationBlend, blend, fromHasPose, toHasPose);
        }
    }

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
