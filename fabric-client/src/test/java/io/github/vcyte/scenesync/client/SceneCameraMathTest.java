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

    @Test void excessMouseTravelIsDiscardedAtTheEdgeSoReversingRespondsImmediately() {
        float atEdge = SceneCameraMath.clampUserOffset(500.0f, 30.0f, 55.0f);
        assertEquals(25.0f, atEdge, 1.0e-5f);
        assertEquals(24.0f, SceneCameraMath.clampUserOffset(atEdge - 1.0f, 30.0f, 55.0f), 1.0e-5f);
    }

    @Test void frontThirdPersonCameraMirrorsYawAndPitchLikeVanilla() {
        SceneCameraMath.Look front = SceneCameraMath.forCamera(35.0f, 22.0f, true);
        assertEquals(-145.0f, front.yaw(), 1.0e-5f);
        assertEquals(-22.0f, front.pitch(), 1.0e-5f);
        SceneCameraMath.Look normal = SceneCameraMath.forCamera(35.0f, 22.0f, false);
        assertEquals(35.0f, normal.yaw(), 1.0e-5f);
        assertEquals(22.0f, normal.pitch(), 1.0e-5f);
    }
}
