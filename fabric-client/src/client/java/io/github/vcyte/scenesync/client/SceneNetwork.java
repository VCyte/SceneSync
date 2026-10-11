package io.github.vcyte.scenesync.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicLong;

final class SceneNetwork {
    private static final int PROTOCOL = 3;
    private static final long WINDOW_NANOS = 10_000_000_000L;
    private static final int MAX_PACKETS_PER_WINDOW = 64;
    private static final Pattern SESSION = Pattern.compile("[a-f0-9]{32}");
    private final AtomicLong windowStart = new AtomicLong(System.nanoTime());
    private int windowCount;
    private volatile long serverOffsetMillis;
    private volatile String boundSession;
    private int helloTicks;
    private int helloAttempts;

    void register() {
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Hello.TYPE,
                (payload, context) -> receive(payload.data(), context.client(), this::handshake, false));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Play.TYPE,
                (payload, context) -> receive(payload.data(), context.client(), json -> SceneController.acceptPlay(json, serverOffsetMillis), true));
        ClientPlayNetworking.registerGlobalReceiver(BridgePayloads.Stop.TYPE,
                (payload, context) -> receive(payload.data(), context.client(), SceneController::stop, true));
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> client.execute(() -> {
            boundSession = null; serverOffsetMillis = 0; helloAttempts = 0; helloTicks = 0; sendHello();
        }));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(() -> {
            SceneController.clear(); serverOffsetMillis = 0; boundSession = null; helloAttempts = 0; helloTicks = 0;
            windowCount = 0; windowStart.set(System.nanoTime());
        }));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null || boundSession != null || helloAttempts >= 5) return;
            if (helloTicks-- <= 0) sendHello();
        });
    }

    private void receive(byte[] data, Minecraft client, java.util.function.Consumer<JsonObject> consumer, boolean requireSession) {
        if (!allowPacket()) return;
        SceneController.parse(data).ifPresent(json -> client.execute(() -> {
            if (requireSession && !validSession(json)) return;
            consumer.accept(json);
        }));
    }

    private boolean validSession(JsonObject json) {
        try {
            return boundSession != null && json.has("session") && json.get("session").isJsonPrimitive()
                    && json.get("session").getAsJsonPrimitive().isString()
                    && boundSession.equals(json.get("session").getAsString());
        } catch (RuntimeException ignored) { return false; }
    }

    private synchronized boolean allowPacket() {
        long now = System.nanoTime();
        if (now - windowStart.get() > WINDOW_NANOS) { windowStart.set(now); windowCount = 0; }
        return ++windowCount <= MAX_PACKETS_PER_WINDOW;
    }

    private void sendHello() {
        JsonObject hello = new JsonObject();
        hello.addProperty("protocol", PROTOCOL);
        hello.addProperty("clientTime", System.currentTimeMillis());
        try {
            ClientPlayNetworking.send(new BridgePayloads.Hello(hello.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (IllegalStateException ignored) { }
        helloAttempts++;
        helloTicks = 40;
    }

    private void handshake(JsonObject json) {
        try {
        if (json.has("protocol") && json.get("protocol").getAsInt() == PROTOCOL
                && json.has("clientTime") && json.has("serverTime") && json.has("session")) {
            long echoed = json.get("clientTime").getAsLong();
            long server = json.get("serverTime").getAsLong();
            long received = System.currentTimeMillis();
            String session = json.get("session").getAsString();
            if (!SESSION.matcher(session).matches() || (boundSession != null && !boundSession.equals(session))) return;
            if (echoed >= received - 60_000 && echoed <= received + 60_000
                    && server >= received - 86_400_000 && server <= received + 86_400_000)
                { serverOffsetMillis = server - ((echoed + received) / 2L); boundSession = session; }
        }
        } catch (RuntimeException ignored) { }
    }
}
