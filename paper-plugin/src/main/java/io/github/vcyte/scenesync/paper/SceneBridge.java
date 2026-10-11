package io.github.vcyte.scenesync.paper;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.vcyte.scenesync.api.SceneDefinition;
import io.github.vcyte.scenesync.api.SceneFrame;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Bounded Paper plugin-message transport. Tokens prevent stale packets crossing reconnects. */
final class SceneBridge implements PluginMessageListener {
    static final String HELLO = "scenesync:hello";
    static final String PLAY = "scenesync:play";
    static final String STOP = "scenesync:stop";
    static final int MAX_PACKET_BYTES = 64 * 1024;
    private final SceneSyncPlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, Window> helloWindows = new HashMap<>();

    SceneBridge(SceneSyncPlugin plugin) { this.plugin = plugin; }

    void register() {
        var messenger = plugin.getServer().getMessenger();
        messenger.registerIncomingPluginChannel(plugin, HELLO, this);
        messenger.registerOutgoingPluginChannel(plugin, HELLO);
        messenger.registerOutgoingPluginChannel(plugin, PLAY);
        messenger.registerOutgoingPluginChannel(plugin, STOP);
    }

    void unregister() {
        plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, HELLO, this);
        plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin);
        sessions.clear(); helloWindows.clear();
    }

    @Override public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!HELLO.equals(channel) || message == null || message.length == 0 || message.length > 4096) return;
        if (!allowHello(player.getUniqueId())) return;
        JsonObject request = parse(message);
        if (request == null || integer(request, "protocol", -1) != 1) return;
        long clientTime = longNumber(request, "clientTime", -1);
        long now = System.currentTimeMillis();
        if (clientTime <= 0 || clientTime < now - 86_400_000L || clientTime > now + 86_400_000L) return;
        Session session = sessions.computeIfAbsent(player.getUniqueId(),
                ignored -> new Session(UUID.randomUUID().toString().replace("-", ""), System.nanoTime()));
        JsonObject response = new JsonObject();
        response.addProperty("protocol", 1);
        response.addProperty("clientTime", clientTime);
        response.addProperty("serverTime", now);
        response.addProperty("session", session.token());
        send(player, HELLO, response);
        plugin.getLogger().fine("SceneSync client handshake accepted for " + player.getUniqueId());
    }

    boolean isCompatible(Player player) { return sessions.containsKey(player.getUniqueId()); }
    void remove(Player player) { sessions.remove(player.getUniqueId()); helloWindows.remove(player.getUniqueId()); }

    void sendPlay(Player viewer, Player actor, String runId, long startAtMillis, SceneDefinition scene) {
        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("protocol", 1);
        packet.addProperty("session", session.token());
        packet.addProperty("runId", runId);
        packet.addProperty("entityId", actor.getEntityId());
        packet.addProperty("scene", scene.id());
        packet.addProperty("durationTicks", scene.durationTicks());
        packet.addProperty("startAtMillis", startAtMillis);
        JsonArray frames = new JsonArray();
        for (SceneFrame frame : scene.frames()) {
            JsonObject f = new JsonObject();
            f.addProperty("tick", frame.tick()); f.addProperty("x", frame.x());
            f.addProperty("y", frame.y()); f.addProperty("z", frame.z());
            f.addProperty("yaw", frame.yaw()); f.addProperty("pitch", frame.pitch());
            f.addProperty("animation", frame.animation());
            f.addProperty("cameraYaw", frame.cameraYaw()); f.addProperty("cameraPitch", frame.cameraPitch());
            f.addProperty("cameraRoll", frame.cameraRoll()); f.addProperty("cameraShake", frame.cameraShake());
            frames.add(f);
        }
        packet.add("frames", frames);
        send(viewer, PLAY, packet);
    }

    void sendStop(Player viewer, int entityId, String runId) {
        Session session = sessions.get(viewer.getUniqueId());
        if (session == null) return;
        JsonObject packet = new JsonObject();
        packet.addProperty("protocol", 1); packet.addProperty("session", session.token());
        packet.addProperty("entityId", entityId); packet.addProperty("runId", runId);
        send(viewer, STOP, packet);
    }

    private void send(Player player, String channel, JsonObject payload) {
        try {
            byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
            if (bytes.length == 0 || bytes.length > MAX_PACKET_BYTES) return;
            player.sendPluginMessage(plugin, channel, bytes);
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("SceneSync skipped oversized packet on " + channel);
        }
    }

    private boolean allowHello(UUID player) {
        long now = System.nanoTime();
        Window old = helloWindows.get(player);
        if (old == null || now - old.startedNanos() > 10_000_000_000L) {
            helloWindows.put(player, new Window(now, 0)); old = helloWindows.get(player);
        }
        if (old.count() >= 5) return false;
        helloWindows.put(player, new Window(old.startedNanos(), old.count() + 1));
        return true;
    }

    private static JsonObject parse(byte[] bytes) {
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (!boundedNesting(text, 12)) return null;
        try { JsonElement e = JsonParser.parseString(text); return e.isJsonObject() ? e.getAsJsonObject() : null; }
        catch (RuntimeException ignored) { return null; }
    }
    private static int integer(JsonObject o, String key, int fallback) {
        try { if (!o.has(key) || !o.get(key).getAsJsonPrimitive().isNumber()) return fallback;
            long v = o.get(key).getAsLong(); return v >= Integer.MIN_VALUE && v <= Integer.MAX_VALUE ? (int)v : fallback;
        } catch (RuntimeException ignored) { return fallback; }
    }
    private static long longNumber(JsonObject o, String key, long fallback) {
        try { return o.has(key) && o.get(key).getAsJsonPrimitive().isNumber() ? o.get(key).getAsLong() : fallback; }
        catch (RuntimeException ignored) { return fallback; }
    }
    private static boolean boundedNesting(String s, int max) {
        int depth = 0; boolean quoted = false; boolean escape = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (quoted) { if (escape) escape = false; else if (c == '\\') escape = true; else if (c == '"') quoted = false; continue; }
            if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > max) return false; }
            else if (c == '}' || c == ']') { if (--depth < 0) return false; }
        }
        return depth == 0 && !quoted;
    }
    private record Session(String token, long startedNanos) { }
    private record Window(long startedNanos, int count) { }
}
