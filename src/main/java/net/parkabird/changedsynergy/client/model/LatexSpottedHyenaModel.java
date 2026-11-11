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

public final class LatexSpottedHyenaModel<T extends ChangedEntity> extends AdvancedHumanoidModel<T> {
    public static final ModelLayerLocation MALE_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_spotted_hyena_male"), "main");
    public static final ModelLayerLocation FEMALE_LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_spotted_hyena_female"), "main");
    private final ModelPart root, head, torso, leftArm, rightArm, leftLeg, rightLeg;
    private final boolean female;
    private final HumanoidAnimator<T, LatexSpottedHyenaModel<T>> animator;

    public LatexSpottedHyenaModel(ModelPart root, boolean female) {
        super(root);
        this.root = root;
        this.female = female;
        head = root.getChild("Head");
        torso = root.getChild("Torso");
        leftArm = root.getChild("LeftArm");
        rightArm = root.getChild("RightArm");
        leftLeg = root.getChild("LeftLeg");
        rightLeg = root.getChild("RightLeg");
        ModelPart leftLower = leftLeg.getChild("LeftLowerLeg"), rightLower = rightLeg.getChild("RightLowerLeg");
        ModelPart leftFoot = leftLower.getChild("LeftFoot"), rightFoot = rightLower.getChild("RightFoot");
        ModelPart tail = torso.getChild("Tail"), primary = tail.getChild("TailPrimary");
        ModelPart secondary = primary.getChild("TailPrimary_bind_r1").getChild("TailSecondary");
        ModelPart tertiary = secondary.getChild("TailSecondary_bind_r1").getChild("TailTertiary");
        // The ear-free preset keeps the authored ear pivots; the shared spring adds follow-through.
        animator = HumanoidAnimator.<T, LatexSpottedHyenaModel<T>>of(this)
                .hipOffset(-1.5F).torsoLength(female ? 13.2F : 12.0F).torsoWidth(female ? 5.3F : 5.0F)
                .addPreset(AnimatorPresets.deerLike(head, head.getChild("LeftEar"), head.getChild("RightEar"),
                        torso, leftArm, rightArm, tail, List.of(primary, secondary, tertiary),
                        leftLeg, leftLower, leftFoot, leftFoot.getChild("LeftPad"),
                        rightLeg, rightLower, rightFoot, rightFoot.getChild("RightPad")))
                .addAnimator(new MuntjacEarsAnimator<>(head.getChild("LeftEar"), head.getChild("RightEar")));
        SmoothGrabAnimator.install(animator, head, torso, leftArm, rightArm);
    }

    @Override public void scaleForHead(PoseStack pose) {
        // Expand around the head pivot; scaling its model-space position hides the eyes when looking down.
        if (female) {
            pose.translate(head.x / 16.0F, head.y / 16.0F, head.z / 16.0F);
            pose.scale(1.027F, 1.027F, 1.027F);
            pose.translate(-head.x / 16.0F, -head.y / 16.0F, -head.z / 16.0F);
        }
    }
    @Override public void setupAnim(T entity, float swing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, swing, amount, age, yaw, pitch);
    }
    @Override public ModelPart getArm(HumanoidArm side) { return side == HumanoidArm.LEFT ? leftArm : rightArm; }
    @Override public ModelPart getLeg(HumanoidArm side) { return side == HumanoidArm.LEFT ? leftLeg : rightLeg; }
    @Override public ModelPart getHead() { return head; }
    @Override public ModelPart getTorso() { return torso; }
    @Override public HumanoidAnimator<T, LatexSpottedHyenaModel<T>> getAnimator(T entity) { return animator; }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) { root.render(pose, buffer, light, overlay, red, green, blue, alpha); }
}
