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

    @Test void stationaryScenePreservesAllCardinalStartingDirections() {
        assertEquals(0.0f, stationaryYaw(0), 1.0e-4f);       // south
        assertEquals(-180.0f, stationaryYaw(180), 1.0e-4f); // north (wrapped equivalent)
        assertEquals(90.0f, stationaryYaw(90), 1.0e-4f);   // west
        assertEquals(-90.0f, stationaryYaw(-90), 1.0e-4f); // east
    }

    @Test void movingSceneUsesWorldTravelDirectionForEveryCardinalBasis() {
        // Authored local +Z is forward. Rotating the actor basis must rotate both
        // the path and the resulting body yaw, without a hidden 90-degree offset.
        assertEquals(0.0f, movingYaw(0), 1.0e-4f);       // south
        assertEquals(-180.0f, movingYaw(180), 1.0e-4f);   // north (wrapped equivalent)
        assertEquals(90.0f, movingYaw(90), 1.0e-4f);     // west
        assertEquals(-90.0f, movingYaw(-90), 1.0e-4f);   // east
    }

    @Test void fovOffsetUsesSmoothEasingBetweenKeyframes() {
        SceneTrack track = new SceneTrack("run-3", 9, 20, 1_000,
                0, 64, 0, 0,
                List.of(frameWithFov(0, 0), frameWithFov(20, -20)));

        SceneTrack.Sample sample = track.sample(1_250);

        assertEquals(-3.125f, sample.cameraFov(), 1.0e-4f);
    }

    @Test void preservesLimitedCameraControlInSample() {
        SceneTrack track = new SceneTrack("run-4", 10, 20, 1_000,
                0, 64, 0, 0, new SceneTrack.CameraControl(SceneTrack.CameraMode.LIMITED, 80, 45),
                List.of(frame(0, 0, 0, 0, 0), frame(20, 0, 0, 2, 0)));

        SceneTrack.Sample sample = track.sample(1_500);

        assertEquals(SceneTrack.CameraMode.LIMITED, sample.cameraMode());
        assertEquals(80.0f, sample.maxYawDegrees());
        assertEquals(45.0f, sample.maxPitchDegrees());
    }

    @Test void interpolatesPluginPoseAndUsesShortestRotationPath() {
        SceneTrack.Bone startArm = new SceneTrack.Bone(0, 0, 0, 170, 0, 0);
        SceneTrack.Bone endArm = new SceneTrack.Bone(2, 0, 0, -170, 0, 0);
        SceneTrack.Pose startPose = new SceneTrack.Pose(SceneTrack.Pose.NEUTRAL.head(),
                SceneTrack.Pose.NEUTRAL.body(), SceneTrack.Pose.NEUTRAL.leftArm(), startArm,
                SceneTrack.Pose.NEUTRAL.leftLeg(), SceneTrack.Pose.NEUTRAL.rightLeg());
        SceneTrack.Pose endPose = new SceneTrack.Pose(SceneTrack.Pose.NEUTRAL.head(),
                SceneTrack.Pose.NEUTRAL.body(), SceneTrack.Pose.NEUTRAL.leftArm(), endArm,
                SceneTrack.Pose.NEUTRAL.leftLeg(), SceneTrack.Pose.NEUTRAL.rightLeg());
        SceneTrack track = new SceneTrack("run-pose", 11, 20, 1_000,
                0, 64, 0, 0, List.of(
                posedFrame(0, "plugin:windup", startPose),
                posedFrame(20, "plugin:slash", endPose)));

        SceneTrack.Sample sample = track.sample(1_500);

        assertEquals(1.0f, sample.pose().rightArm().x(), 1.0e-6f);
        assertEquals(180.0f, sample.pose().rightArm().pitch(), 1.0e-4f);
    }

    @Test void easesPoseAndCameraEffectsAtBothSceneBoundaries() {
        SceneTrack track = new SceneTrack("run-envelope", 12, 20, 1_000,
                0, 64, 0, 0, List.of(frameWithFov(0, -20), frameWithFov(20, -20)));

        assertEquals(0.0f, track.sample(1_000).sceneBlend(), 1.0e-6f);
        assertEquals(1.0f, track.sample(1_300).sceneBlend(), 1.0e-6f);
        assertEquals(1.0f, track.sample(1_700).sceneBlend(), 1.0e-6f);
        assertEquals(0.0f, track.sample(2_000).sceneBlend(), 1.0e-6f);
    }
    @Test void preRollClampsToFrameZeroInsteadOfFallingBeforeTheSharedStart() {
        SceneTrack track = new SceneTrack("run-preroll", 12, 20, 2_000,
                10, 70, 20, 0,
                List.of(frame(0, 0, 0, 0, 0), frame(20, 0, 0, 8, 0)));
        SceneTrack.Sample preRoll = track.sample(1_900);
        assertEquals(10.0, preRoll.worldX(), 1.0e-6);
        assertEquals(70.0, preRoll.worldY(), 1.0e-6);
        assertEquals(20.0, preRoll.worldZ(), 1.0e-6);
        assertEquals(0.0f, preRoll.sceneBlend(), 1.0e-6f);
    }

    @Test void smoothPathUsesFractionalTimelineAndZeroVelocityAtItsStart() {
        SceneTrack track = new SceneTrack("run-smooth", 13, 24, 1_000,
                0, 64, 0, 0, List.of(
                frame(0, 0, 0, 0, 0), frame(14, 0, 0, 0, 0), frame(16, 0, 0, 0, 0),
                frame(18, 0, 0, 8, 0), frame(20, 0, 0, 16, 0), frame(22, 0, 0, 24, 0),
                frame(24, 0, 0, 24, 0)));

        assertEquals(0.0, track.sample(1_800).worldZ(), 1.0e-6);
        SceneTrack.Sample betweenTicks = track.sample(1_875);
        assertEquals(5.625, betweenTicks.worldZ(), 1.0e-6);
        assertEquals(24.0, track.sample(2_200).worldZ(), 1.0e-6);
    }

    @Test void keyframeAnimationIdsCrossfadeInsteadOfChangingAsAOneFrameSwitch() {
        SceneTrack track = new SceneTrack("run-animation-blend", 14, 20, 1_000,
                0, 64, 0, 0, List.of(
                frameNamed(0, "bow"), frameNamed(20, "wave")));

        SceneTrack.Sample middle = track.sample(1_500);
        assertEquals("bow", middle.animation());
        assertEquals("wave", middle.nextAnimation());
        assertEquals(0.5f, middle.animationBlend(), 1.0e-6f);
    }

    @Test void actorFacingEasesInAndTurnsAcrossAPathCornerInsteadOfSnapping() {
        SceneTrack turn = new SceneTrack("run-turn", 15, 20, 1_000,
                0, 64, 0, 0, List.of(
                frame(0, 0, 0, 0, 0), frame(10, 0, 0, 10, 0), frame(20, 10, 0, 10, 0)));
        assertEquals(0.0f, turn.sample(1_000).yaw(), 1.0e-4f);
        assertEquals(-45.0f, turn.sample(1_500).yaw(), 0.1f);
        assertEquals(-90.0f, turn.sample(1_600).yaw(), 0.1f);

        SceneTrack turnAtStart = new SceneTrack("run-turn-entry", 16, 10, 1_000,
                0, 64, 0, 0, List.of(frame(0, 0, 0, 0, 0), frame(10, 10, 0, 0, 0)));
        assertEquals(0.0f, turnAtStart.sample(1_000).yaw(), 1.0e-4f);
        assertEquals(-90.0f, turnAtStart.sample(1_150).yaw(), 0.1f);
    }

    private static SceneTrack.Frame frame(int tick, double x, double y, double z, float yaw) {
        return new SceneTrack.Frame(tick, x, y, z, yaw, 0, "walk", 0, 0, 0, 0);
    }

    private static float stationaryYaw(float originYaw) {
        SceneTrack track = new SceneTrack("stationary-" + originYaw, 20, 20, 1_000,
                0, 64, 0, originYaw,
                List.of(frame(0, 0, 0, 0, 0), frame(20, 0, 0, 0, 0)));
        return track.sample(1_500).yaw();
    }

    private static float movingYaw(float originYaw) {
        SceneTrack track = new SceneTrack("moving-" + originYaw, 21, 20, 1_000,
                0, 64, 0, originYaw,
                List.of(frame(0, 0, 0, 0, 0), frame(20, 0, 0, 2, 0)));
        return track.sample(1_500).yaw();
    }

    private static SceneTrack.Frame frameWithFov(int tick, float fov) {
        return new SceneTrack.Frame(tick, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0, fov);
    }

    private static SceneTrack.Frame frameNamed(int tick, String animation) {
        return new SceneTrack.Frame(tick, 0, 0, 0, 0, 0, animation, 0, 0, 0, 0);
    }

    private static SceneTrack.Frame posedFrame(int tick, String animation, SceneTrack.Pose pose) {
        return new SceneTrack.Frame(tick, 0, 0, 0, 0, 0, animation,
                0, 0, 0, 0, 0, pose);
    }
}
