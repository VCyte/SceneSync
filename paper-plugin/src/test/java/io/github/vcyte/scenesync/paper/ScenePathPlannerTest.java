package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneFrame;
import io.github.vcyte.scenesync.api.SceneDefinition;
import io.github.vcyte.scenesync.api.DamagePolicy;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ScenePathPlannerTest {
    @Test void holdsAtTheLastSafeKeyframeInsteadOfSendingTheActorIntoTheWall() {
        List<SceneFrame> source = List.of(frame(0, 0), frame(16, 0), frame(18, 8), frame(20, 16), frame(22, 24));
        List<SceneFrame> safe = ScenePathPlanner.holdAt(source, 18, new ScenePathMath.Point(0, 0, 8));

        assertEquals(List.of(0, 16, 18, 20, 22), safe.stream().map(SceneFrame::tick).toList());
        assertEquals(8.0, safe.get(2).z());
        assertEquals(8.0, safe.get(3).z());
        assertEquals(8.0, safe.get(4).z());
        assertEquals("scenesync:dash", safe.get(4).animation());
        assertNotNull(safe.get(4).pose());
    }

    @Test void insertsAStopKeyframeWhenTheCollisionTickIsBetweenAuthoredKeys() {
        List<SceneFrame> source = List.of(frame(0, 0), frame(16, 0), frame(20, 16), frame(24, 24));
        List<SceneFrame> safe = ScenePathPlanner.holdAt(source, 18, new ScenePathMath.Point(0, 0, 8));
        assertEquals(List.of(0, 16, 18, 20, 24), safe.stream().map(SceneFrame::tick).toList());
        assertEquals(8.0, safe.get(2).z());
        assertEquals(8.0, safe.get(3).z());
    }

    @Test void obstructionPlanCreatesAStationaryBraceAndRecoveryWithoutMovingThroughTheWall() {
        SceneDefinition source = new SceneDefinition("dash", 22,
                List.of(frame(0, 0), frame(16, 0), frame(18, 8), frame(20, 16), frame(22, 24)),
                DamagePolicy.CANCEL_SCENE);
        SceneDefinition planned = ScenePathPlanner.stopAndBrace(source, 18, new ScenePathMath.Point(0, 0, 8));

        assertEquals(22, planned.durationTicks());
        assertEquals(List.of(0, 16, 18, 19, 21, 22), planned.frames().stream().map(SceneFrame::tick).toList());
        assertEquals("scenesync:wall_brace", planned.frames().get(3).animation());
        assertNotNull(planned.frames().get(3).pose());
        assertEquals("idle", planned.frames().getLast().animation());
        assertEquals(8.0, planned.frames().stream().mapToDouble(SceneFrame::z).max().orElseThrow());
    }

    private static SceneFrame frame(int tick, double z) {
        return new SceneFrame(tick, 0, 0, z, 0, 0, "scenesync:dash", 0, 0, 0, 0, 0,
                io.github.vcyte.scenesync.api.ModelPose.NEUTRAL);
    }
}
