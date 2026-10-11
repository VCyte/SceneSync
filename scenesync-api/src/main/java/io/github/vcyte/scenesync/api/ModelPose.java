package io.github.vcyte.scenesync.api;

import java.util.Objects;

/**
 * Generic vanilla-humanoid skeleton pose. Each part is an offset from the normal model pose.
 * Armor layers and hand-attached items inherit these same part transforms.
 */
public record ModelPose(BonePose head, BonePose body, BonePose leftArm, BonePose rightArm,
                        BonePose leftLeg, BonePose rightLeg) {
    public static final ModelPose NEUTRAL = new ModelPose(BonePose.ZERO, BonePose.ZERO, BonePose.ZERO,
            BonePose.ZERO, BonePose.ZERO, BonePose.ZERO);

    public ModelPose {
        Objects.requireNonNull(head, "head");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(leftArm, "leftArm");
        Objects.requireNonNull(rightArm, "rightArm");
        Objects.requireNonNull(leftLeg, "leftLeg");
        Objects.requireNonNull(rightLeg, "rightLeg");
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private BonePose head = BonePose.ZERO;
        private BonePose body = BonePose.ZERO;
        private BonePose leftArm = BonePose.ZERO;
        private BonePose rightArm = BonePose.ZERO;
        private BonePose leftLeg = BonePose.ZERO;
        private BonePose rightLeg = BonePose.ZERO;

        public Builder head(BonePose value) { head = Objects.requireNonNull(value); return this; }
        public Builder body(BonePose value) { body = Objects.requireNonNull(value); return this; }
        public Builder leftArm(BonePose value) { leftArm = Objects.requireNonNull(value); return this; }
        public Builder rightArm(BonePose value) { rightArm = Objects.requireNonNull(value); return this; }
        public Builder leftLeg(BonePose value) { leftLeg = Objects.requireNonNull(value); return this; }
        public Builder rightLeg(BonePose value) { rightLeg = Objects.requireNonNull(value); return this; }

        public ModelPose build() { return new ModelPose(head, body, leftArm, rightArm, leftLeg, rightLeg); }
    }
}
