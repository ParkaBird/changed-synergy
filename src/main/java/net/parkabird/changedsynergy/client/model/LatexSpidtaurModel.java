package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.animations.Limb;
import net.ltxprogrammer.changed.client.renderer.animate.AnimatorPresets;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.arm.DoubleArmBobAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.upperbody.DoubleArmUpperBodyAttackAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.upperbody.DoubleArmUpperBodyCrouchAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.upperbody.DoubleArmUpperBodyInitAnimator;
import net.ltxprogrammer.changed.client.renderer.animate.upperbody.DoubleArmUpperBodyStandAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel;
import net.ltxprogrammer.changed.client.renderer.model.LowerTorsoedModel;
import net.ltxprogrammer.changed.client.tfanimations.HelperModel;
import net.ltxprogrammer.changed.client.tfanimations.TransfurHelper;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.parkabird.changedsynergy.entity.LatexSpidtaur;

public class LatexSpidtaurModel extends AdvancedHumanoidModel<LatexSpidtaur>
        implements LowerTorsoedModel, DoubleArmedModel<LatexSpidtaur> {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "latex_spidtaur"), "main");

    private final HumanoidAnimator<LatexSpidtaur, LatexSpidtaurModel> animator;
    private final ModelPart Head;
    private final ModelPart Torso;
    private final ModelPart LowerTorso;
    private final ModelPart Saddle;
    private final ModelPart LeftArm;
    private final ModelPart RightArm;
    private final ModelPart LeftArm2;
    private final ModelPart RightArm2;
    private final ModelPart FrontLeftLeg;
    private final ModelPart FrontRightLeg;

    public LatexSpidtaurModel(ModelPart root) {
        super(root);
        this.Head = root.getChild("Head");
        this.Torso = root.getChild("Torso");
        this.LowerTorso = root.getChild("LowerTorso");
        this.Saddle = this.LowerTorso.getChild("Saddle");
        this.LeftArm = root.getChild("LeftArm");
        this.RightArm = root.getChild("RightArm");
        this.LeftArm2 = root.getChild("LeftArm2");
        this.RightArm2 = root.getChild("RightArm2");
        this.FrontLeftLeg = this.LowerTorso.getChild("LeftLeg");
        this.FrontRightLeg = this.LowerTorso.getChild("RightLeg");
        ModelPart backLeftLeg = this.LowerTorso.getChild("LeftLeg2");
        ModelPart backRightLeg = this.LowerTorso.getChild("RightLeg2");
        ModelPart frontLeftLowerLeg = this.FrontLeftLeg.getChild("LeftLowerLeg");
        ModelPart frontRightLowerLeg = this.FrontRightLeg.getChild("RightLowerLeg");
        ModelPart backLeftLowerLeg = backLeftLeg.getChild("LeftLowerLeg2");
        ModelPart backRightLowerLeg = backRightLeg.getChild("RightLowerLeg2");
        ModelPart backLeftFoot = backLeftLowerLeg.getChild("LeftFoot2");
        ModelPart backRightFoot = backRightLowerLeg.getChild("RightFoot2");
        this.animator = HumanoidAnimator.<LatexSpidtaur, LatexSpidtaurModel>of(this)
                .addPreset(AnimatorPresets.taurLike(
                        this.Head,
                        this.Head.getChild("LeftAntenna"),
                        this.Head.getChild("RightAntenna"),
                        this.Torso,
                        this.LeftArm,
                        this.RightArm,
                        this.LowerTorso,
                        this.FrontLeftLeg,
                        frontLeftLowerLeg,
                        frontLeftLowerLeg.getChild("LeftFoot"),
                        this.FrontRightLeg,
                        frontRightLowerLeg,
                        frontRightLowerLeg.getChild("RightFoot"),
                        backLeftLeg,
                        backLeftLowerLeg,
                        backLeftFoot,
                        backLeftFoot.getChild("LeftPad2"),
                        backRightLeg,
                        backRightLowerLeg,
                        backRightFoot,
                        backRightFoot.getChild("RightPad2")))
                .setupHands(1, this.LeftArm, this.RightArm)
                // Antenna roots carry Synergy ear follow; pivots keep the authored bind tilt.
                .addAnimator(new MuntjacEarsAnimator<>(
                        this.Head.getChild("LeftAntenna"),
                        this.Head.getChild("RightAntenna")))
                .addAnimator(new SmoothGrabAnimator<>(this.Head, this.Torso, this.LeftArm, this.RightArm))
                .addAnimator(new SmoothGrabAnimator<>(this.Head, this.Torso, this.LeftArm2, this.RightArm2))
                .addAnimator(new DoubleArmUpperBodyInitAnimator<>(
                        this.Head, this.Torso, this.LeftArm, this.RightArm, this.LeftArm2, this.RightArm2))
                .addAnimator(new DoubleArmUpperBodyCrouchAnimator<>(
                        this.Head, this.Torso, this.LeftArm, this.RightArm, this.LeftArm2, this.RightArm2))
                .addAnimator(new DoubleArmUpperBodyAttackAnimator<>(
                        this.Head, this.Torso, this.LeftArm, this.RightArm, this.LeftArm2, this.RightArm2))
                .addAnimator(new DoubleArmUpperBodyStandAnimator<>(
                        this.Head, this.Torso, this.LeftArm, this.RightArm, this.LeftArm2, this.RightArm2))
                .addAnimator(new DoubleArmBobAnimator<>(this.LeftArm, this.RightArm, this.LeftArm2, this.RightArm2))
                // Primary arms get this from taurLike; secondary arms need the same jump Y counter-shift.
                .addAnimator(new TaurSecondaryArmJumpAnimator<>(this.LeftArm2, this.RightArm2))
                .forwardOffset(-7.0F)
                .hipOffset(-1.5F)
                .legLength(13.5F)
                .torsoLength(11.05F);
    }

    public static LayerDefinition createBodyLayer() {
        MeshDefinition meshdefinition = new MeshDefinition();
        PartDefinition partdefinition = meshdefinition.getRoot();

        PartDefinition Head = partdefinition.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(1, 28).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(1, 104).addBox(-2.0F, -3.0F, -5.5F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.0F))
        .texOffs(15, 104).addBox(-1.5F, -1.0F, -4.75F, 3.0F, 1.0F, 1.0F, new CubeDeformation(0.025F)), PartPose.offset(0.0F, -1.5F, -7.0F));

        PartDefinition Hair = Head.addOrReplaceChild("Hair", CubeListBuilder.create().texOffs(1, 59).addBox(-4.0F, -8.6F, -2.0F, 8.0F, 1.0F, 6.0F, new CubeDeformation(0.05F))
        .texOffs(31, 59).addBox(-4.0F, -7.5F, 3.8F, 8.0F, 6.0F, 1.0F, new CubeDeformation(0.025F)), PartPose.offset(0.0F, 0.0F, 0.0F));

        PartDefinition RightCrownFur_r1 = Hair.addOrReplaceChild("RightCrownFur_r1", CubeListBuilder.create().texOffs(71, 59).addBox(-1.5F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.5F, -7.3F, 1.0F, 0.0F, 0.0F, 0.1745F));

        PartDefinition LeftCrownFur_r1 = Hair.addOrReplaceChild("LeftCrownFur_r1", CubeListBuilder.create().texOffs(71, 59).addBox(-0.5F, -1.0F, -2.5F, 2.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.5F, -7.3F, 1.0F, 0.0F, 0.0F, -0.1745F));

        PartDefinition LeftChelicera = Head.addOrReplaceChild("LeftChelicera", CubeListBuilder.create(), PartPose.offset(3.05F, -2.3F, -3.9F));

        PartDefinition LeftFang_r1 = LeftChelicera.addOrReplaceChild("LeftFang_r1", CubeListBuilder.create().texOffs(13, 122).addBox(-0.5F, 1.6F, -1.8F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(1, 122).addBox(-1.0F, 0.0F, -1.7F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.2F, 0.1F, -0.1396F, 0.0F, 0.1222F));

        PartDefinition RightChelicera = Head.addOrReplaceChild("RightChelicera", CubeListBuilder.create(), PartPose.offset(-3.05F, -2.3F, -3.9F));

        PartDefinition RightFang_r1 = RightChelicera.addOrReplaceChild("RightFang_r1", CubeListBuilder.create().texOffs(13, 122).addBox(-0.5F, 1.6F, -1.8F, 1.0F, 1.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(1, 122).addBox(-1.0F, 0.0F, -1.7F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.2F, 0.1F, -0.1396F, 0.0F, -0.1222F));

        PartDefinition LeftAntenna = Head.addOrReplaceChild("LeftAntenna", CubeListBuilder.create(), PartPose.offset(2.35F, -7.3F, -2.2F));

        PartDefinition LeftAntennaPivot = LeftAntenna.addOrReplaceChild("LeftAntennaPivot", CubeListBuilder.create().texOffs(108, 104).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.48F, -0.1047F, 0.2443F));

        PartDefinition LeftUpperAntenna_r1 = LeftAntennaPivot.addOrReplaceChild("LeftUpperAntenna_r1", CubeListBuilder.create().texOffs(108, 111).addBox(-0.5F, -2.8F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.7F, 0.0F, -0.3927F, 0.0F, 0.0F));

        PartDefinition RightAntenna = Head.addOrReplaceChild("RightAntenna", CubeListBuilder.create(), PartPose.offset(-2.35F, -7.3F, -2.2F));

        PartDefinition RightAntennaPivot = RightAntenna.addOrReplaceChild("RightAntennaPivot", CubeListBuilder.create().texOffs(118, 104).addBox(-1.0F, -3.0F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.48F, 0.1047F, -0.2443F));

        PartDefinition RightUpperAntenna_r1 = RightAntennaPivot.addOrReplaceChild("RightUpperAntenna_r1", CubeListBuilder.create().texOffs(118, 111).addBox(-0.5F, -2.8F, -0.5F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.7F, 0.0F, -0.3927F, 0.0F, 0.0F));

        PartDefinition Torso = partdefinition.addOrReplaceChild("Torso", CubeListBuilder.create().texOffs(35, 28).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 10.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, -1.5F, -7.0F));

        PartDefinition ChestFur = Torso.addOrReplaceChild("ChestFur", CubeListBuilder.create(), PartPose.offset(0.0F, 1.0F, -1.5F));

        PartDefinition RightChestFur_r1 = ChestFur.addOrReplaceChild("RightChestFur_r1", CubeListBuilder.create().texOffs(119, 59).addBox(-1.0F, -0.5F, -1.3F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-3.0F, -0.8F, 0.0F, 0.1396F, 0.0F, 0.3142F));

        PartDefinition LeftChestFur_r1 = ChestFur.addOrReplaceChild("LeftChestFur_r1", CubeListBuilder.create().texOffs(119, 59).addBox(-1.0F, -0.5F, -1.3F, 2.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(3.0F, -0.8F, 0.0F, 0.1396F, 0.0F, -0.3142F));

        PartDefinition ChestFurCenter_r1 = ChestFur.addOrReplaceChild("ChestFurCenter_r1", CubeListBuilder.create().texOffs(105, 59).addBox(-2.0F, -0.5F, -1.5F, 4.0F, 3.0F, 2.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.2094F, 0.0F, 0.0F));

        PartDefinition LeftArm = partdefinition.addOrReplaceChild("LeftArm", CubeListBuilder.create().texOffs(45, 46).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(5.0F, 0.5F, -7.0F));

        PartDefinition LeftForearm = LeftArm.addOrReplaceChild("LeftForearm", CubeListBuilder.create().texOffs(63, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.025F))
        .texOffs(87, 59).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.18F)), PartPose.offset(1.0F, 4.0F, 0.0F));

        PartDefinition LeftForearmFurTip_r1 = LeftForearm.addOrReplaceChild("LeftForearmFurTip_r1", CubeListBuilder.create().texOffs(21, 68).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 1.7F, -1.6F, 0.0F, 0.0F, -0.3142F));

        PartDefinition RightArm = partdefinition.addOrReplaceChild("RightArm", CubeListBuilder.create().texOffs(45, 46).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offset(-5.0F, 0.5F, -7.0F));

        PartDefinition RightForearm = RightArm.addOrReplaceChild("RightForearm", CubeListBuilder.create().texOffs(63, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.025F))
        .texOffs(87, 59).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.18F)), PartPose.offset(-1.0F, 4.0F, 0.0F));

        PartDefinition RightForearmFurTip_r1 = RightForearm.addOrReplaceChild("RightForearmFurTip_r1", CubeListBuilder.create().texOffs(21, 68).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 1.7F, -1.6F, 0.0F, 0.0F, 0.3142F));

        PartDefinition LeftArm2 = partdefinition.addOrReplaceChild("LeftArm2", CubeListBuilder.create().texOffs(45, 46).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.05F)), PartPose.offset(5.0F, 4.5F, -7.0F));

        PartDefinition LeftForearm2 = LeftArm2.addOrReplaceChild("LeftForearm2", CubeListBuilder.create().texOffs(63, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.075F))
        .texOffs(87, 59).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.18F)), PartPose.offset(1.0F, 4.0F, 0.0F));

        PartDefinition LeftForearm2FurTip_r1 = LeftForearm2.addOrReplaceChild("LeftForearm2FurTip_r1", CubeListBuilder.create().texOffs(21, 68).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(1.0F, 1.7F, -1.6F, 0.0F, 0.0F, -0.3142F));

        PartDefinition RightArm2 = partdefinition.addOrReplaceChild("RightArm2", CubeListBuilder.create().texOffs(45, 46).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.05F)), PartPose.offset(-5.0F, 4.5F, -7.0F));

        PartDefinition RightForearm2 = RightArm2.addOrReplaceChild("RightForearm2", CubeListBuilder.create().texOffs(63, 46).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(-0.075F))
        .texOffs(87, 59).addBox(-2.0F, 1.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.18F)), PartPose.offset(-1.0F, 4.0F, 0.0F));

        PartDefinition RightForearm2FurTip_r1 = RightForearm2.addOrReplaceChild("RightForearm2FurTip_r1", CubeListBuilder.create().texOffs(21, 68).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-1.0F, 1.7F, -1.6F, 0.0F, 0.0F, 0.3142F));

        PartDefinition LowerTorso = partdefinition.addOrReplaceChild("LowerTorso", CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, -2.0F, -2.0F, 8.0F, 6.0F, 19.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 10.5F, -7.0F));

        PartDefinition Abdomen = LowerTorso.addOrReplaceChild("Abdomen", CubeListBuilder.create().texOffs(57, 1).addBox(-4.0F, -3.5F, -1.0F, 8.0F, 7.0F, 10.0F, new CubeDeformation(0.08F))
        .texOffs(61, 28).addBox(-3.0F, -4.0F, 0.0F, 6.0F, 1.0F, 8.0F, new CubeDeformation(0.0F))
        .texOffs(19, 46).addBox(-3.0F, -2.25F, 7.7F, 6.0F, 5.0F, 3.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 0.0F, 14.5F));

        PartDefinition Spinnerets = Abdomen.addOrReplaceChild("Spinnerets", CubeListBuilder.create().texOffs(29, 68).addBox(0.4F, -1.0F, -0.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F))
        .texOffs(29, 68).addBox(-1.4F, -1.0F, -0.3F, 1.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 1.0F, 10.7F));

        PartDefinition LeftLeg2 = LowerTorso.addOrReplaceChild("LeftLeg2", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, 15.875F));

        PartDefinition LeftLeg2Geometry_r1 = LeftLeg2.addOrReplaceChild("LeftLeg2Geometry_r1", CubeListBuilder.create().texOffs(99, 28).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F));

        PartDefinition LeftLowerLeg2 = LeftLeg2.addOrReplaceChild("LeftLowerLeg2", CubeListBuilder.create(), PartPose.offset(0.0F, 6.375F, -3.45F));

        PartDefinition LeftLowerLeg2Geometry_r1 = LeftLowerLeg2.addOrReplaceChild("LeftLowerLeg2Geometry_r1", CubeListBuilder.create().texOffs(81, 46).addBox(-2.01F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.95F, 0.8727F, 0.0F, 0.0F));

        PartDefinition LeftFoot2 = LeftLowerLeg2.addOrReplaceChild("LeftFoot2", CubeListBuilder.create(), PartPose.offset(0.0F, 0.8F, 7.175F));

        PartDefinition LeftFoot2Geometry_r1 = LeftFoot2.addOrReplaceChild("LeftFoot2Geometry_r1", CubeListBuilder.create().texOffs(99, 46).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.3491F, 0.0F, 0.0F));

        PartDefinition LeftPad2 = LeftFoot2.addOrReplaceChild("LeftPad2", CubeListBuilder.create().texOffs(51, 59).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.325F, -4.425F));

        PartDefinition RightLeg2 = LowerTorso.addOrReplaceChild("RightLeg2", CubeListBuilder.create(), PartPose.offset(-3.5F, 0.0F, 16.375F));

        PartDefinition RightLeg2Geometry_r1 = RightLeg2.addOrReplaceChild("RightLeg2Geometry_r1", CubeListBuilder.create().texOffs(99, 28).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.2182F, 0.0F, 0.0F));

        PartDefinition RightLowerLeg2 = RightLeg2.addOrReplaceChild("RightLowerLeg2", CubeListBuilder.create(), PartPose.offset(0.0F, 6.375F, -3.45F));

        PartDefinition RightLowerLeg2Geometry_r1 = RightLowerLeg2.addOrReplaceChild("RightLowerLeg2Geometry_r1", CubeListBuilder.create().texOffs(81, 46).addBox(-1.99F, -0.125F, -2.9F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -2.125F, 1.95F, 0.8727F, 0.0F, 0.0F));

        PartDefinition RightFoot2 = RightLowerLeg2.addOrReplaceChild("RightFoot2", CubeListBuilder.create(), PartPose.offset(0.0F, 0.8F, 7.175F));

        PartDefinition RightFoot2Geometry_r1 = RightFoot2.addOrReplaceChild("RightFoot2Geometry_r1", CubeListBuilder.create().texOffs(99, 46).addBox(-2.0F, -8.45F, -0.725F, 4.0F, 6.0F, 3.0F, new CubeDeformation(0.005F)), PartPose.offsetAndRotation(0.0F, 7.075F, -4.975F, -0.3491F, 0.0F, 0.0F));

        PartDefinition RightPad2 = RightFoot2.addOrReplaceChild("RightPad2", CubeListBuilder.create().texOffs(51, 59).addBox(-2.0F, 0.0F, -2.5F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 4.325F, -4.425F));

        PartDefinition LeftLeg = LowerTorso.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offset(3.5F, 0.0F, -1.7F));

        PartDefinition LeftLegGeometry_r1 = LeftLeg.addOrReplaceChild("LeftLegGeometry_r1", CubeListBuilder.create().texOffs(99, 28).addBox(-2.0F, -6.89F, -4.2461F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(0.25F, 5.5348F, 3.9528F, 0.0873F, 0.0F, 0.0F));

        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offset(0.25F, 5.7848F, 3.7028F));

        PartDefinition LeftLowerLegGeometry_r1 = LeftLowerLeg.addOrReplaceChild("LeftLowerLegGeometry_r1", CubeListBuilder.create().texOffs(1, 46).addBox(-2.0F, 3.8638F, 2.7342F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -5.275F, -5.575F, -0.2182F, 0.0F, 0.0F));

        PartDefinition LeftFoot = LeftLowerLeg.addOrReplaceChild("LeftFoot", CubeListBuilder.create().texOffs(51, 59).addBox(-1.95F, 0.0F, -2.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.0F, 5.7152F, -4.3278F));

        PartDefinition RightLeg = LowerTorso.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offset(-4.0F, 0.0F, -1.7F));

        PartDefinition RightLegGeometry_r1 = RightLeg.addOrReplaceChild("RightLegGeometry_r1", CubeListBuilder.create().texOffs(99, 28).addBox(-9.5F, -6.89F, -4.2461F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.1F)), PartPose.offsetAndRotation(7.75F, 5.5348F, 3.9528F, 0.0873F, 0.0F, 0.0F));

        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offset(0.0F, 5.7848F, 3.7028F));

        PartDefinition RightLowerLegGeometry_r1 = RightLowerLeg.addOrReplaceChild("RightLowerLegGeometry_r1", CubeListBuilder.create().texOffs(1, 46).addBox(-2.0F, 3.8638F, 2.7342F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.25F, -5.275F, -5.575F, -0.2182F, 0.0F, 0.0F));

        PartDefinition RightFoot = RightLowerLeg.addOrReplaceChild("RightFoot", CubeListBuilder.create().texOffs(51, 59).addBox(-2.025F, 0.0F, -2.0F, 4.0F, 2.0F, 5.0F, new CubeDeformation(0.0F)), PartPose.offset(0.25F, 5.7152F, -4.3278F));

        PartDefinition Saddle = LowerTorso.addOrReplaceChild("Saddle", CubeListBuilder.create().texOffs(1, 80).addBox(-4.0F, -5.0F, 3.0F, 8.0F, 9.0F, 10.0F, new CubeDeformation(0.15F))
        .texOffs(70, 104).addBox(-4.0F, -5.75F, 4.0F, 8.0F, 1.0F, 8.0F, new CubeDeformation(0.04F)), PartPose.offset(0.0F, 3.0F, 0.0F));

        PartDefinition SaddleRearRoll_r1 = Saddle.addOrReplaceChild("SaddleRearRoll_r1", CubeListBuilder.create().texOffs(70, 116).addBox(-4.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -5.9F, 12.3F, 0.1745F, 0.0F, 0.0F));

        PartDefinition SaddleFrontRoll_r1 = Saddle.addOrReplaceChild("SaddleFrontRoll_r1", CubeListBuilder.create().texOffs(70, 116).addBox(-4.0F, -0.5F, -0.5F, 8.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -6.0F, 4.3F, -0.1745F, 0.0F, 0.0F));

        return LayerDefinition.create(meshdefinition, 128, 128);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer vertexConsumer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {
        this.Head.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.Torso.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.LeftArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.RightArm.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.LeftArm2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        this.RightArm2.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        swapResetPoseStack(poseStack);
        this.LowerTorso.render(poseStack, vertexConsumer, packedLight, packedOverlay, red, green, blue, alpha);
        swapResetPoseStack(poseStack);
    }

    @Override
    public ModelPart getArm(HumanoidArm side) {
        return side == HumanoidArm.LEFT ? this.LeftArm : this.RightArm;
    }

    @Override
    public ModelPart getOtherArm(HumanoidArm side) {
        return side == HumanoidArm.LEFT ? this.LeftArm2 : this.RightArm2;
    }

    @Override
    public ModelPart getLeg(HumanoidArm side) {
        return side == HumanoidArm.LEFT ? this.FrontLeftLeg : this.FrontRightLeg;
    }

    @Override
    public ModelPart getHead() {
        return this.Head;
    }

    @Override
    public ModelPart getTorso() {
        return this.Torso;
    }

    @Override
    public ModelPart getLowerTorso() {
        return this.LowerTorso;
    }

    @Override
    public HumanoidAnimator<LatexSpidtaur, LatexSpidtaurModel> getAnimator(LatexSpidtaur entity) {
        return this.animator;
    }

    @Override
    public void translateToUpperHand(LatexSpidtaur entity, HumanoidArm side, PoseStack poseStack) {
        getArm(side).translateAndRotate(poseStack);
        poseStack.translate(0.0, (animator.armLength - 12.0F) / 20.0, 0.0);
    }

    @Override
    public void translateToLowerHand(LatexSpidtaur entity, HumanoidArm side, PoseStack poseStack) {
        getOtherArm(side).translateAndRotate(poseStack);
        poseStack.translate(0.0, (animator.armLength - 12.0F) / 20.0, 0.0);
    }

    @Override
    public HelperModel getTransfurHelperModel(Limb limb) {
        if (limb == Limb.LOWER_TORSO) {
            return TransfurHelper.getTaurTorso();
        }
        if (limb == Limb.TORSO) {
            return null;
        }
        return super.getTransfurHelperModel(limb);
    }

    @Override
    public boolean shouldPartTransfur(ModelPart part) {
        return super.shouldPartTransfur(part) && part != this.Saddle;
    }
}