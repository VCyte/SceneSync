package io.github.vcyte.scenesync.api;

/** What SceneSync does when a playing actor receives positive final damage. */
public enum DamagePolicy {
    /** Stop the scene first, then let normal Minecraft damage and hurt animation proceed. */
    CANCEL_SCENE,
    /** Cancel the damage event and keep the scene running; the actor remains unharmed. */
    IGNORE_DAMAGE
}
