package io.github.vcyte.scenesync.client.mixin;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(Camera.class)
public interface CameraAccessor {
    @Accessor("rotation") Quaternionf scenesync$getRotation();
    @Accessor("entity") Entity scenesync$getEntity();
}
