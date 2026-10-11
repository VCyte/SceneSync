package io.github.vcyte.scenesync.api;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.Objects;

/** Read-only context passed to a DynamicFrameProvider on the Paper main thread. */
public record DynamicSceneContext(Player actor, Location origin, int tick, long serverTimeMillis) {
    public DynamicSceneContext {
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(origin, "origin");
        if (tick < 0 || tick > SceneDefinition.MAX_DURATION_TICKS)
            throw new IllegalArgumentException("tick out of range");
        origin = origin.clone();
    }
}
