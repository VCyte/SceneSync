package io.github.vcyte.scenesync.client;

import java.util.List;

public record SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis,
                         double originX, double originY, double originZ, float originYaw, List<Frame> frames) {
    public SceneTrack { frames = List.copyOf(frames); }

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
                lerp(a.cameraRoll(), b.cameraRoll(), t), lerp(a.cameraShake(), b.cameraShake(), t));
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
                        float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake) { }
    public record Sample(double worldX, double worldY, double worldZ, float yaw, float pitch,
                         String animation, float ticks, float cameraYaw, float cameraPitch,
                         float cameraRoll, float cameraShake) { }
}
