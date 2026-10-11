package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Shadow public Input keyPresses;
    @Shadow protected Vec2 moveVector;

    @Inject(method = "tick()V", at = @At("TAIL"))
    private void scenesync$blockMovementInputDuringScene(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || !SceneController.controlsInput(player.getId(), System.currentTimeMillis())) return;
        keyPresses = Input.EMPTY;
        moveVector = Vec2.ZERO;
    }
}
