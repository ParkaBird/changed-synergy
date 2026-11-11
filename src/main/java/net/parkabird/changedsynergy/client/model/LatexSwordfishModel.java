package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.List;
import net.ltxprogrammer.changed.client.renderer.animate.AnimatorPresets;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.parkabird.changedsynergy.entity.LatexSwordfish;
import net.parkabird.changedsynergy.init.ChangedSynergyAbilities;

/** Geometry and UVs from UnifiedLatexSwordfish.bbmodel, driven by Changed's aquatic rig. */
public final class LatexSwordfishModel extends AdvancedHumanoidModel<LatexSwordfish> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_swordfish"), "main");
    private final ModelPart root, head, torso, leftArm, rightArm, leftLeg, rightLeg, tail;
    private final HumanoidAnimator<LatexSwordfish, LatexSwordfishModel> animator;

    public LatexSwordfishModel(ModelPart root) {
        super(root);
        this.root=root; head=root.getChild("Head"); torso=root.getChild("Torso");
        leftArm=root.getChild("LeftArm"); rightArm=root.getChild("RightArm");
        leftLeg=root.getChild("LeftLeg"); rightLeg=root.getChild("RightLeg"); tail=torso.getChild("Tail");
        ModelPart primary=tail.getChild("TailPrimary"), secondary=primary.getChild("TailSecondary");
        ModelPart leftLower=leftLeg.getChild("LeftLowerLeg"), rightLower=rightLeg.getChild("RightLowerLeg");
        ModelPart leftFoot=leftLower.getChild("LeftFoot"), rightFoot=rightLower.getChild("RightFoot");
        animator=HumanoidAnimator.<LatexSwordfish, LatexSwordfishModel>of(this).hipOffset(-1.5F).legSpacing(0.5F)
                .addPreset(AnimatorPresets.sharkLike(head, torso, leftArm, rightArm, tail,
                        List.of(primary, secondary, secondary.getChild("TailTertiary")),
                        leftLeg,leftLower,leftFoot,leftFoot.getChild("LeftPad"),
                        rightLeg,rightLower,rightFoot,rightFoot.getChild("RightPad")));
        SmoothGrabAnimator.install(animator, head, torso, leftArm, rightArm);
    }

    public static LayerDefinition createBodyLayer() {
        // Changed collapses *_rN mesh wrappers before matching transfur geometry.
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("cube_1_r1", CubeListBuilder.create().texOffs(52, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0F, -0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        RightLowerLeg.addOrReplaceChild("cube_2_r1", CubeListBuilder.create().texOffs(56, 0).addBox(-1.99F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0F, -2.125F, 1.95F, 0.872665F, 0.0F, 0.0F));
        PartDefinition RightFoot = RightLowerLeg.addOrReplaceChild("RightFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        RightFoot.addOrReplaceChild("cube_3_r1", CubeListBuilder.create().texOffs(16, 64).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(-0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition RightPad = RightFoot.addOrReplaceChild("RightPad", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_4_r1", CubeListBuilder.create().texOffs(46, 62).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("cube_5_r1", CubeListBuilder.create().texOffs(0, 56).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0F, -0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        LeftLowerLeg.addOrReplaceChild("cube_6_r1", CubeListBuilder.create().texOffs(30, 61).addBox(-2.01F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0F, -2.125F, 1.95F, 0.872665F, 0.0F, 0.0F));
        PartDefinition LeftFoot = LeftLowerLeg.addOrReplaceChild("LeftFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        LeftFoot.addOrReplaceChild("cube_7_r1", CubeListBuilder.create().texOffs(64, 56).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(-0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition LeftPad = LeftFoot.addOrReplaceChild("LeftPad", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_8_r1", CubeListBuilder.create().texOffs(64, 49).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_9_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_10_r1", CubeListBuilder.create().texOffs(100, 37).addBox(0.0F, -12.0F, -3.0F, 0.0F, 4.0F, 7.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_11_r1", CubeListBuilder.create().texOffs(115, 37).addBox(0.0F, -12.0F, 4.0F, 0.0F, 9.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_12_r1", CubeListBuilder.create().texOffs(68, 16).addBox(-1.5F, -3.0F, -7.0F, 3.0F, 2.0F, 3.0F, new CubeDeformation(0.01F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_13_r1", CubeListBuilder.create().texOffs(68, 45).addBox(-1.5F, -1.0F, -6.0F, 3.0F, 1.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_14_r1", CubeListBuilder.create().texOffs(68, 39).addBox(-1.0F, 0.0F, 0.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.5F, -3.01F, -7.0F, 0.0F, 0.218166F, 0.0F));
        Head.addOrReplaceChild("cube_15_r1", CubeListBuilder.create().texOffs(46, 69).addBox(0.0F, 0.0F, 0.0F, 1.0F, 2.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.5F, -3.01F, -7.0F, 0.0F, -0.218166F, 0.0F));
        PartDefinition Hair = Head.addOrReplaceChild("Hair", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_16_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_17_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_18_r1", CubeListBuilder.create().texOffs(101, 27).addBox(-1.0F, -2.9F, -14.0F, 2.0F, 1.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_19_r1", CubeListBuilder.create().texOffs(88, 37).addBox(-0.5F, -2.75F, -18.0F, 1.0F, 0.7F, 4.1F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_36_r1", CubeListBuilder.create().texOffs(0, 80).addBox(-1.25F, -3.0F, -9.2F, 2.5F, 1.2F, 2.5F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_20_r1", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Tail = Torso.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailPrimary = Tail.addOrReplaceChild("TailPrimary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.75F, 0.0F, 0.0F, 0.0F));
        TailPrimary.addOrReplaceChild("cube_21_r1", CubeListBuilder.create().texOffs(88, 0).addBox(-2.0F, 0.75F, -0.8F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.0F, -1.0F, 1.178097F, 0.0F, 0.0F));
        TailPrimary.addOrReplaceChild("cube_22_r1", CubeListBuilder.create().texOffs(88, 27).addBox(-2.0F, -1.075F, 0.375F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.875F, 0.85F, 1.919862F, 0.0F, 0.0F));
        TailPrimary.addOrReplaceChild("cube_23_r1", CubeListBuilder.create().texOffs(88, 13).addBox(-4.0F, 0.0F, 0.25F, 1.0F, 9.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 1.75F, 1.0F, 1.788962F, 0.0F, 0.0F));
        TailPrimary.addOrReplaceChild("cube_24_r1", CubeListBuilder.create().texOffs(93, 13).addBox(-4.0F, 4.0F, -0.75F, 1.0F, 4.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, 1.75F, 1.0F, 1.788962F, 0.0F, 0.0F));
        PartDefinition TailSecondary = TailPrimary.addOrReplaceChild("TailSecondary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 3.25F, 7.25F, 0.0F, 0.0F, 0.0F));
        TailSecondary.addOrReplaceChild("cube_25_r1", CubeListBuilder.create().texOffs(105, 0).addBox(-1.5F, -1.3563F, -0.60882F, 3.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 1.25F, 1.0F, 1.308997F, 0.0F, 0.0F));
        PartDefinition TailTertiary = TailSecondary.addOrReplaceChild("TailTertiary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 4.5F, 0.0F, 0.0F, 0.0F));
        TailTertiary.addOrReplaceChild("cube_26_r1", CubeListBuilder.create().texOffs(118, 0).addBox(-1.0F, -0.34489F, -0.72029F, 2.0F, 5.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.25F, 0.25F, 1.48353F, 0.0F, 0.0F));
        TailTertiary.addOrReplaceChild("cube_27_r1", CubeListBuilder.create().texOffs(98, 13).addBox(-0.5F, -8.16682F, -2.11786F, 1.0F, 10.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 4.25F, -0.872665F, 0.0F, 0.0F));
        TailTertiary.addOrReplaceChild("cube_28_r1", CubeListBuilder.create().texOffs(107, 13).addBox(-0.5F, -6.16682F, 0.88214F, 1.0F, 7.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 4.25F, -0.872665F, 0.0F, 0.0F));
        TailTertiary.addOrReplaceChild("cube_29_r1", CubeListBuilder.create().texOffs(112, 13).addBox(-0.5F, -2.55383F, -1.82961F, 1.0F, 8.0F, 3.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.0F, 0.5F, 4.25F, 1.134464F, 0.0F, 0.0F));
        TailTertiary.addOrReplaceChild("cube_30_r1", CubeListBuilder.create().texOffs(121, 13).addBox(-0.5F, 5.34617F, -1.82961F, 1.0F, 1.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.0F, 0.5F, 4.25F, 1.134464F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_31_r1", CubeListBuilder.create().texOffs(88, 52).addBox(0.0F, -1.0F, 1.95F, 0.0F, 10.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("cube_32_r1", CubeListBuilder.create().texOffs(32, 45).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("cube_33_r1", CubeListBuilder.create().texOffs(105, 52).addBox(-3.02F, 2.0F, 0.8F, 0.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("cube_34_r1", CubeListBuilder.create().texOffs(32, 29).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("cube_35_r1", CubeListBuilder.create().texOffs(116, 52).addBox(3.02F, 2.0F, 0.8F, 0.0F, 6.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    @Override public void setupAnim(LatexSwordfish entity, float swing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        super.setupAnim(entity,swing,amount,age,yaw,pitch);
        var rush=entity.getAbilityInstance(ChangedSynergyAbilities.SWORDFISH_RUSH.get());
        if (rush != null) SwordfishRushPose.apply(this, rush.visualAmount(animator.partialTicks));
    }
    @Override public ModelPart getArm(HumanoidArm arm) { return arm==HumanoidArm.LEFT ? leftArm : rightArm; }
    @Override public ModelPart getLeg(HumanoidArm arm) { return arm==HumanoidArm.LEFT ? leftLeg : rightLeg; }
    @Override public ModelPart getHead() { return head; }
    @Override public ModelPart getTorso() { return torso; }
    @Override public HumanoidAnimator<LatexSwordfish,LatexSwordfishModel> getAnimator(LatexSwordfish entity) { return animator; }
    @Override public void renderToBuffer(PoseStack pose, VertexConsumer buffer, int light, int overlay, float r,float g,float b,float alpha) {
        for (ModelPart part : List.of(head,torso,leftArm,rightArm,leftLeg,rightLeg)) part.render(pose,buffer,light,overlay,r,g,b,alpha);
    }
}
