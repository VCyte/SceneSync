package io.github.vcyte.scenesync.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SceneDefinitionTest {
    private static SceneFrame frame(int tick) {
        return new SceneFrame(tick, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0);
    }

    @Test void acceptsValidTimelineAndCopiesFrames() {
        var input = new java.util.ArrayList<>(List.of(frame(0), frame(20)));
        var definition = new SceneDefinition("intro", 40, input);
        input.clear();
        assertEquals(2, definition.frames().size());
    }
    @Test void defaultsToCancelOnHitAndAllowsDamageIgnorePolicy() {
        assertEquals(DamagePolicy.CANCEL_SCENE, new SceneDefinition("intro", 40, List.of(frame(0))).damagePolicy());
        assertEquals(DamagePolicy.IGNORE_DAMAGE,
                new SceneDefinition("intro", 40, List.of(frame(0)), DamagePolicy.IGNORE_DAMAGE).damagePolicy());
    }
    @Test void choosesDamagePolicyForAnimationActiveAtHitTick() {
        var wave = new SceneFrame(20, 0, 0, 0, 0, 0, "wave", 0, 0, 0, 0);
        var scene = new SceneDefinition("intro", 40, List.of(frame(0), wave), DamagePolicy.CANCEL_SCENE,
                Map.of("wave", DamagePolicy.IGNORE_DAMAGE));

        assertEquals(DamagePolicy.CANCEL_SCENE, scene.damagePolicyAtTick(19));
        assertEquals(DamagePolicy.IGNORE_DAMAGE, scene.damagePolicyAtTick(20));
        assertEquals(DamagePolicy.IGNORE_DAMAGE, scene.damagePolicyAtTick(39));
    }
    @Test void rejectsDamageOverridesForUnusedAnimations() {
        assertThrows(IllegalArgumentException.class, () -> new SceneDefinition("intro", 40, List.of(frame(0)),
                DamagePolicy.CANCEL_SCENE, Map.of("wave", DamagePolicy.IGNORE_DAMAGE)));
    }
    @Test void cameraDefaultsToFreeLookAndValidatesForwardConeBounds() {
        var defaultScene = new SceneDefinition("intro", 40, List.of(frame(0)));
        assertEquals(CameraMode.FREE_LOOK, defaultScene.cameraSettings().mode());
        assertEquals(80.0f, defaultScene.cameraSettings().maxYawDegrees());
        var controlled = new SceneCameraSettings(CameraMode.LIMITED, 80, 45);
        assertEquals(CameraMode.LIMITED, controlled.mode());
        assertThrows(IllegalArgumentException.class, () -> new SceneCameraSettings(CameraMode.LIMITED, 101, 45));
    }
    @Test void rejectsNonzeroInitialFrame() {
        assertThrows(IllegalArgumentException.class, () -> new SceneDefinition("intro", 40, List.of(frame(1))));
    }
    @Test void rejectsUnorderedFrames() {
        assertThrows(IllegalArgumentException.class, () -> new SceneDefinition("intro", 40, List.of(frame(0), frame(0))));
    }
    @Test void rejectsExcessiveCameraShake() {
        assertThrows(IllegalArgumentException.class,
                () -> new SceneFrame(0, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 11));
    }
    @Test void acceptsBoundedFovAndLegacyConstructorDefaultsToNeutral() {
        assertEquals(0.0f, frame(0).cameraFov());
        assertEquals(-25.0f, new SceneFrame(0, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0, -25).cameraFov());
    }
    @Test void rejectsExcessiveCameraFovOffset() {
        assertThrows(IllegalArgumentException.class,
                () -> new SceneFrame(0, 0, 0, 0, 0, 0, "idle", 0, 0, 0, 0, 26));
    }
    @Test void rejectsNonfiniteMovement() {
        assertThrows(IllegalArgumentException.class,
                () -> new SceneFrame(0, Double.NaN, 0, 0, 0, 0, "idle", 0, 0, 0, 0));
    }

    @Test void rejectsUnsupportedAnimation() {
        assertThrows(IllegalArgumentException.class,
                () -> new SceneFrame(0, 0, 0, 0, 0, 0, "arbitrary", 0, 0, 0, 0));
    }
}
