package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneFrame;

import java.util.List;

/** Continuous scene-path math shared by server movement validation and collision sampling. */
final class ScenePathMath {
    private static final double TIME_STEP_TICKS = 1.0 / 16.0;

    private ScenePathMath() { }

    record Point(double x, double y, double z) { }

    static Point sample(List<SceneFrame> frames, double tick) {
        if (frames.isEmpty()) throw new IllegalArgumentException("frames cannot be empty");
        if (tick <= frames.getFirst().tick()) return point(frames.getFirst());
        if (tick >= frames.getLast().tick()) return point(frames.getLast());
        int segment = 0;
        for (int i = 1; i < frames.size(); i++) {
            if (tick <= frames.get(i).tick()) { segment = i - 1; break; }
        }
        return new Point(coordinate(frames, segment, tick, 0), coordinate(frames, segment, tick, 1),
                coordinate(frames, segment, tick, 2));
    }

    /** Matches a reported world position against the fractional authored path over a limited lag window. */
    static boolean matches(List<SceneFrame> frames, double firstTick, double lastTick,
                           double originX, double originY, double originZ, float originYaw,
                           double worldX, double worldY, double worldZ, double tolerance) {
        if (lastTick < firstTick || !Double.isFinite(tolerance) || tolerance < 0) return false;
        double radians = Math.toRadians(originYaw), cos = Math.cos(radians), sin = Math.sin(radians);
        double dx = worldX - originX, dz = worldZ - originZ;
        double localX = dx * cos + dz * sin;
        double localY = worldY - originY;
        double localZ = -dx * sin + dz * cos;
        double toleranceSquared = tolerance * tolerance;
        int steps = Math.max(1, (int)Math.ceil((lastTick - firstTick) / TIME_STEP_TICKS));
        for (int i = 0; i <= steps; i++) {
            double tick = firstTick + (lastTick - firstTick) * i / steps;
            Point expected = sample(frames, tick);
            double ex = localX - expected.x(), ey = localY - expected.y(), ez = localZ - expected.z();
            if (ex * ex + ey * ey + ez * ez <= toleranceSquared) return true;
        }
        return false;
    }

    private static Point point(SceneFrame frame) { return new Point(frame.x(), frame.y(), frame.z()); }

    private static double coordinate(List<SceneFrame> frames, int segment, double tick, int axis) {
        int last = frames.size() - 1;
        if (segment >= last) return value(frames.get(last), axis);
        SceneFrame a = frames.get(segment), b = frames.get(segment + 1);
        double h = b.tick() - a.tick();
        double m0 = tangent(frames, segment, axis), m1 = tangent(frames, segment + 1, axis);
        double t = Math.max(0.0, Math.min(1.0, (tick - a.tick()) / h));
        double t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * value(a, axis)
                + (t3 - 2 * t2 + t) * h * m0
                + (-2 * t3 + 3 * t2) * value(b, axis)
                + (t3 - t2) * h * m1;
    }

    /** PCHIP tangents preserve monotonicity and position continuity without corner overshoot. */
    private static double tangent(List<SceneFrame> frames, int index, int axis) {
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

    private static double secant(List<SceneFrame> frames, int from, int to, int axis) {
        return (value(frames.get(to), axis) - value(frames.get(from), axis))
                / (frames.get(to).tick() - frames.get(from).tick());
    }

    private static double value(SceneFrame frame, int axis) {
        return switch (axis) { case 0 -> frame.x(); case 1 -> frame.y(); default -> frame.z(); };
    }
}
