package io.github.vcyte.scenesync.api;

import org.bukkit.entity.Player;

import java.util.Set;

/** Public service registered by the SceneSync Paper plugin. All calls must run on the server thread. */
public interface SceneManager {
    void register(SceneDefinition definition);
    boolean unregister(String id);
    Set<String> sceneIds();
    boolean play(Player actor, String sceneId);
    boolean stop(Player actor);
    boolean isPlaying(Player actor);
}
