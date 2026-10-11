package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import io.github.vcyte.scenesync.client.SceneTrack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSceneMixin {
    @Inject(method = "sendPosition()V", at = @At("HEAD"))
    private void scenesync$sendInterpolatedScenePosition(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        SceneTrack.Sample sample = SceneController.sample(player.getId(), System.currentTimeMillis());
        if (sample == null) return;
        player.setPos(sample.worldX(), sample.worldY(), sample.worldZ());
        player.setDeltaMovement(Vec3.ZERO);
    }
}
