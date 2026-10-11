package io.github.vcyte.scenesync.client;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SceneControllerTest {
    @AfterEach void clearTracks() { SceneController.clear(); }

    @Test void acceptsThunderclapTimelineAndLockedCameraPacket() {
        int entityId = 2_100_001;
        long start = System.currentTimeMillis() + 1_000;
        String raw = """
                {
                  "protocol": 2,
                  "entityId": %d,
                  "runId": "thunderclap1",
                  "durationTicks": 36,
                  "startAtMillis": %d,
                  "originX": 0,
                  "originY": 64,
                  "originZ": 0,
                  "originYaw": 0,
                  "cameraControl": {"mode":"LOCKED","maxYawDegrees":80,"maxPitchDegrees":45},
                  "frames": [
                    {"tick":0,"x":0,"y":0,"z":0,"animation":"lightning_charge"},
                    {"tick":16,"x":0,"y":0,"z":0,"animation":"lightning_dash"},
                    {"tick":22,"x":0,"y":0,"z":12,"animation":"lightning_finish"},
                    {"tick":36,"x":0,"y":0,"z":12,"animation":"idle"}
                  ]
                }
                """.formatted(entityId, start);

        assertTrue(SceneController.acceptPlay(JsonParser.parseString(raw).getAsJsonObject(), 0));
        SceneTrack.Sample charge = SceneController.sample(entityId, start);
        SceneTrack.Sample dash = SceneController.sample(entityId, start + 16 * 50L);
        SceneTrack.Sample finish = SceneController.sample(entityId, start + 22 * 50L);
        assertNotNull(charge);
        assertEquals("lightning_charge", charge.animation());
        assertEquals(SceneTrack.CameraMode.LOCKED, charge.cameraMode());
        assertEquals("lightning_dash", dash.animation());
        assertEquals("lightning_finish", finish.animation());
    }
}
