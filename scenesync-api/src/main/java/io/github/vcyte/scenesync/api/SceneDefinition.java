package io.github.vcyte.scenesync.api;

import java.util.List;
import java.util.Objects;

/** Immutable, server-owned scene timeline. Coordinates are relative to the starting actor pose. */
public record SceneDefinition(String id, int durationTicks, List<SceneFrame> frames) {
    public static final int MAX_FRAMES = 128;
    public static final int MAX_DURATION_TICKS = 24_000;

    public SceneDefinition {
        if (id == null || !id.matches("[a-z0-9][a-z0-9._-]{0,63}"))
            throw new IllegalArgumentException("id must be a lowercase namespaced-safe identifier");
        if (durationTicks < 1 || durationTicks > MAX_DURATION_TICKS)
            throw new IllegalArgumentException("durationTicks must be in 1.." + MAX_DURATION_TICKS);
        frames = List.copyOf(Objects.requireNonNull(frames, "frames"));
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
    }
}
