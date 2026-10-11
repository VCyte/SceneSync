package io.github.vcyte.scenesync.client.mixin;

import io.github.vcyte.scenesync.client.SceneController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {
    @Inject(method = "tick()V", at = @At("TAIL"))
    private void scenesync$blockMovementInputDuringScene(CallbackInfo ci) {
        var player = Minecraft.getInstance().player;
        if (player == null || !SceneController.controlsInput(player.getId(), System.currentTimeMillis())) return;
        ClientInputAccessor input = (ClientInputAccessor)(Object)this;
        input.scenesync$setKeyPresses(Input.EMPTY);
        input.scenesync$setMoveVector(Vec2.ZERO);
    }
}
