package net.parkabird.changedsynergy.client.model;

import java.util.List;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.util.Mth;
import net.parkabird.changedsynergy.ability.ManulCrouchRestAbility;
import org.jetbrains.annotations.NotNull;

/**
 * Blends the authored Tailwrap foot-coil onto cat-like tail joints.
 *
 * <p>Rest curl already lives on each {@code *_bind_r1} bone (import negates BB X/Y).
 * Wrap therefore applies the BB (wrap − rest) delta with the same {@code (-X, -Y, +Z)}
 * conversion onto the animatable joints, otherwise the tail pitches up into the shoulders.
 */
public final class ManulTailWrapAnimator<T extends ChangedEntity, M extends AdvancedHumanoidModel<T>>
        extends HumanoidAnimator.Animator<T, M> {
    private static final float DEG = ((float) Math.PI) / 180.0F;

    /**
     * Joint-local wrap targets (radians), primary → quinary.
     * BB rest [28,−8,−14,−12,−6] / wrap [50/86.5/11, …]; import uses (−X,−Y,+Z).
     */
    private static final float[][] WRAP = {
        bbDelta(22.0F, 86.5F, 11.0F),
        bbDelta(16.0F, 27.0F, -14.8F),
        bbDelta(28.0F, 33.5F, -6.0F),
        bbDelta(20.0F, 43.5F, -4.0F),
        bbDelta(6.0F, 20.8F, -0.2F),
    };

    private static float[] bbDelta(float bbX, float bbY, float bbZ) {
        return new float[] {-bbX * DEG, -bbY * DEG, bbZ * DEG};
    }

    private final List<ModelPart> joints;

    public ManulTailWrapAnimator(List<ModelPart> joints) {
        this.joints = joints;
    }

    @Override
    public HumanoidAnimator.AnimateStage preferredStage() {
        return HumanoidAnimator.AnimateStage.FINAL;
    }

    @Override
    public void setupAnim(
            @NotNull T entity,
            float limbSwing,
            float limbSwingAmount,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        float amount = ManulCrouchRestAbility.wrapAmount(entity, core.partialTicks);
        if (amount <= 0.001F) {
            return;
        }
        int count = Math.min(joints.size(), WRAP.length);
        for (int i = 0; i < count; i++) {
            ModelPart joint = joints.get(i);
            float[] target = WRAP[i];
            joint.xRot = Mth.lerp(amount, joint.xRot, target[0]);
            joint.yRot = Mth.lerp(amount, joint.yRot, target[1]);
            joint.zRot = Mth.lerp(amount, joint.zRot, target[2]);
        }
    }
}
