package io.github.vcyte.scenesync.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneTrackTest {
    @Test void rotatesForwardPathIntoInitialFacingAndFacesTravelDirection() {
        SceneTrack track = new SceneTrack("run-1", 7, 20, 1_000,
                10, 64, 20, 90,
                List.of(
                        frame(0, 0, 0, 0, 0),
                        frame(20, 0, 0, 2, 0)));

        SceneTrack.Sample middle = track.sample(1_500);

        assertEquals(9.0, middle.worldX(), 1.0e-6);
        assertEquals(64.0, middle.worldY(), 1.0e-6);
        assertEquals(20.0, middle.worldZ(), 1.0e-6);
        assertEquals(90.0f, middle.yaw(), 1.0e-4f);
    }

    @Test void stationaryFrameKeepsStartingViewPlusFrameYaw() {
        SceneTrack track = new SceneTrack("run-2", 8, 20, 1_000,
                0, 70, 0, 45,
                List.of(frame(0, 1, 0, 0, 10), frame(20, 1, 0, 0, 10)));

        SceneTrack.Sample sample = track.sample(1_500);

        double rootHalf = Math.sqrt(0.5);
        assertEquals(rootHalf, sample.worldX(), 1.0e-6);
        assertEquals(rootHalf, sample.worldZ(), 1.0e-6);
        assertEquals(55.0f, sample.yaw(), 1.0e-4f);
    }

    @Test void fovOffsetUsesSmoothEasingBetweenKeyframes() {
        SceneTrack track = new SceneTrack("run-3", 9, 20, 1_000,
                0, 64, 0, 0,
                List.of(frameWithFov(0, 0), frameWithFov(20, -20)));

        SceneTrack.Sample sample = track.sample(1_250);

        assertEquals(-3.125f, sample.cameraFov(), 1.0e-4f);
    }

    private static SceneTrack.Frame frame(int tick, double x, double y, double z, float yaw) {
        return new SceneTrack.Frame(tick, x, y, z, yaw, 0, "walk", 0, 0, 0, 0);
    }

    private static SceneTrack.Frame frameWithFov(int tick, float fov) {
        return new SceneTrack.Frame(tick, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0, fov);
    }
}
