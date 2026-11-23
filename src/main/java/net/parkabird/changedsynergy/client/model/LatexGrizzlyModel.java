package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.ltxprogrammer.changed.client.renderer.animate.AnimatorPresets;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.tail.AbstractTailAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SpringType;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

public final class LatexGrizzlyModel<T extends ChangedEntity> extends AdvancedHumanoidModel<T> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_grizzly"), "main");
    private final ModelPart root, head, torso, leftArm, rightArm, leftLeg, rightLeg, tail, tailPrimary;
    private final HumanoidAnimator<T, LatexGrizzlyModel<T>> animator;

    public LatexGrizzlyModel(ModelPart root) {
        super(root);
        this.root = root;
        head = root.getChild("Head");
        torso = root.getChild("Torso");
        leftArm = root.getChild("LeftArm");
        rightArm = root.getChild("RightArm");
        leftLeg = root.getChild("LeftLeg");
        rightLeg = root.getChild("RightLeg");
        ModelPart leftLower = leftLeg.getChild("LeftLowerLeg"), rightLower = rightLeg.getChild("RightLowerLeg");
        ModelPart leftFoot = leftLower.getChild("LeftFoot"), rightFoot = rightLower.getChild("RightFoot");
        this.tail = torso.getChild("Tail");
        this.tailPrimary = this.tail.getChild("TailPrimary");
        List<ModelPart> tailJoints = List.of(this.tailPrimary);
        animator = HumanoidAnimator.<T, LatexGrizzlyModel<T>>of(this)
                .hipOffset(-1.5F).torsoWidth(4.5F).torsoLength(9.0F)
                .addPreset(AnimatorPresets.deerLike(head, head.getChild("LeftEar"), head.getChild("RightEar"),
                        torso, leftArm, rightArm, this.tail, tailJoints,
                        leftLeg, leftLower, leftFoot, leftFoot.getChild("LeftPad"),
                        rightLeg, rightLower, rightFoot, rightFoot.getChild("RightPad")))
                .addAnimator(new VerticalTailInitAnimator<>(this.tail, tailJoints))
                .addAnimator(new MuntjacEarsAnimator<>(head.getChild("LeftEar"), head.getChild("RightEar")));
        SmoothGrabAnimator.install(animator, head, torso, leftArm, rightArm);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("cube_1_r1", CubeListBuilder.create().texOffs(22, 30).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.025F, -0.218166F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        RightLowerLeg.addOrReplaceChild("cube_2_r1", CubeListBuilder.create().texOffs(58, 16).addBox(-1.99F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.925F, 0.872665F, 0.0F, 0.0F));
        PartDefinition RightFoot = RightLowerLeg.addOrReplaceChild("RightFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        RightFoot.addOrReplaceChild("cube_3_r1", CubeListBuilder.create().texOffs(54, 30).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition RightPad = RightFoot.addOrReplaceChild("RightPad", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_4_r1", CubeListBuilder.create().texOffs(0, 39).addBox(-4.0F, -4.5F, -2.45F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -0.05F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_5_r1", CubeListBuilder.create().texOffs(34, 46).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(1.5F, 1.5F, -2.75F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_6_r1", CubeListBuilder.create().texOffs(34, 46).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(0.0F, 1.5F, -2.75F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_7_r1", CubeListBuilder.create().texOffs(34, 46).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(-1.5F, 1.5F, -2.75F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("cube_8_r1", CubeListBuilder.create().texOffs(38, 30).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.025F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        LeftLowerLeg.addOrReplaceChild("cube_9_r1", CubeListBuilder.create().texOffs(74, 16).addBox(-2.01F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.925F, 0.872665F, 0.0F, 0.0F));
        PartDefinition LeftFoot = LeftLowerLeg.addOrReplaceChild("LeftFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        LeftFoot.addOrReplaceChild("cube_10_r1", CubeListBuilder.create().texOffs(68, 30).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition LeftPad = LeftFoot.addOrReplaceChild("LeftPad", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_11_r1", CubeListBuilder.create().texOffs(18, 39).addBox(-4.0F, -4.5F, -2.45F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -0.05F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_12_r1", CubeListBuilder.create().texOffs(34, 46).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(1.5F, 1.5F, -2.75F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_13_r1", CubeListBuilder.create().texOffs(34, 46).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(0.0F, 1.5F, -2.75F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_14_r1", CubeListBuilder.create().texOffs(34, 46).addBox(-0.5F, -0.5F, -0.5F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(-1.5F, 1.5F, -2.75F, 0.0F, 0.0F, 0.0F));
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_15_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Hair = Head.addOrReplaceChild("Hair", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_16_r1", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_17_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_18_r1", CubeListBuilder.create().texOffs(84, 39).addBox(-2.0F, -29.0F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_19_r1", CubeListBuilder.create().texOffs(14, 46).addBox(-2.0F, -27.0F, -5.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_20_r1", CubeListBuilder.create().texOffs(22, 46).addBox(-1.0F, -29.65F, -1.05F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 26.0F, 0.0F, 0.174533F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_21_r1", CubeListBuilder.create().texOffs(0, 46).addBox(-0.5F, -1.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-4.0F, -1.25F, -2.6F, 0.0F, 0.261799F, 0.0F));
        Head.addOrReplaceChild("cube_22_r1", CubeListBuilder.create().texOffs(0, 46).addBox(-0.5F, -1.0F, -1.0F, 1.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(4.0F, -1.25F, -2.6F, 0.0F, -0.261799F, 0.0F));
        PartDefinition RightEar = Head.addOrReplaceChild("RightEar", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.0F, -7.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightEarPivot = RightEar.addOrReplaceChild("RightEarPivot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.174533F, -0.261799F));
        RightEarPivot.addOrReplaceChild("cube_23_r1", CubeListBuilder.create().texOffs(6, 46).addBox(-1.5F, -2.0F, -0.5F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_24_r1", CubeListBuilder.create().texOffs(28, 46).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftEar = Head.addOrReplaceChild("LeftEar", CubeListBuilder.create(), PartPose.offsetAndRotation(3.0F, -7.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftEarPivot = LeftEar.addOrReplaceChild("LeftEarPivot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, -0.174533F, 0.261799F));
        LeftEarPivot.addOrReplaceChild("cube_25_r1", CubeListBuilder.create().texOffs(6, 46).addBox(-1.5F, -2.0F, -0.5F, 3.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_26_r1", CubeListBuilder.create().texOffs(28, 46).addBox(-1.0F, -3.0F, -0.5F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_27_r1", CubeListBuilder.create().texOffs(0, 30).addBox(-4.0F, -26.0F, -2.0F, 7.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_28_r1", CubeListBuilder.create().texOffs(32, 16).addBox(-4.0F, -24.0F, -2.0F, 8.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 29.0F, -0.75F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_29_r1", CubeListBuilder.create().texOffs(36, 39).addBox(-4.0F, -26.0F, -2.0F, 7.0F, 1.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 37.0F, 0.025F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_30_r1", CubeListBuilder.create().texOffs(58, 39).addBox(-3.0F, -3.0F, -0.5F, 6.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 2.7F, 0.475F, -0.261799F, 0.0F, 0.0F));
        PartDefinition Tail = Torso.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 9.5F, 2.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailPrimary = Tail.addOrReplaceChild("TailPrimary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        TailPrimary.addOrReplaceChild("cube_31_r1", CubeListBuilder.create().texOffs(74, 39).addBox(-1.5F, -1.5F, 0.0F, 3.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.25F, -0.275F, 0.715585F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.5F, 3.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("cube_32_r1", CubeListBuilder.create().texOffs(64, 0).addBox(-4.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(4.5F, 3.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("cube_33_r1", CubeListBuilder.create().texOffs(80, 0).addBox(0.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 64);
    }

    @Override public void setupAnim(T entity, float swing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity, swing, amount, age, yaw, pitch);
        tail.yRot = 0.0F;
        tail.zRot = 0.0F;
        tailPrimary.yRot = 0.0F;
        tailPrimary.zRot = 0.0F;
    }

    /** Vertical-only tail lag and natural up/down wag (`xRot` only; `yRot` and `zRot` remain 0). */
    private static final class VerticalTailInitAnimator<T extends ChangedEntity, M extends AdvancedHumanoidModel<T>>
            extends AbstractTailAnimator<T, M> {
        private VerticalTailInitAnimator(ModelPart tail, List<ModelPart> tailJoints) {
            super(tail, tailJoints);
        }

        @Override
        public HumanoidAnimator.AnimateStage preferredStage() {
            return HumanoidAnimator.AnimateStage.INIT;
        }

        @Override
        public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
            float fallScale = 1.0F;
            if (entity.getFallFlyingTicks() > 4) {
                fallScale = (float) entity.getDeltaMovement().lengthSqr() / 0.2F;
                fallScale = fallScale * fallScale * fallScale;
                if (fallScale < 1.0F) {
                    fallScale = 1.0F;
                }
            }
            float naturalSway = 0.1F * Mth.cos(ageInTicks * 0.083333336F + (float) (Math.PI / 4.0D));
            float strideSway = Mth.cos(limbSwing * 0.6662F) * 0.35F * limbSwingAmount / fallScale;
            float verticalDrag = entity.getSimulatedSpring(
                    SpringType.HEAVY_WEAK, SpringType.Direction.VERTICAL, core.partialTicks) * 0.4F;

            tail.yRot = 0.0F;
            tail.zRot = 0.0F;
            tail.xRot = Mth.lerp(0.0F, naturalSway, 1.0F) + strideSway * 0.3F - verticalDrag * 0.65F;

            float offset = 0.0F;
            for (ModelPart joint : tailJoints) {
                joint.yRot = 0.0F;
                joint.zRot = 0.0F;
                float jointWave = 0.1F * Mth.cos(ageInTicks * 0.083333336F - ((float) Math.PI / 3.0F) * offset);
                joint.xRot = jointWave + strideSway * 0.25F - verticalDrag;
                offset += 0.75F;
            }
        }
    }
    @Override public ModelPart getArm(HumanoidArm side) { return side == HumanoidArm.LEFT ? leftArm : rightArm; }
    @Override public ModelPart getLeg(HumanoidArm side) { return side == HumanoidArm.LEFT ? leftLeg : rightLeg; }
    @Override public ModelPart getHead() { return head; }
    @Override public ModelPart getTorso() { return torso; }
    @Override public HumanoidAnimator<T, LatexGrizzlyModel<T>> getAnimator(T entity) { return animator; }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        leftLeg.render(pose, buffer, light, overlay, red, green, blue, alpha);
        rightLeg.render(pose, buffer, light, overlay, red, green, blue, alpha);
        head.render(pose, buffer, light, overlay, red, green, blue, alpha);
        torso.render(pose, buffer, light, overlay, red, green, blue, alpha);
        leftArm.render(pose, buffer, light, overlay, red, green, blue, alpha);
        rightArm.render(pose, buffer, light, overlay, red, green, blue, alpha);
    }
}
