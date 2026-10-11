package io.github.vcyte.scenesync.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneControllerTest {
    @AfterEach void clearTracks() { SceneController.clear(); }

    @Test void acceptsPluginDefinedNamespacedAnimationsAndGenericPosePacket() {
        int entityId = 2_100_001;
        long start = System.currentTimeMillis() + 1_000;
        JsonArray frames = new JsonArray();
        frames.add(frame(0, "example:slash_charge", 0, -35.0f));
        frames.add(frame(16, "example:slash", 0, -120.0f));
        frames.add(frame(22, "example:slash_finish", 12, -165.0f));
        frames.add(frame(36, "idle", 12, null));
        JsonObject packet = packet(entityId, start, 36, frames);

        assertTrue(SceneController.acceptPlay(packet, 0));
        SceneTrack.Sample charge = SceneController.sample(entityId, start);
        SceneTrack.Sample transition = SceneController.sample(entityId, start + 8 * 50L);
        SceneTrack.Sample strike = SceneController.sample(entityId, start + 16 * 50L);
        assertNotNull(charge);
        assertEquals("example:slash_charge", charge.animation());
        assertEquals(SceneTrack.CameraMode.LOCKED, charge.cameraMode());
        assertEquals(-35.0f, charge.pose().rightArm().pitch());
        assertEquals("example:slash_charge", transition.animation());
        assertEquals(-77.5f, transition.pose().rightArm().pitch(), 0.001f);
        assertEquals("example:slash", strike.animation());
        assertEquals(-120.0f, strike.pose().rightArm().pitch());
    }

    @Test void rejectsUnposedCustomLabelsAndOutOfRangeTransforms() {
        long start = System.currentTimeMillis() + 1_000;
        JsonArray missingPose = new JsonArray();
        missingPose.add(frame(0, "example:missing", 0, null));
        assertFalse(SceneController.acceptPlay(packet(2_100_002, start, 1, missingPose), 0));

        JsonArray invalidTransform = new JsonArray();
        invalidTransform.add(frame(0, "example:overflow", 0, 180.1f));
        assertFalse(SceneController.acceptPlay(packet(2_100_003, start, 1, invalidTransform), 0));
    }

    @Test void earlyStopUnlocksMovementImmediatelyButFadesTheFrozenPose() {
        int entityId = 2_100_004;
        long now = System.currentTimeMillis();
        long start = now - 500;
        JsonArray frames = new JsonArray();
        frames.add(frame(0, "example:charge", 0, -60.0f));
        frames.add(frame(36, "example:charge", 0, -60.0f));
        assertTrue(SceneController.acceptPlay(packet(entityId, start, 36, frames), 0));
        SceneTrack.Sample before = SceneController.sample(entityId, now);
        assertNotNull(before);
        assertTrue(before.sceneBlend() > 0.99f);

        JsonObject stop = new JsonObject();
        stop.addProperty("protocol", 3);
        stop.addProperty("entityId", entityId);
        stop.addProperty("runId", "pluginpose" + entityId);
        SceneController.stop(stop);

        assertFalse(SceneController.controlsInput(entityId, now));
        SceneTrack.Sample fading = SceneController.visualSample(entityId, now + 100);
        assertNotNull(fading);
        assertEquals(before.pose(), fading.pose());
        assertTrue(fading.sceneBlend() > 0.0f && fading.sceneBlend() < before.sceneBlend());
        assertEquals(null, SceneController.visualSample(entityId, now + 500));
    }

    private static JsonObject packet(int entityId, long start, int duration, JsonArray frames) {
        JsonObject packet = new JsonObject();
        packet.addProperty("protocol", 3);
        packet.addProperty("entityId", entityId);
        packet.addProperty("runId", "pluginpose" + entityId);
        packet.addProperty("durationTicks", duration);
        packet.addProperty("startAtMillis", start);
        packet.addProperty("originX", 0);
        packet.addProperty("originY", 64);
        packet.addProperty("originZ", 0);
        packet.addProperty("originYaw", 0);
        JsonObject camera = new JsonObject();
        camera.addProperty("mode", "LOCKED");
        camera.addProperty("maxYawDegrees", 80);
        camera.addProperty("maxPitchDegrees", 45);
        packet.add("cameraControl", camera);
        packet.add("frames", frames);
        return packet;
    }

    private static JsonObject frame(int tick, String animation, double z, Float rightArmPitch) {
        JsonObject frame = new JsonObject();
        frame.addProperty("tick", tick);
        frame.addProperty("x", 0);
        frame.addProperty("y", 0);
        frame.addProperty("z", z);
        frame.addProperty("animation", animation);
        if (rightArmPitch != null) frame.add("pose", pose(rightArmPitch));
        return frame;
    }

    private static JsonArray pose(float rightArmPitch) {
        JsonArray pose = new JsonArray();
        pose.add(bone(0)); pose.add(bone(0));
        pose.add(bone(0)); pose.add(bone(rightArmPitch));
        pose.add(bone(0)); pose.add(bone(0));
        return pose;
    }

    private static JsonArray bone(float pitch) {
        JsonArray values = new JsonArray();
        values.add(0); values.add(0); values.add(0);
        values.add(pitch); values.add(0); values.add(0);
        return values;
    }
}
