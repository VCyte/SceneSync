package io.github.vcyte.scenesync.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneClockMathTest {
    @Test void estimatesClockOffsetFromBothEndsOfTheHandshake() {
        // Client clock is 2 ms behind server; server processing takes 4 ms.
        assertEquals(2, SceneClockMath.estimateOffset(1_000, 1_100, 1_050, 1_054));
        assertEquals(96, SceneClockMath.networkRoundTrip(1_000, 1_100, 1_050, 1_054));
    }

    @Test void keepsTheLeastDelayedClockSampleAndRejectsInvalidRoundTrips() {
        assertTrue(SceneClockMath.isBetterSample(40, Long.MAX_VALUE));
        assertFalse(SceneClockMath.isBetterSample(55, 40));
        assertFalse(SceneClockMath.isBetterSample(-1, 40));
        assertTrue(SceneClockMath.isBetterSample(20, 40));
    }
}
