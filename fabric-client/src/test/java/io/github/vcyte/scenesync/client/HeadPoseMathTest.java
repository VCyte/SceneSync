package io.github.vcyte.scenesync.client;

import io.github.vcyte.scenesync.client.animation.HeadPoseMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HeadPoseMathTest {
    @Test void clampsTheCombinedPoseAndCameraOverlayRelativeToVanillaHead() {
        float vanillaPitch = (float)Math.toRadians(25.0);
        float combinedPitch = (float)Math.toRadians(25.0 + 180.0 + 45.0);
        assertEquals(vanillaPitch + HeadPoseMath.MAX_PITCH_RADIANS,
                HeadPoseMath.clampOverlay(vanillaPitch, combinedPitch, HeadPoseMath.MAX_PITCH_RADIANS), 1e-6f);
    }

    @Test void preservesVanillaHeadDirectionWhenScriptedOffsetIsNeutral() {
        float vanillaYaw = (float)Math.toRadians(40.0);
        assertEquals(vanillaYaw, HeadPoseMath.clampOverlay(vanillaYaw, vanillaYaw, HeadPoseMath.MAX_YAW_RADIANS), 1e-6f);
    }

    @Test void clampsExtremeYawAndRollWithoutFlippingAcrossTheNeck() {
        float vanilla = 0.1f;
        assertEquals(vanilla - HeadPoseMath.MAX_YAW_RADIANS,
                HeadPoseMath.clampOverlay(vanilla, vanilla - 4.0f, HeadPoseMath.MAX_YAW_RADIANS), 1e-6f);
        assertEquals(vanilla + HeadPoseMath.MAX_ROLL_RADIANS,
                HeadPoseMath.clampOverlay(vanilla, vanilla + 3.0f, HeadPoseMath.MAX_ROLL_RADIANS), 1e-6f);
    }
}
