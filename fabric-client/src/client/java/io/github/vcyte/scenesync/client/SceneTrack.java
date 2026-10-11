package io.github.vcyte.scenesync.client;

import java.util.List;

public record SceneTrack(String runId, int entityId, int durationTicks, long localStartMillis, List<Frame> frames) {
    public SceneTrack { frames = List.copyOf(frames); }

    Sample sample(long now) {
        double ticks = (now - localStartMillis) / 50.0;
        if (ticks < 0 || ticks > durationTicks) return null;
        Frame a = frames.getFirst();
        Frame b = frames.getFirst();
        for (int i = 1; i < frames.size(); i++) {
            if (ticks < frames.get(i).tick()) { b = frames.get(i); break; }
            a = frames.get(i);
            b = a;
        }
        double span = Math.max(1, b.tick() - a.tick());
        float t = (float) Math.max(0, Math.min(1, (ticks - a.tick()) / span));
        return new Sample(a.animation(), (float) ticks,
                lerp(a.cameraYaw(), b.cameraYaw(), t),
                lerp(a.cameraPitch(), b.cameraPitch(), t),
                lerp(a.cameraRoll(), b.cameraRoll(), t),
                lerp(a.cameraShake(), b.cameraShake(), t));
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }
    public record Frame(int tick, double x, double y, double z, float yaw, float pitch, String animation,
                 float cameraYaw, float cameraPitch, float cameraRoll, float cameraShake) { }
    public record Sample(String animation, float ticks, float cameraYaw, float cameraPitch,
                  float cameraRoll, float cameraShake) { }
}
