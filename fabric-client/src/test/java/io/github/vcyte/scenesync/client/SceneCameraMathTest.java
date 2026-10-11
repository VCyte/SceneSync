package io.github.vcyte.scenesync.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SceneCameraMathTest {
    @Test void limitedLookCannotCrossTheForwardYawConeEvenWithCameraOffset() {
        SceneCameraMath.Look look = SceneCameraMath.limitedLook(0, 0, 45, 0, 50, 0, 80, 45);
        assertEquals(80.0f, look.yaw(), 1.0e-5f);
    }

    @Test void limitedLookWrapsAcrossTheMinus180BoundaryWithoutAllowingRearwardView() {
        SceneCameraMath.Look look = SceneCameraMath.limitedLook(170, 0, 0, 0, 100, 0, 80, 45);
        assertEquals(80.0f, SceneCameraMath.wrap(look.yaw() - 170), 1.0e-5f);
    }

    @Test void limitedLookHonorsZeroAndVerticalBounds() {
        SceneCameraMath.Look zero = SceneCameraMath.limitedLook(25, 5, 30, 20, 80, 80, 0, 0);
        assertEquals(25.0f, zero.yaw(), 1.0e-5f);
        assertEquals(5.0f, zero.pitch(), 1.0e-5f);

        SceneCameraMath.Look vertical = SceneCameraMath.limitedLook(0, 0, 0, 0, 0, 80, 80, 45);
        assertEquals(45.0f, vertical.pitch(), 1.0e-5f);
    }
}
