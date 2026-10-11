package io.github.vcyte.scenesync.api;

/**
 * Produces one relative scene frame for a server tick. Implementations must be fast,
 * deterministic for the supplied context, and run only on the Paper main thread.
 */
@FunctionalInterface
public interface DynamicFrameProvider {
    SceneFrame frame(DynamicSceneContext context);
}
