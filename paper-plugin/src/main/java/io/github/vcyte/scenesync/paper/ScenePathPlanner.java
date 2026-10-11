package io.github.vcyte.scenesync.paper;

import io.github.vcyte.scenesync.api.SceneFrame;
import io.github.vcyte.scenesync.api.BonePose;
import io.github.vcyte.scenesync.api.ModelPose;
import io.github.vcyte.scenesync.api.SceneDefinition;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Per-actor path conditioning used before a scene packet is broadcast. */
final class ScenePathPlanner {
    private ScenePathPlanner() { }

    static List<SceneFrame> holdAt(List<SceneFrame> frames, int stopTick, ScenePathMath.Point safePoint) {
        List<SceneFrame> result = new ArrayList<>(frames.size());
        SceneFrame stopFrame = null;
        for (SceneFrame frame : frames) {
            if (frame.tick() <= stopTick) {
                if (frame.tick() == stopTick) {
                    stopFrame = withPosition(frame, safePoint);
                    result.add(stopFrame);
                } else result.add(frame);
            }
        }
        if (stopFrame == null) {
            SceneFrame source = frames.getFirst();
            for (SceneFrame frame : frames) {
                if (frame.tick() > stopTick) break;
                source = frame;
            }
            stopFrame = new SceneFrame(stopTick, safePoint.x(), safePoint.y(), safePoint.z(), source.yaw(),
                    source.pitch(), source.animation(), source.cameraYaw(), source.cameraPitch(), source.cameraRoll(),
                    source.cameraShake(), source.cameraFov(), source.pose());
            result.add(stopFrame);
        }
        for (SceneFrame frame : frames) {
            if (frame.tick() > stopTick) result.add(withPosition(frame, safePoint));
        }
        return List.copyOf(result);
    }

    static SceneDefinition stopAndBrace(SceneDefinition source, int stopTick, ScenePathMath.Point safePoint) {
        SceneFrame stopSource = source.frames().getFirst();
        List<SceneFrame> frames = new ArrayList<>();
        for (SceneFrame frame : source.frames()) {
            if (frame.tick() > stopTick) break;
            stopSource = frame;
            frames.add(frame.tick() == stopTick ? withPosition(frame, safePoint) : frame);
        }
        if (frames.isEmpty() || frames.getLast().tick() != stopTick) {
            frames.add(new SceneFrame(stopTick, safePoint.x(), safePoint.y(), safePoint.z(), stopSource.yaw(),
                    stopSource.pitch(), stopSource.animation(), stopSource.cameraYaw(), stopSource.cameraPitch(),
                    stopSource.cameraRoll(), stopSource.cameraShake(), stopSource.cameraFov(), stopSource.pose()));
        }

        int endTick = Math.min(source.durationTicks(), stopTick + 6);
        int braceTick = Math.min(stopTick + 1, endTick);
        int braceHoldTick = Math.min(stopTick + 3, endTick);
        ModelPose brace = new ModelPose(BonePose.ZERO, new BonePose(0, 0, 0, 14, 0, 0),
                new BonePose(0, 0, 0, -48, 18, 0), new BonePose(0, 0, 0, -48, -18, 0),
                new BonePose(0, 0, 0, 12, 0, 0), new BonePose(0, 0, 0, 12, 0, 0));
        if (braceTick > stopTick) frames.add(new SceneFrame(braceTick, safePoint.x(), safePoint.y(), safePoint.z(),
                stopSource.yaw(), 0, "scenesync:wall_brace", stopSource.cameraYaw(), stopSource.cameraPitch(),
                0, 0, 0, brace));
        if (braceHoldTick > braceTick) frames.add(new SceneFrame(braceHoldTick, safePoint.x(), safePoint.y(), safePoint.z(),
                stopSource.yaw(), 0, "scenesync:wall_brace", stopSource.cameraYaw(), stopSource.cameraPitch(),
                0, 0, 0, brace));
        if (endTick > braceHoldTick) frames.add(new SceneFrame(endTick, safePoint.x(), safePoint.y(), safePoint.z(),
                stopSource.yaw(), 0, "idle", 0, 0, 0, 0, 0, ModelPose.NEUTRAL));

        Set<String> usedAnimations = new HashSet<>();
        frames.forEach(frame -> usedAnimations.add(frame.animation()));
        Map<String, io.github.vcyte.scenesync.api.DamagePolicy> policies = source.animationDamagePolicies().entrySet()
                .stream().filter(entry -> usedAnimations.contains(entry.getKey()))
                .collect(java.util.stream.Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
        return new SceneDefinition(source.id(), Math.max(1, endTick), frames, source.damagePolicy(), policies,
                source.cameraSettings());
    }

    private static SceneFrame withPosition(SceneFrame frame, ScenePathMath.Point point) {
        return new SceneFrame(frame.tick(), point.x(), point.y(), point.z(), frame.yaw(), frame.pitch(),
                frame.animation(), frame.cameraYaw(), frame.cameraPitch(), frame.cameraRoll(),
                frame.cameraShake(), frame.cameraFov(), frame.pose());
    }
}
