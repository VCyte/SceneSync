package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneFrame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenePathMathTest {
    @Test void acceptsAClientSampleBetweenIntegerTicksOnTheSmoothDashPath() {
        List<SceneFrame> frames = dashFrames();
        ScenePathMath.Point fractional = ScenePathMath.sample(frames, 17.5);

        assertTrue(ScenePathMath.matches(frames, 17.5, 17.5,
                0, 64, 0, 0, fractional.x(), 64 + fractional.y(), fractional.z(), 0.75));
    }

    @Test void rejectsMovementThatIsNotNearAnyFractionalPointOnTheAuthoredPath() {
        List<SceneFrame> frames = dashFrames();
        assertFalse(ScenePathMath.matches(frames, 17.5, 17.5,
                0, 64, 0, 0, 2, 64, 5.625, 0.75));
    }

    @Test void convertsWorldCoordinatesBackIntoActorLocalPathCoordinates() {
        List<SceneFrame> frames = List.of(frame(0, 0), frame(10, 10));
        ScenePathMath.Point local = ScenePathMath.sample(frames, 5);
        // Minecraft yaw 90 turns actor-local +Z into world -X.
        assertTrue(ScenePathMath.matches(frames, 5, 5,
                100, 64, 200, 90, 100 - local.z(), 64 + local.y(), 200 + local.x(), 0.75));
    }

    private static List<SceneFrame> dashFrames() {
        return List.of(frame(0, 0), frame(14, 0), frame(16, 0), frame(18, 8),
                frame(20, 16), frame(22, 24), frame(24, 24), frame(36, 24));
    }

    private static SceneFrame frame(int tick, double z) {
        return new SceneFrame(tick, 0, 0, z, 0, 0, "idle", 0, 0, 0, 0);
    }
}
