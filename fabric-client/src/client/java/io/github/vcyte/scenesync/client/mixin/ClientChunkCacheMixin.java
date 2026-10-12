package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientChunkCache;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Keeps the local chunk-cache window centered on a detached SceneSync camera. */
@Mixin(ClientChunkCache.class)
public abstract class ClientChunkCacheMixin {
    @ModifyArgs(method = "updateViewCenter(II)V", at = @At("HEAD"))
    private void scenesync$centerOnDetachedCamera(Args args) {
        Minecraft client = Minecraft.getInstance();
        LocalPlayer player = client.player;
        if (player == null || client.options.getCameraType().isFirstPerson()) return;
        SceneTrack.Sample sample = SceneController.sample(player.getId(), System.currentTimeMillis());
        if (sample == null || sample.cameraMode() != SceneTrack.CameraMode.FREE_LOOK) return;
        Vec3 camera = client.gameRenderer.mainCamera().position();
        args.set(0, ((int)Math.floor(camera.x)) >> 4);
        args.set(1, ((int)Math.floor(camera.z)) >> 4);
    }
}
