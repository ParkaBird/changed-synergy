package net.parkabird.changedsynergy.client.model;

import java.util.Map;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.client.renderer.animate.upperbody.HoldEntityAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel.GrabState;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.util.Mth;

/** Blends Changed's grab poses into and out of the normal arm animation. */
public final class SmoothGrabAnimator<T extends ChangedEntity, M extends AdvancedHumanoidModel<T>>
        extends HoldEntityAnimator<T, M> {
    private final Map<T, Transition> transitions = new WeakHashMap<>();

    public SmoothGrabAnimator(ModelPart head, ModelPart torso, ModelPart leftArm, ModelPart rightArm) {
        super(head, torso, leftArm, rightArm);
    }

    /** Replace the preset's hold in place, preserving the rest of its species animation chain. */
    public static <T extends ChangedEntity, M extends AdvancedHumanoidModel<T>> void install(
            HumanoidAnimator<T, M> animator, ModelPart head, ModelPart torso, ModelPart leftArm, ModelPart rightArm) {
        var list = ((net.parkabird.changedsynergy.mixin.HumanoidAnimatorAccess)(Object)animator)
                .changedSynergy$animators().get(HumanoidAnimator.AnimateStage.FINAL);
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getClass() == HoldEntityAnimator.class) {
                var smooth = new SmoothGrabAnimator<T, M>(head, torso, leftArm, rightArm);
                smooth.core = animator;
                list.set(i, smooth);
                return;
            }
        }
        throw new IllegalStateException("Species preset has no hold animator to replace");
    }

    @Override public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        if (net.parkabird.changedsynergy.ai.EagleOwlTraits.airborne(
                net.parkabird.changedsynergy.client.PlayerHugPoses.physical(entity))) {
            transitions.remove(entity);
            return;
        }
        GrabState requested = core.grabState;
        var grab = entity.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        // Hold ticks start on the next tick; the controller already knows the key is down.
        if (requested == GrabState.EMPTY && grab != null && grab.grabbedEntity == null
                && grab.getController().getProgressActive() > 0) requested = GrabState.REACH;
        Transition transition = transitions.computeIfAbsent(entity, ignored -> new Transition(ageInTicks));
        float elapsed = Mth.clamp(ageInTicks - transition.age, 0, 4);
        transition.age = ageInTicks;
        boolean active = requested != GrabState.EMPTY;
        if (active) transition.pose = requested;
        transition.weight = Mth.clamp(transition.weight + (active ? elapsed : -elapsed) / 4, 0, 1);
        if (transition.weight == 0) return;

        PartPose leftBase = leftArm.storePose(), rightBase = rightArm.storePose();
        GrabState original = core.grabState;
        try {
            core.grabState = transition.pose;
            super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
        } finally {
            core.grabState = original;
        }
        blend(leftArm, leftBase, transition.weight);
        blend(rightArm, rightBase, transition.weight);
    }

    private static void blend(ModelPart arm, PartPose base, float weight) {
        arm.x = Mth.lerp(weight, base.x, arm.x);
        arm.y = Mth.lerp(weight, base.y, arm.y);
        arm.z = Mth.lerp(weight, base.z, arm.z);
        arm.xRot = Mth.lerp(weight, base.xRot, arm.xRot);
        arm.yRot = Mth.lerp(weight, base.yRot, arm.yRot);
        arm.zRot = Mth.lerp(weight, base.zRot, arm.zRot);
    }

    private static final class Transition {
        private float age, weight;
        private GrabState pose = GrabState.REACH;

        private Transition(float age) { this.age = age; }
    }
}
