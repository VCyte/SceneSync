package io.github.vcyte.scenesync.client;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.function.Function;

/** Raw codecs deliberately match Paper's sendPluginMessage payload bytes (no length prefix). */
public final class BridgePayloads {
    public static final int MAX_BYTES = 64 * 1024;
    private BridgePayloads() { }

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Hello.TYPE, Hello.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Play.TYPE, Play.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(Stop.TYPE, Stop.CODEC);
    }

    private static <T extends CustomPacketPayload> StreamCodec<RegistryFriendlyByteBuf, T> raw(
            Function<T, byte[]> getter, Function<byte[], T> factory) {
        return StreamCodec.of((buf, payload) -> {
            byte[] bytes = getter.apply(payload);
            if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
                throw new IllegalArgumentException("SceneSync payload must be 1.." + MAX_BYTES + " bytes");
            buf.writeBytes(bytes);
        }, buf -> {
            int size = buf.readableBytes();
            if (size <= 0 || size > MAX_BYTES) throw new IllegalArgumentException("Invalid SceneSync payload size");
            byte[] bytes = new byte[size];
            buf.readBytes(bytes);
            return factory.apply(bytes);
        });
    }

    public record Hello(byte[] data) implements CustomPacketPayload {
        public static final Type<Hello> TYPE = new Type<>(SceneSyncClient.id("hello"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Hello> CODEC = raw(Hello::data, Hello::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Play(byte[] data) implements CustomPacketPayload {
        public static final Type<Play> TYPE = new Type<>(SceneSyncClient.id("play"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Play> CODEC = raw(Play::data, Play::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
    public record Stop(byte[] data) implements CustomPacketPayload {
        public static final Type<Stop> TYPE = new Type<>(SceneSyncClient.id("stop"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Stop> CODEC = raw(Stop::data, Stop::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
