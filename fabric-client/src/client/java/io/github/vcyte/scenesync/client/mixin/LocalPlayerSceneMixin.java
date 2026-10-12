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
    @org.spongepowered.asm.mixin.Unique private boolean scenesync$restoring;
    @org.spongepowered.asm.mixin.Unique private Vec3 scenesync$realPosition;
    @org.spongepowered.asm.mixin.Unique private Vec3 scenesync$realVelocity;

    @Inject(method = "sendPosition()V", at = @At("HEAD"))
    private void scenesync$sendInterpolatedScenePosition(CallbackInfo ci) {
        LocalPlayer player = (LocalPlayer)(Object)this;
        SceneTrack.Sample sample = SceneController.sample(player.getId(), System.currentTimeMillis());
        if (sample == null) return;
        scenesync$realPosition = player.position();
        scenesync$realVelocity = player.getDeltaMovement();
        scenesync$restoring = true;
        player.setPos(sample.worldX(), sample.worldY(), sample.worldZ());
        player.setDeltaMovement(Vec3.ZERO);
    }

    @Inject(method = "sendPosition()V", at = @At("TAIL"))
    private void scenesync$restoreInterpolatedScenePosition(CallbackInfo ci) {
        if (!scenesync$restoring || scenesync$realPosition == null) return;
        LocalPlayer player = (LocalPlayer)(Object)this;
        player.setPos(scenesync$realPosition.x, scenesync$realPosition.y, scenesync$realPosition.z);
        player.setDeltaMovement(scenesync$realVelocity == null ? Vec3.ZERO : scenesync$realVelocity);
        scenesync$restoring = false;
        scenesync$realPosition = null;
        scenesync$realVelocity = null;
    }
}
