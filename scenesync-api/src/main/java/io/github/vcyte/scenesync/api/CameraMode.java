package io.github.vcyte.scenesync.api;

/** Actor-local camera behavior while a scene is active. */
public enum CameraMode {
    /** Preserve the player's camera and mouse look without SceneSync orientation changes. */
    FREE_LOOK,
    /** Follow the scene-authored camera yaw/pitch track; mouse look is temporarily locked. */
    LOCKED,
    /** Follow the actor and scripted camera target while clamping mouse look to a forward cone. */
    LIMITED
}
