package io.github.vcyte.scenesync.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public final class SceneController {
    private static final Pattern ID = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Set<String> POSES = Set.of("idle", "walk", "run", "wave", "bow", "spin", "clap", "sit",
            "lightning_charge", "lightning_dash", "lightning_finish");
    private static final ConcurrentHashMap<Integer, SceneTrack> TRACKS = new ConcurrentHashMap<>();
    private SceneController() { }

    static Optional<JsonObject> parse(byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > BridgePayloads.MAX_BYTES) return Optional.empty();
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (text.length() > BridgePayloads.MAX_BYTES || !boundedNesting(text, 16)) return Optional.empty();
        try {
            JsonElement element = JsonParser.parseString(text);
            return element.isJsonObject() ? Optional.of(element.getAsJsonObject()) : Optional.empty();
        } catch (RuntimeException ignored) { return Optional.empty(); }
    }

    static boolean acceptPlay(JsonObject json, long serverOffsetMillis) {
        try {
            int protocol = integer(json, "protocol", -1, 2, 2);
            int entityId = integer(json, "entityId", -1, 0, Integer.MAX_VALUE);
            int duration = integer(json, "durationTicks", -1, 1, 24_000);
            String runId = string(json, "runId", 64);
            long serverStart = longValue(json, "startAtMillis", -1, 0, Long.MAX_VALUE);
            long now = System.currentTimeMillis();
            long localStart = serverStart - serverOffsetMillis;
            double originX = decimal(json, "originX", Double.NaN, -30_000_000, 30_000_000);
            double originY = decimal(json, "originY", Double.NaN, -2048, 2048);
            double originZ = decimal(json, "originZ", Double.NaN, -30_000_000, 30_000_000);
            float originYaw = (float)decimal(json, "originYaw", Double.NaN, -360, 360);
            JsonArray source = json.getAsJsonArray("frames");
            if (protocol != 2 || entityId < 0 || runId == null || !ID.matcher(runId).matches()
                    || duration < 1 || source == null || source.isEmpty() || source.size() > 128
                    || !Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                    || !Float.isFinite(originYaw) || localStart < now - 120_000L || localStart > now + 120_000L) return false;
            List<SceneTrack.Frame> frames = new java.util.ArrayList<>(source.size());
            int previous = -1;
            for (JsonElement item : source) {
                if (!item.isJsonObject()) return false;
                JsonObject f = item.getAsJsonObject();
                int tick = integer(f, "tick", -1, 0, duration);
                double x = decimal(f, "x", 0, -128, 128);
                double y = decimal(f, "y", 0, -128, 128);
                double z = decimal(f, "z", 0, -128, 128);
                float yaw = (float) decimal(f, "yaw", 0, -360, 360);
                float pitch = (float) decimal(f, "pitch", 0, -90, 90);
                String animation = string(f, "animation", 32);
                float cameraYaw = (float) decimal(f, "cameraYaw", 0, -45, 45);
                float cameraPitch = (float) decimal(f, "cameraPitch", 0, -45, 45);
                float cameraRoll = (float) decimal(f, "cameraRoll", 0, -45, 45);
                float shake = (float) decimal(f, "cameraShake", 0, 0, 10);
                float cameraFov = (float) decimal(f, "cameraFov", 0, -25, 25);
                if (tick <= previous || animation == null || !POSES.contains(animation)) return false;
                frames.add(new SceneTrack.Frame(tick, x, y, z, yaw, pitch, animation,
                        cameraYaw, cameraPitch, cameraRoll, shake, cameraFov));
                previous = tick;
            }
            if (frames.getFirst().tick() != 0) return false;
            SceneTrack.CameraControl cameraControl = SceneTrack.CameraControl.DEFAULT;
            if (json.has("cameraControl")) {
                if (!json.get("cameraControl").isJsonObject()) return false;
                JsonObject camera = json.getAsJsonObject("cameraControl");
                String rawMode = string(camera, "mode", 16);
                double maxYaw = decimal(camera, "maxYawDegrees", Double.NaN, 0, 100);
                double maxPitch = decimal(camera, "maxPitchDegrees", Double.NaN, 0, 70);
                if (rawMode == null || !Double.isFinite(maxYaw) || !Double.isFinite(maxPitch)) return false;
                SceneTrack.CameraMode mode = SceneTrack.CameraMode.valueOf(rawMode);
                cameraControl = new SceneTrack.CameraControl(mode, (float)maxYaw, (float)maxPitch);
            }
            TRACKS.put(entityId, new SceneTrack(runId, entityId, duration, localStart,
                    originX, originY, originZ, originYaw, cameraControl, List.copyOf(frames)));
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    static void stop(JsonObject json) {
        if (integer(json, "protocol", -1, 2, 2) != 2) return;
        int id = integer(json, "entityId", -1, 0, Integer.MAX_VALUE);
        String runId = string(json, "runId", 64);
        if (id < 0 || runId == null || !ID.matcher(runId).matches()) return;
        TRACKS.computeIfPresent(id, (key, track) -> track.runId().equals(runId) ? null : track);
    }

    public static SceneTrack.Sample sample(int entityId, long now) {
        SceneTrack track = TRACKS.get(entityId);
        if (track == null) return null;
        SceneTrack.Sample sample = track.sample(now);
        if (sample == null && now >= track.localStartMillis() + (long) track.durationTicks() * 50L)
            TRACKS.remove(entityId, track);
        return sample;
    }

    public static boolean controlsInput(int entityId, long now) {
        SceneTrack track = TRACKS.get(entityId);
        if (track == null) return false;
        long end = track.localStartMillis() + (long)track.durationTicks() * 50L;
        if (now <= end) return true;
        TRACKS.remove(entityId, track);
        return false;
    }

    static void clear() { TRACKS.clear(); }
    static int activeTracks() { return TRACKS.size(); }

    private static int integer(JsonObject o, String key, int fallback, int min, int max) {
        try { if (!o.has(key) || !o.get(key).getAsJsonPrimitive().isNumber()) return fallback;
            long v = o.get(key).getAsLong(); return v >= min && v <= max ? (int) v : fallback;
        } catch (RuntimeException e) { return fallback; }
    }
    private static long longValue(JsonObject o, String key, long fallback, long min, long max) {
        try { if (!o.has(key) || !o.get(key).getAsJsonPrimitive().isNumber()) return fallback;
            long v = o.get(key).getAsLong(); return v >= min && v <= max ? v : fallback;
        } catch (RuntimeException e) { return fallback; }
    }
    private static String string(JsonObject o, String key, int max) {
        try { if (!o.has(key) || !o.get(key).isJsonPrimitive() || !o.get(key).getAsJsonPrimitive().isString()) return null;
            String v = o.get(key).getAsString(); return v.length() <= max ? v : null;
        } catch (RuntimeException e) { return null; }
    }
    private static double decimal(JsonObject o, String key, double fallback, double min, double max) {
        try { if (!o.has(key) || !o.get(key).getAsJsonPrimitive().isNumber()) return fallback;
            double v = o.get(key).getAsDouble(); return Double.isFinite(v) && v >= min && v <= max ? v : fallback;
        } catch (RuntimeException e) { return fallback; }
    }
    private static boolean boundedNesting(String s, int max) {
        int depth = 0; boolean quoted = false; boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) { if (escaped) escaped = false; else if (c == '\\') escaped = true; else if (c == '"') quoted = false; continue; }
            if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > max) return false; }
            else if (c == '}' || c == ']') { if (--depth < 0) return false; }
        }
        return !quoted && depth == 0;
    }
}
