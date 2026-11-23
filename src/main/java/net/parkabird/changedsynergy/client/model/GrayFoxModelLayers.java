package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Original pivots and UVs preserved from latex_gray_fox bbmodels. */
public final class GrayFoxModelLayers {
    private GrayFoxModelLayers() {}

    public static LayerDefinition male() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("cube_1_r1", CubeListBuilder.create().texOffs(48, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        RightLowerLeg.addOrReplaceChild("cube_2_r1", CubeListBuilder.create().texOffs(48, 40).addBox(-1.99F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.95F, 0.872665F, 0.0F, 0.0F));
        PartDefinition RightFoot = RightLowerLeg.addOrReplaceChild("RightFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        RightFoot.addOrReplaceChild("cube_3_r1", CubeListBuilder.create().texOffs(13, 57).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition RightPad = RightFoot.addOrReplaceChild("RightPad", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_4_r1", CubeListBuilder.create().texOffs(52, 32).addBox(-4.0F, -4.5F, -2.45F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -0.05F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("cube_5_r1", CubeListBuilder.create().texOffs(32, 44).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        LeftLowerLeg.addOrReplaceChild("cube_6_r1", CubeListBuilder.create().texOffs(48, 22).addBox(-2.01F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.95F, 0.872665F, 0.0F, 0.0F));
        PartDefinition LeftFoot = LeftLowerLeg.addOrReplaceChild("LeftFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        LeftFoot.addOrReplaceChild("cube_7_r1", CubeListBuilder.create().texOffs(56, 11).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition LeftPad = LeftFoot.addOrReplaceChild("LeftPad", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_8_r1", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0F, -4.5F, -2.45F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -0.05F, 0.0F, 0.0F, 0.0F));
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_9_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightEar = Head.addOrReplaceChild("RightEar", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.0F, -7.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightEarPivot = RightEar.addOrReplaceChild("RightEarPivot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.5F, -1.25F, 0.0F, -0.1309F, 0.523599F, -0.349066F));
        RightEarPivot.addOrReplaceChild("cube_10_r1", CubeListBuilder.create().texOffs(0, 4).addBox(-7.1F, -1.2F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_11_r1", CubeListBuilder.create().texOffs(0, 16).addBox(-6.1F, -1.6F, -0.4F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.025F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_12_r1", CubeListBuilder.create().texOffs(32, 22).addBox(-6.1F, -2.3F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.035F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_13_r1", CubeListBuilder.create().texOffs(24, 0).addBox(-5.1F, -3.1F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftEar = Head.addOrReplaceChild("LeftEar", CubeListBuilder.create(), PartPose.offsetAndRotation(3.0F, -7.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftEarPivot = LeftEar.addOrReplaceChild("LeftEarPivot", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, -1.25F, 0.0F, -0.1309F, -0.523599F, 0.349066F));
        LeftEarPivot.addOrReplaceChild("cube_14_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-0.9F, -1.2F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(-0.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_15_r1", CubeListBuilder.create().texOffs(0, 20).addBox(-1.0F, -1.4F, -0.4F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.025F)), PartPose.offsetAndRotation(-0.1F, -0.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_16_r1", CubeListBuilder.create().texOffs(32, 24).addBox(0.0F, 0.9F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.035F)), PartPose.offsetAndRotation(-1.1F, -3.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_17_r1", CubeListBuilder.create().texOffs(0, 32).addBox(1.0F, 1.1F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(-2.1F, -4.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Hair = Head.addOrReplaceChild("Hair", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_18_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_19_r1", CubeListBuilder.create().texOffs(24, 8).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 6.0F, 8.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_20_r1", CubeListBuilder.create().texOffs(15, 32).addBox(-2.0F, -29.0F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_21_r1", CubeListBuilder.create().texOffs(24, 22).addBox(-2.0F, -27.0F, -5.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_22_r1", CubeListBuilder.create().texOffs(24, 2).addBox(-1.0F, -29.625F, -0.95F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 26.0F, 0.0F, 0.174533F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_23_r1", CubeListBuilder.create().texOffs(28, 28).addBox(-4.0F, -26.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Tail = Torso.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailPrimary = Tail.addOrReplaceChild("TailPrimary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailPrimary_bind_r1 = TailPrimary.addOrReplaceChild("TailPrimary_bind_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1309F, 0.0F, 0.0F));
        TailPrimary_bind_r1.addOrReplaceChild("cube_24_r1", CubeListBuilder.create().texOffs(48, 50).addBox(-2.0F, 0.75F, -1.5F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 1.178097F, 0.0F, 0.0F));
        PartDefinition TailSecondary = TailPrimary_bind_r1.addOrReplaceChild("TailSecondary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.25F, 4.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailSecondary_bind_r1 = TailSecondary.addOrReplaceChild("TailSecondary_bind_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        TailSecondary_bind_r1.addOrReplaceChild("cube_25_r1", CubeListBuilder.create().texOffs(28, 56).addBox(-2.0F, -0.45F, -1.5F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.45F)), PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 1.48353F, 0.0F, 0.0F));
        PartDefinition TailTertiary = TailSecondary_bind_r1.addOrReplaceChild("TailTertiary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.75F, 6.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailTertiary_bind_r1 = TailTertiary.addOrReplaceChild("TailTertiary_bind_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        TailTertiary_bind_r1.addOrReplaceChild("cube_26_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-2.0F, -0.7F, -1.95F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 1.832596F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("cube_27_r1", CubeListBuilder.create().texOffs(16, 40).addBox(-4.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("cube_28_r1", CubeListBuilder.create().texOffs(0, 40).addBox(0.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 96, 96);
    }

    public static LayerDefinition female() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("cube_1_r1", CubeListBuilder.create().texOffs(40, 18).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        RightLowerLeg.addOrReplaceChild("cube_2_r1", CubeListBuilder.create().texOffs(0, 33).addBox(-1.99F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.95F, 0.872665F, 0.0F, 0.0F));
        PartDefinition RightFoot = RightLowerLeg.addOrReplaceChild("RightFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        RightFoot.addOrReplaceChild("cube_3_r1", CubeListBuilder.create().texOffs(72, 33).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition RightPad = RightFoot.addOrReplaceChild("RightPad", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        RightPad.addOrReplaceChild("cube_4_r1", CubeListBuilder.create().texOffs(54, 43).addBox(-4.0F, -4.5F, -2.45F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -0.05F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("cube_5_r1", CubeListBuilder.create().texOffs(56, 18).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        LeftLowerLeg.addOrReplaceChild("cube_6_r1", CubeListBuilder.create().texOffs(16, 33).addBox(-2.01F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.95F, 0.872665F, 0.0F, 0.0F));
        PartDefinition LeftFoot = LeftLowerLeg.addOrReplaceChild("LeftFoot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.8F, 7.175F, 0.0F, 0.0F, 0.0F));
        LeftFoot.addOrReplaceChild("cube_7_r1", CubeListBuilder.create().texOffs(0, 43).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.349066F, 0.0F, 0.0F));
        PartDefinition LeftPad = LeftFoot.addOrReplaceChild("LeftPad", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 4.325F, -4.425F, 0.0F, 0.0F, 0.0F));
        LeftPad.addOrReplaceChild("cube_8_r1", CubeListBuilder.create().texOffs(72, 43).addBox(-4.0F, -4.5F, -2.45F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(2.0F, 4.5F, -0.05F, 0.0F, 0.0F, 0.0F));
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_9_r1", CubeListBuilder.create().texOffs(64, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightEar = Head.addOrReplaceChild("RightEar", CubeListBuilder.create(), PartPose.offsetAndRotation(-3.0F, -7.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightEarPivot = RightEar.addOrReplaceChild("RightEarPivot", CubeListBuilder.create(), PartPose.offsetAndRotation(0.5F, -1.25F, 0.0F, -0.1309F, 0.523599F, -0.349066F));
        RightEarPivot.addOrReplaceChild("cube_10_r1", CubeListBuilder.create().texOffs(12, 52).addBox(-7.1F, -1.2F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_11_r1", CubeListBuilder.create().texOffs(28, 52).addBox(-6.1F, -1.6F, -0.4F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.025F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_12_r1", CubeListBuilder.create().texOffs(48, 52).addBox(-6.1F, -2.3F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.035F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightEarPivot.addOrReplaceChild("cube_13_r1", CubeListBuilder.create().texOffs(64, 52).addBox(-5.1F, -3.1F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(5.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftEar = Head.addOrReplaceChild("LeftEar", CubeListBuilder.create(), PartPose.offsetAndRotation(3.0F, -7.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftEarPivot = LeftEar.addOrReplaceChild("LeftEarPivot", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.5F, -1.25F, 0.0F, -0.1309F, -0.523599F, 0.349066F));
        LeftEarPivot.addOrReplaceChild("cube_14_r1", CubeListBuilder.create().texOffs(20, 52).addBox(-0.9F, -1.2F, -1.0F, 3.0F, 3.0F, 1.0F, new CubeDeformation(0.05F)), PartPose.offsetAndRotation(-0.2F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_15_r1", CubeListBuilder.create().texOffs(34, 52).addBox(-1.0F, -1.4F, -0.4F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.025F)), PartPose.offsetAndRotation(-0.1F, -0.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_16_r1", CubeListBuilder.create().texOffs(54, 52).addBox(0.0F, 0.9F, -1.0F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.035F)), PartPose.offsetAndRotation(-1.1F, -3.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftEarPivot.addOrReplaceChild("cube_17_r1", CubeListBuilder.create().texOffs(68, 52).addBox(1.0F, 1.1F, -1.0F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(-2.1F, -4.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Hair = Head.addOrReplaceChild("Hair", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_18_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 10.0F, 8.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Hair.addOrReplaceChild("cube_19_r1", CubeListBuilder.create().texOffs(32, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 9.0F, 8.0F, new CubeDeformation(0.35F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_20_r1", CubeListBuilder.create().texOffs(0, 52).addBox(-2.0F, -29.0F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_21_r1", CubeListBuilder.create().texOffs(40, 52).addBox(-2.0F, -27.0F, -5.0F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Head.addOrReplaceChild("cube_22_r1", CubeListBuilder.create().texOffs(60, 52).addBox(-1.0F, -29.625F, -0.95F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.5F, 26.0F, 0.0F, 0.174533F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_23_r1", CubeListBuilder.create().texOffs(72, 18).addBox(-4.0F, -26.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 26.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_24_r1", CubeListBuilder.create().texOffs(32, 33).addBox(-4.0F, 5.0F, -2.0F, 8.0F, 5.0F, 4.0F, new CubeDeformation(-0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("cube_25_r1", CubeListBuilder.create().texOffs(30, 43).addBox(-4.0F, 9.0F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Tail = Torso.addOrReplaceChild("Tail", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailPrimary = Tail.addOrReplaceChild("TailPrimary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailPrimary_bind_r1 = TailPrimary.addOrReplaceChild("TailPrimary_bind_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.1309F, 0.0F, 0.0F));
        TailPrimary_bind_r1.addOrReplaceChild("cube_26_r1", CubeListBuilder.create().texOffs(56, 33).addBox(-2.0F, 0.75F, -1.5F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 1.178097F, 0.0F, 0.0F));
        PartDefinition TailSecondary = TailPrimary_bind_r1.addOrReplaceChild("TailSecondary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.25F, 4.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailSecondary_bind_r1 = TailSecondary.addOrReplaceChild("TailSecondary_bind_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        TailSecondary_bind_r1.addOrReplaceChild("cube_27_r1", CubeListBuilder.create().texOffs(24, 18).addBox(-2.0F, -0.45F, -1.5F, 4.0F, 8.0F, 4.0F, new CubeDeformation(0.45F)), PartPose.offsetAndRotation(0.0F, 0.5F, 0.0F, 1.48353F, 0.0F, 0.0F));
        PartDefinition TailTertiary = TailSecondary_bind_r1.addOrReplaceChild("TailTertiary", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.75F, 6.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition TailTertiary_bind_r1 = TailTertiary.addOrReplaceChild("TailTertiary_bind_r1", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        TailTertiary_bind_r1.addOrReplaceChild("cube_28_r1", CubeListBuilder.create().texOffs(14, 43).addBox(-2.0F, -0.7F, -1.95F, 4.0F, 4.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 1.0F, 1.832596F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("cube_29_r1", CubeListBuilder.create().texOffs(0, 18).addBox(-2.0F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("cube_30_r1", CubeListBuilder.create().texOffs(12, 18).addBox(-1.0F, -2.0F, -1.5F, 3.0F, 12.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 96, 96);
    }
}
