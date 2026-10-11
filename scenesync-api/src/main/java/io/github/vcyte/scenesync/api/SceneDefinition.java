package io.github.vcyte.scenesync.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Immutable, server-owned scene timeline. Coordinates are relative to the starting actor pose. */
public record SceneDefinition(String id, int durationTicks, List<SceneFrame> frames, DamagePolicy damagePolicy,
                              Map<String, DamagePolicy> animationDamagePolicies, SceneCameraSettings cameraSettings) {
    public static final int MAX_FRAMES = 128;
    public static final int MAX_DURATION_TICKS = 24_000;

    public SceneDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("id must be a lowercase namespaced-safe identifier");
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS)
            throw new IllegalArgumentException("durationTicks must be in 1.." + MAX_DURATION_TICKS);
        Objects.requireNonNull(damagePolicy, "damagePolicy");
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
        animationDamagePolicies = Map.copyOf(Objects.requireNonNull(animationDamagePolicies, "animationDamagePolicies"));
        Objects.requireNonNull(cameraSettings, "cameraSettings");
        if (frames.isEmpty() || frames.size() > MAX_FRAMES)
            throw new IllegalArgumentException("scene must contain 1.." + MAX_FRAMES + " frames");
        int previous = -1;
        for (SceneFrame frame : frames) {
            Objects.requireNonNull(frame, "frame");
            if (frame.tick() <= previous) throw new IllegalArgumentException("frame ticks must be strictly increasing");
            if (frame.tick() > durationTicks) throw new IllegalArgumentException("frame exceeds scene duration");
            previous = frame.tick();
        }
        if (frames.getFirst().tick() != 0) throw new IllegalArgumentException("first frame must start at tick 0");
        Set<String> usedAnimations = frames.stream().map(SceneFrame::animation).collect(Collectors.toUnmodifiableSet());
        if (!usedAnimations.containsAll(animationDamagePolicies.keySet()))
            throw new IllegalArgumentException("animation damage policies may only name animations used by this scene");
    }

    public SceneDefinition(String id, int durationTicks, List<SceneFrame> frames, DamagePolicy damagePolicy) {
        this(id, durationTicks, frames, damagePolicy, Map.of(), SceneCameraSettings.DEFAULT);
    }

    public SceneDefinition(String id, int durationTicks, List<SceneFrame> frames, DamagePolicy damagePolicy,
                           Map<String, DamagePolicy> animationDamagePolicies) {
        this(id, durationTicks, frames, damagePolicy, animationDamagePolicies, SceneCameraSettings.DEFAULT);
    }

    /** Backwards-compatible default: a real hit stops the scene and damage is allowed through. */
    public SceneDefinition(String id, int durationTicks, List<SceneFrame> frames) {
        this(id, durationTicks, frames, DamagePolicy.CANCEL_SCENE, Map.of());
    }

    /** Select the policy associated with the animation active at this scene tick. */
    public DamagePolicy damagePolicyAtTick(int tick) {
        SceneFrame current = frames.getFirst();
        for (SceneFrame frame : frames) {
            if (frame.tick() > tick) break;
            current = frame;
        }
        return animationDamagePolicies.getOrDefault(current.animation(), damagePolicy);
    }
}
