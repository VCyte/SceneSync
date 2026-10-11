package io.github.vcyte.scenesync.api;

import java.util.Map;
import java.util.Objects;

/**
 * Low-level server-tick scene stream. A provider is evaluated live during playback;
 * frames are sent to compatible clients as a bounded rolling timeline.
 */
public record DynamicScene(String id, int durationTicks, DynamicFrameProvider provider,
                           DamagePolicy damagePolicy, Map<String, DamagePolicy> animationDamagePolicies,
                           SceneCameraSettings cameraSettings) {
    public DynamicScene {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("id must be a lowercase namespaced-safe identifier");
        if (durationTicks < 1 || durationTicks > SceneDefinition.MAX_DURATION_TICKS)
            throw new IllegalArgumentException("durationTicks must be in 1.." + SceneDefinition.MAX_DURATION_TICKS);
        Objects.requireNonNull(provider, "provider");
        Objects.requireNonNull(damagePolicy, "damagePolicy");
        animationDamagePolicies = Map.copyOf(Objects.requireNonNull(animationDamagePolicies, "animationDamagePolicies"));
        cameraSettings = Objects.requireNonNull(cameraSettings, "cameraSettings");
    }

    public DynamicScene(String id, int durationTicks, DynamicFrameProvider provider) {
        this(id, durationTicks, provider, DamagePolicy.CANCEL_SCENE, Map.of(), SceneCameraSettings.DEFAULT);
    }

    public DynamicScene(String id, int durationTicks, DynamicFrameProvider provider,
                        DamagePolicy damagePolicy, SceneCameraSettings cameraSettings) {
        this(id, durationTicks, provider, damagePolicy, Map.of(), cameraSettings);
    }

    public DamagePolicy damagePolicyAt(String animation) {
        return animationDamagePolicies.getOrDefault(animation, damagePolicy);
    }
}
