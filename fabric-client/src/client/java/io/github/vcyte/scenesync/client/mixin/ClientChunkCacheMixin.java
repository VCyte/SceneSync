package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Keeps the local chunk-cache window centered on a detached SceneSync camera. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @ModifyVariable(method = "updateViewCenter(II)V", at = @At("HEAD"), ordinal = 0, argsOnly = true)
    private int scenesync$cameraChunkX(int original) {
        Vec3 camera = scenesync$detachedCamera();
        return camera == null ? original : ((int) Math.floor(camera.x)) >> 4;
    }

    @ModifyVariable(method = "updateViewCenter(II)V", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private int scenesync$cameraChunkZ(int original) {
        Vec3 camera = scenesync$detachedCamera();
        return camera == null ? original : ((int) Math.floor(camera.z)) >> 4;
    }

    private static Vec3 scenesync$detachedCamera() {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.getCameraType().isFirstPerson()) return null;
        SceneTrack.Sample sample = SceneController.sample(player.getId(), System.currentTimeMillis());
        if (sample == null || sample.cameraMode() != SceneTrack.CameraMode.FREE_LOOK) return null;
        return client.gameRenderer.mainCamera().position();
    }
}
