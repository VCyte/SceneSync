package io.github.vcyte.scenesync.client;

import net.fabricmc.api.ClientModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SceneSyncClient implements ClientModInitializer {
    public static final String MOD_ID = "scenesync";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static Identifier id(String path) { return Identifier.fromNamespaceAndPath(MOD_ID, path); }

    @Override public void onInitializeClient() {
        BridgePayloads.register();
        new SceneNetwork().register();
        LOGGER.info("SceneSync client initialized for Minecraft 26.2");
    }
}
