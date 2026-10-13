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
    private static final Pattern ANIMATION_ID = Pattern.compile("[a-z0-9][a-z0-9._:-]{0,95}");
    private static final Set<String> BUILT_IN_POSES = Set.of("idle", "walk", "run", "wave", "bow", "spin", "clap", "sit");
    private static final ConcurrentHashMap<Integer, SceneTrack> TRACKS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, String> DYNAMIC_RUNS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, PoseFade> POSE_FADES = new ConcurrentHashMap<>();
    private static final long POSE_FADE_MILLIS = 200L;
    private static int detachedLookEntity = -1;
    private static float detachedLookYaw;
    private static float detachedLookPitch;
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
            int protocol = integer(json, "protocol", -1, 3, 3);
            int entityId = integer(json, "entityId", -1, 0, Integer.MAX_VALUE);
            int duration = integer(json, "durationTicks", -1, 1, 24_000);
            boolean dynamic = json.has("dynamic") && json.get("dynamic").getAsBoolean();
            String runId = string(json, "runId", 64);
            String phase = string(json, "phase", 16);
            long serverStart = longValue(json, "startAtMillis", -1, 0, Long.MAX_VALUE);
            long now = System.currentTimeMillis();
            long localStart = serverStart - serverOffsetMillis;
            double originX = decimal(json, "originX", Double.NaN, -30_000_000, 30_000_000);
            double originY = decimal(json, "originY", Double.NaN, -2048, 2048);
            double originZ = decimal(json, "originZ", Double.NaN, -30_000_000, 30_000_000);
            float originYaw = (float)decimal(json, "originYaw", Double.NaN, -360, 360);
            float originPitch = (float)decimal(json, "originPitch", 0.0, -90, 90);
            JsonArray source = json.getAsJsonArray("frames");
            if (protocol != 3 || entityId < 0 || runId == null || !ID.matcher(runId).matches()
                    || duration < 1 || source == null || source.isEmpty() || source.size() > 128
                    || !Double.isFinite(originX) || !Double.isFinite(originY) || !Double.isFinite(originZ)
                    || !Float.isFinite(originYaw) || !Float.isFinite(originPitch)
                    || localStart < now - 120_000L || localStart > now + 120_000L) return false;
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
                String animation = string(f, "animation", 96);
                float cameraYaw = (float) decimal(f, "cameraYaw", 0, -45, 45);
                float cameraPitch = (float) decimal(f, "cameraPitch", 0, -45, 45);
                float cameraRoll = (float) decimal(f, "cameraRoll", 0, -45, 45);
                float shake = (float) decimal(f, "cameraShake", 0, 0, 10);
                float cameraFov = (float) decimal(f, "cameraFov", 0, -25, 25);
                SceneTrack.Pose pose = f.has("pose") ? parsePose(f.get("pose")) : null;
                if (tick <= previous || animation == null || !ANIMATION_ID.matcher(animation).matches()
                        || (!BUILT_IN_POSES.contains(animation) && pose == null)) return false;
                frames.add(new SceneTrack.Frame(tick, x, y, z, yaw, pitch, animation,
                        cameraYaw, cameraPitch, cameraRoll, shake, cameraFov, pose));
                previous = tick;
            }
            if (!dynamic && frames.getFirst().tick() != 0) return false;
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
            SceneTrack parsed = new SceneTrack(runId, entityId, duration, localStart,
                    originX, originY, originZ, originYaw, originPitch, cameraControl, List.copyOf(frames));
            // Preparation is acknowledged without moving or animating the player. The
            // server sends the final origin and synchronized start after the acknowledgement.
            if ("prepare".equals(phase)) return true;
            TRACKS.put(entityId, parsed);
            if (dynamic) DYNAMIC_RUNS.put(entityId, runId); else DYNAMIC_RUNS.remove(entityId);
            POSE_FADES.remove(entityId);
            if (detachedLookEntity == entityId) detachedLookEntity = -1;
            return true;
        } catch (RuntimeException ignored) { return false; }
    }

    /** Append one bounded live frame to the dynamic scene's rolling timeline. */
    static void acceptDynamicUpdate(JsonObject json) {
        try {
            if (integer(json, "protocol", -1, 3, 3) != 3) return;
            int entityId = integer(json, "entityId", -1, 0, Integer.MAX_VALUE);
            String runId = string(json, "runId", 64);
            JsonObject raw = json.has("frame") && json.get("frame").isJsonObject()
                    ? json.getAsJsonObject("frame") : null;
            SceneTrack current = TRACKS.get(entityId);
            if (entityId < 0 || runId == null || raw == null || current == null
                    || !runId.equals(DYNAMIC_RUNS.get(entityId)) || !current.runId().equals(runId)) return;
            int tick = integer(raw, "tick", -1, 0, current.durationTicks());
            if (tick <= current.frames().getLast().tick()) return;
            String animation = string(raw, "animation", 96);
            if (animation == null || !ANIMATION_ID.matcher(animation).matches()) return;
            SceneTrack.Pose pose = raw.has("pose") ? parsePose(raw.get("pose")) : null;
            if (!BUILT_IN_POSES.contains(animation) && pose == null) return;
            SceneTrack.Frame next = new SceneTrack.Frame(tick,
                    decimal(raw, "x", 0, -128, 128), decimal(raw, "y", 0, -128, 128),
                    decimal(raw, "z", 0, -128, 128), (float)decimal(raw, "yaw", 0, -360, 360),
                    (float)decimal(raw, "pitch", 0, -90, 90), animation,
                    (float)decimal(raw, "cameraYaw", 0, -45, 45), (float)decimal(raw, "cameraPitch", 0, -45, 45),
                    (float)decimal(raw, "cameraRoll", 0, -45, 45), (float)decimal(raw, "cameraShake", 0, 0, 10),
                    (float)decimal(raw, "cameraFov", 0, -25, 25), pose);
            java.util.ArrayList<SceneTrack.Frame> frames = new java.util.ArrayList<>(current.frames());
            frames.add(next);
            while (frames.size() > 96) frames.removeFirst();
            TRACKS.replace(entityId, current, new SceneTrack(current.runId(), current.entityId(), current.durationTicks(),
                    current.localStartMillis(), current.originX(), current.originY(), current.originZ(),
                    current.originYaw(), current.originPitch(), current.cameraControl(), List.copyOf(frames)));
        } catch (RuntimeException ignored) { }
    }

    static void stop(JsonObject json) {
        if (integer(json, "protocol", -1, 3, 3) != 3) return;
        int id = integer(json, "entityId", -1, 0, Integer.MAX_VALUE);
        String runId = string(json, "runId", 64);
        if (id < 0 || runId == null || !ID.matcher(runId).matches()) return;
        long now = System.currentTimeMillis();
        TRACKS.computeIfPresent(id, (key, track) -> {
            if (!track.runId().equals(runId)) return track;
            SceneTrack.Sample last = track.sample(now);
            if (last == null && now >= track.localStartMillis())
                last = track.sample(track.localStartMillis() + (long)track.durationTicks() * 50L);
            if (last != null) POSE_FADES.put(id, new PoseFade(last, now, last.sceneBlend()));
            return null;
        });
        DYNAMIC_RUNS.remove(id, runId);
    }

    public static SceneTrack.Sample sample(int entityId, long now) {
        SceneTrack track = TRACKS.get(entityId);
        if (track == null) return null;
        SceneTrack.Sample sample = track.sample(now);
        if (sample == null && now >= track.localStartMillis() + (long) track.durationTicks() * 50L) {
            TRACKS.remove(entityId, track);
            DYNAMIC_RUNS.remove(entityId, track.runId());
            beginNaturalFade(entityId, track, now);
        }
        return sample;
    }

    /** Rendering-only tail; movement and camera controllers use sample() and unlock immediately. */
    public static SceneTrack.Sample visualSample(int entityId, long now) {
        SceneTrack.Sample active = sample(entityId, now);
        if (active != null) return active;
        VisualFade fade = visualFade(entityId, now);
        return fade == null ? null : fade.sample();
    }

    /** Short render-only root handoff used to reconcile the sampled endpoint with vanilla entity state. */
    public static VisualFade visualFade(int entityId, long now) {
        PoseFade fade = POSE_FADES.get(entityId);
        if (fade == null) return null;
        float t = Math.max(0.0f, Math.min(1.0f, (now - fade.startedAtMillis()) / (float)POSE_FADE_MILLIS));
        if (t >= 1.0f) {
            POSE_FADES.remove(entityId, fade);
            return null;
        }
        float smooth = t * t * (3.0f - 2.0f * t);
        return new VisualFade(fade.sample().withSceneBlend(fade.startBlend() * (1.0f - smooth)), 1.0f - smooth);
    }

    public static boolean controlsInput(int entityId, long now) {
        SceneTrack track = TRACKS.get(entityId);
        if (track == null) return false;
        // Scene playback owns the actor transform. FREE_LOOK and LIMITED only change
        // the local camera; they must never leak WASD, jump, sprint or sneak input
        // into the server-authoritative actor while the head animation is playing.
        long end = track.localStartMillis() + (long)track.durationTicks() * 50L;
        if (now <= end) return true;
        if (TRACKS.remove(entityId, track)) beginNaturalFade(entityId, track, now);
        return false;
    }

    /** Mouse look must not fight the scripted camera while the scene is LOCKED. */
    public static boolean blocksLook(int entityId, long now) {
        SceneTrack.Sample sample = sample(entityId, now);
        return sample != null && sample.cameraMode() == SceneTrack.CameraMode.LOCKED;
    }

    /** FREE_LOOK is a detached third-person camera; the actor head stays independent. */
    public static boolean usesDetachedLook(int entityId, long now) {
        SceneTrack.Sample sample = sample(entityId, now);
        return sample != null && sample.cameraMode() == SceneTrack.CameraMode.FREE_LOOK;
    }

    public static void beginDetachedLook(int entityId, float yaw, float pitch) {
        if (detachedLookEntity != entityId) {
            detachedLookEntity = entityId;
            detachedLookYaw = yaw;
            detachedLookPitch = pitch;
        }
    }

    public static Look detachedLook(int entityId) {
        return detachedLookEntity == entityId ? new Look(detachedLookYaw, detachedLookPitch) : null;
    }

    public static void updateDetachedLook(int entityId, double yawDelta, double pitchDelta) {
        if (detachedLookEntity != entityId) return;
        detachedLookYaw = wrapDegrees(detachedLookYaw + (float)yawDelta);
        detachedLookPitch = Math.max(-90.0f, Math.min(90.0f, detachedLookPitch + (float)pitchDelta));
    }

    /** LIMITED never wraps past its cone: excess input is discarded at the boundary. */
    public static void updateLimitedLook(int entityId, double yawDelta, double pitchDelta, long now) {
        if (detachedLookEntity != entityId) return;
        SceneTrack.Sample sample = sample(entityId, now);
        if (sample == null) return;
        float yawRelative = wrapDegrees(detachedLookYaw - sample.yaw() + (float) yawDelta);
        float pitchRelative = detachedLookPitch - sample.pitch() + (float) pitchDelta;
        yawRelative = Math.max(-sample.maxYawDegrees(), Math.min(sample.maxYawDegrees(), yawRelative));
        pitchRelative = Math.max(-sample.maxPitchDegrees(), Math.min(sample.maxPitchDegrees(), pitchRelative));
        detachedLookYaw = wrapDegrees(sample.yaw() + yawRelative);
        detachedLookPitch = Math.max(-90.0f, Math.min(90.0f, sample.pitch() + pitchRelative));
    }

    public static void clearDetachedLook(int entityId) {
        if (detachedLookEntity == entityId) detachedLookEntity = -1;
    }

    private static void beginNaturalFade(int entityId, SceneTrack track, long now) {
        long end = track.localStartMillis() + (long)track.durationTicks() * 50L;
        SceneTrack.Sample endpoint = track.sample(end);
        if (endpoint != null) {
            // sceneEnvelope intentionally reaches zero at the exact timeline end,
            // but the render-only handoff must start from the final authored pose,
            // not from an already-reset vanilla pose.
            SceneTrack.Sample held = endpoint.withSceneBlend(1.0f);
            POSE_FADES.putIfAbsent(entityId, new PoseFade(held, now, 1.0f));
        }
    }

    static void clear() { TRACKS.clear(); DYNAMIC_RUNS.clear(); POSE_FADES.clear(); detachedLookEntity = -1; }
    static int activeTracks() { return TRACKS.size(); }

    public record VisualFade(SceneTrack.Sample sample, float rootWeight) { }

    private static SceneTrack.Pose parsePose(JsonElement element) {
        if (!element.isJsonArray()) throw new IllegalArgumentException("pose must be an ordered array");
        JsonArray pose = element.getAsJsonArray();
        if (pose.size() != 6) throw new IllegalArgumentException("pose must contain six humanoid parts");
        return new SceneTrack.Pose(parseBone(pose, 0), parseBone(pose, 1), parseBone(pose, 2),
                parseBone(pose, 3), parseBone(pose, 4), parseBone(pose, 5));
    }

    private static SceneTrack.Bone parseBone(JsonArray pose, int index) {
        if (!pose.get(index).isJsonArray()) throw new IllegalArgumentException("pose part must be an array");
        JsonArray values = pose.get(index).getAsJsonArray();
        if (values.size() != 6) throw new IllegalArgumentException("pose part must have six values");
        return new SceneTrack.Bone(poseValue(values, 0, -8, 8), poseValue(values, 1, -8, 8),
                poseValue(values, 2, -8, 8), poseValue(values, 3, -180, 180),
                poseValue(values, 4, -180, 180), poseValue(values, 5, -180, 180));
    }

    private static float poseValue(JsonArray values, int index, float min, float max) {
        JsonElement element = values.get(index);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("pose values must be numeric");
        float value = element.getAsFloat();
        if (!Float.isFinite(value) || value < min || value > max)
            throw new IllegalArgumentException("pose value exceeds bounds");
        return value;
    }

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

    private static float wrapDegrees(float degrees) {
        degrees %= 360.0f;
        if (degrees >= 180.0f) degrees -= 360.0f;
        if (degrees < -180.0f) degrees += 360.0f;
        return degrees;
    }

    private record PoseFade(SceneTrack.Sample sample, long startedAtMillis, float startBlend) { }
    public record Look(float yaw, float pitch) { }
}
