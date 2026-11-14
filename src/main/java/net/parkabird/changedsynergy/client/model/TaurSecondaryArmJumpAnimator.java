package net.parkabird.changedsynergy.client.model;

import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;

/**
 * {@code TaurUpperBodyJumpAnimator} only compensates the primary arm pair.
 * Secondary (lower) arms need the same vertical counter-shift so they stay with
 * the upper body instead of drifting with the pitched lower torso on jump.
 */
public final class TaurSecondaryArmJumpAnimator<T extends ChangedEntity, M extends AdvancedHumanoidModel<T>>
        extends HumanoidAnimator.Animator<T, M> {
    private final ModelPart leftArm;
    private final ModelPart rightArm;

    public TaurSecondaryArmJumpAnimator(ModelPart leftArm, ModelPart rightArm) {
        this.leftArm = leftArm;
        this.rightArm = rightArm;
    }

    @Override
    public HumanoidAnimator.AnimateStage preferredStage() {
        return HumanoidAnimator.AnimateStage.BOB;
    }

    @Override
    public void setupAnim(T entity, float limbSwing, float limbSwingAmount,
            float ageInTicks, float netHeadYaw, float headPitch) {
        float vertical = Mth.clamp(
                (float) entity.getDeltaMovement(core.partialTicks).y, -0.8F, 0.8F);
        float shift = vertical * 5.0F;
        leftArm.y -= shift;
        rightArm.y -= shift;
    }
}
