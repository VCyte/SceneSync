package io.github.vcyte.scenesync.api;

import org.bukkit.entity.Player;

/** Handle returned by a live dynamic playback. Calls must run on the Paper main thread. */
public interface DynamicSceneHandle {
    String sceneId();
    Player actor();
    boolean isActive();
    boolean stop();
}
