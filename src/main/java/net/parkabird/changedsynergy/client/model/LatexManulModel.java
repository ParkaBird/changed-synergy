package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.ltxprogrammer.changed.client.renderer.animate.AnimatorPresets;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;

public final class LatexManulModel<T extends ChangedEntity> extends AdvancedHumanoidModel<T> {
    public static final ModelLayerLocation MALE_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_manul"), "main");
    public static final ModelLayerLocation FEMALE_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_manul_female"), "main");

    private final ModelPart root;
    private final ModelPart head;
    private final ModelPart torso;
    private final ModelPart leftArm;
    private final ModelPart rightArm;
    private final ModelPart leftLeg;
    private final ModelPart rightLeg;
    private final HumanoidAnimator<T, LatexManulModel<T>> animator;

    public LatexManulModel(ModelPart root) {
        super(root);
        this.root = root;
        head = root.getChild("Head");
        torso = root.getChild("Torso");
        leftArm = root.getChild("LeftArm");
        rightArm = root.getChild("RightArm");
        leftLeg = root.getChild("LeftLeg");
        rightLeg = root.getChild("RightLeg");
        ModelPart leftLower = leftLeg.getChild("LeftLowerLeg");
        ModelPart rightLower = rightLeg.getChild("RightLowerLeg");
        ModelPart leftFoot = leftLower.getChild("LeftFoot");
        ModelPart rightFoot = rightLower.getChild("RightFoot");
        ModelPart tail = torso.getChild("Tail");
        ModelPart primary = tail.getChild("TailPrimary");
        ModelPart secondary = primary.getChild("TailPrimary_bind_r1").getChild("TailSecondary");
        ModelPart tertiary = secondary.getChild("TailSecondary_bind_r1").getChild("TailTertiary");
        ModelPart quaternary = tertiary.getChild("TailTertiary_bind_r1").getChild("TailQuaternary");
        ModelPart quinary = quaternary.getChild("TailQuaternary_bind_r1").getChild("TailQuinary");
        List<ModelPart> tailJoints = List.of(primary, secondary, tertiary, quaternary, quinary);
        animator = HumanoidAnimator.<T, LatexManulModel<T>>of(this)
                .hipOffset(-1.5F)
                .addPreset(AnimatorPresets.catLike(
                        head,
                        head.getChild("LeftEar"),
                        head.getChild("RightEar"),
                        torso,
                        leftArm,
                        rightArm,
                        tail,
                        tailJoints,
                        leftLeg,
                        leftLower,
                        leftFoot,
                        leftFoot.getChild("LeftPad"),
                        rightLeg,
                        rightLower,
                        rightFoot,
                        rightFoot.getChild("RightPad")))
                .addAnimator(new MuntjacEarsAnimator<>(head.getChild("LeftEar"), head.getChild("RightEar")))
                .addAnimator(new ManulTailWrapAnimator<>(tailJoints));
        SmoothGrabAnimator.install(animator, head, torso, leftArm, rightArm);
    }

    @Override
    public void setupAnim(T entity, float swing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, swing, amount, age, yaw, pitch);
    }

    @Override
    public ModelPart getArm(HumanoidArm side) {
        return side == HumanoidArm.LEFT ? leftArm : rightArm;
    }

    @Override
    public ModelPart getLeg(HumanoidArm side) {
        return side == HumanoidArm.LEFT ? leftLeg : rightLeg;
    }

    @Override
    public ModelPart getHead() {
        return head;
    }

    @Override
    public ModelPart getTorso() {
        return torso;
    }

    @Override
    public HumanoidAnimator<T, LatexManulModel<T>> getAnimator(T entity) {
        return animator;
    }

    @Override
    public void renderToBuffer(
            PoseStack pose,
            VertexConsumer buffer,
            int light,
            int overlay,
            float red,
            float green,
            float blue,
            float alpha) {
        root.render(pose, buffer, light, overlay, red, green, blue, alpha);
    }
}
