package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Authored Field Coat (科考服) layers across standard bipedal, female, and grizzly proportions. */
public final class FieldCoatLayers {
    private FieldCoatLayers() {}

    public static LayerDefinition bipedal() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_CoatBody = Torso.addOrReplaceChild("Outfit_CoatBody", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("Coat_TorsoShell_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("Coat_ShoulderYoke_r2", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("Coat_WaistBelt_r3", CubeListBuilder.create().texOffs(48, 0).addBox(-4.0F, 9.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.34F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Collar = Torso.addOrReplaceChild("Outfit_Collar", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Collar.addOrReplaceChild("Collar_BackAndSides_r4", CubeListBuilder.create().texOffs(24, 7).addBox(-4.0F, -0.4F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.44F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_LapelR_r5", CubeListBuilder.create().texOffs(72, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 12.0F, 1.0F, new CubeDeformation(-0.08F)), PartPose.offsetAndRotation(-2.75F, 0.3F, -2.05F, 0.0F, 0.10472F, 0.0F));
        State_Open.addOrReplaceChild("Open_LapelL_r6", CubeListBuilder.create().texOffs(80, 0).addBox(-1.5F, 0.0F, -0.5F, 3.0F, 12.0F, 1.0F, new CubeDeformation(-0.08F)), PartPose.offsetAndRotation(2.75F, 0.3F, -2.05F, 0.0F, -0.10472F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_StormPlacket_r7", CubeListBuilder.create().texOffs(88, 0).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 12.0F, 1.0F, new CubeDeformation(-0.06F)), PartPose.offsetAndRotation(0.0F, 0.25F, -2.02F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_StormCollarFront_r8", CubeListBuilder.create().texOffs(100, 0).addBox(-3.5F, -1.0F, -0.5F, 7.0F, 2.0F, 1.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.5F, -2.18F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_DosimeterBadge_r9", CubeListBuilder.create().texOffs(100, 4).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(2.55F, 4.9F, -2.48F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_SamplePouchR_r10", CubeListBuilder.create().texOffs(108, 4).addBox(-1.0F, -0.4F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(-0.2F)), PartPose.offsetAndRotation(-2.75F, 7.6F, -2.55F, -0.087266F, -0.174533F, -0.069813F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SleeveR = RightArm.addOrReplaceChild("Outfit_SleeveR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveR.addOrReplaceChild("SleeveR_Main_r11", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_BracerR = RightArm.addOrReplaceChild("Outfit_BracerR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_BracerR.addOrReplaceChild("BracerR_Cuff_r12", CubeListBuilder.create().texOffs(32, 16).addBox(-3.0F, 6.4F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.33F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SleeveL = LeftArm.addOrReplaceChild("Outfit_SleeveL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveL.addOrReplaceChild("SleeveL_Main_r13", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_BracerL = LeftArm.addOrReplaceChild("Outfit_BracerL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_BracerL.addOrReplaceChild("BracerL_Cuff_r14", CubeListBuilder.create().texOffs(48, 16).addBox(-1.0F, 6.4F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.33F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        PartDefinition LegR_Open = RightLeg.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegR_Open.addOrReplaceChild("SkirtR_Open_r15", CubeListBuilder.create().texOffs(64, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LegR_Closed = RightLeg.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegR_Closed.addOrReplaceChild("SkirtR_Closed_r15", CubeListBuilder.create().texOffs(64, 104).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        PartDefinition LegL_Open = LeftLeg.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegL_Open.addOrReplaceChild("SkirtL_Open_r16", CubeListBuilder.create().texOffs(80, 16).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LegL_Closed = LeftLeg.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegL_Closed.addOrReplaceChild("SkirtL_Closed_r16", CubeListBuilder.create().texOffs(80, 104).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition female() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_CoatBody = Torso.addOrReplaceChild("Outfit_CoatBody", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_Upper_r1", CubeListBuilder.create().texOffs(0, 32).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 5.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_Waist_r2", CubeListBuilder.create().texOffs(24, 32).addBox(-4.0F, 4.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_Hip_r3", CubeListBuilder.create().texOffs(48, 32).addBox(-4.0F, 8.6F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.35F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_ShoulderYoke_r4", CubeListBuilder.create().texOffs(24, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.33F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_RightPlantoid_r5", CubeListBuilder.create().texOffs(0, 42).addBox(-4.25F, -1.7F, -0.8F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.279253F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_LeftPlantoid_r6", CubeListBuilder.create().texOffs(12, 42).addBox(0.25F, -1.7F, -0.8F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.279253F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("CoatF_PlantoidCenter_r7", CubeListBuilder.create().texOffs(24, 42).addBox(-0.5F, -1.3F, -0.1F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.38F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.191986F, 0.0F, 0.0F));
        PartDefinition Outfit_Collar = Torso.addOrReplaceChild("Outfit_Collar", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Collar.addOrReplaceChild("CollarF_BackAndSides_r8", CubeListBuilder.create().texOffs(24, 7).addBox(-4.0F, -0.6F, -2.0F, 8.0F, 3.0F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("OpenF_UpperFlapR_r9", CubeListBuilder.create().texOffs(36, 42).addBox(-4.25F, -1.7F, -0.85F, 2.75F, 3.8F, 1.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.279253F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("OpenF_UpperFlapL_r10", CubeListBuilder.create().texOffs(44, 42).addBox(1.5F, -1.7F, -0.85F, 2.75F, 3.8F, 1.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.279253F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("OpenF_LowerFlapR_r11", CubeListBuilder.create().texOffs(64, 42).addBox(-1.4F, 0.0F, -0.5F, 2.8F, 7.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-2.7F, 4.8F, -1.8F, 0.0F, 0.087266F, 0.0F));
        State_Open.addOrReplaceChild("OpenF_LowerFlapL_r12", CubeListBuilder.create().texOffs(72, 42).addBox(-1.4F, 0.0F, -0.5F, 2.8F, 7.0F, 1.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(2.7F, 4.8F, -1.8F, 0.0F, -0.087266F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("ClosedF_PlantoidBridge_r13", CubeListBuilder.create().texOffs(28, 42).addBox(-1.6F, -1.7F, -0.8F, 3.2F, 4.0F, 2.0F, new CubeDeformation(0.235F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.279253F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("ClosedF_LowerPlacket_r14", CubeListBuilder.create().texOffs(52, 42).addBox(-2.5F, 0.0F, -0.5F, 5.0F, 7.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.0F, 4.8F, -1.92F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("ClosedF_StormCollarFront_r15", CubeListBuilder.create().texOffs(100, 0).addBox(-3.5F, -0.9F, -0.5F, 7.0F, 1.8F, 1.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 0.5F, -2.42F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("PropF_DosimeterBadge_r16", CubeListBuilder.create().texOffs(100, 4).addBox(1.95F, -0.4F, -1.15F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(0.0F, 2.0F, -2.0F, -0.279253F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("PropF_SamplePouchR_r17", CubeListBuilder.create().texOffs(108, 4).addBox(-1.0F, -0.4F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(-0.22F)), PartPose.offsetAndRotation(-2.75F, 10.2F, -2.2F, 0.139626F, -0.174533F, -0.069813F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SleeveR = RightArm.addOrReplaceChild("Outfit_SleeveR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveR.addOrReplaceChild("SleeveFR_Main_r18", CubeListBuilder.create().texOffs(0, 16).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_BracerR = RightArm.addOrReplaceChild("Outfit_BracerR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_BracerR.addOrReplaceChild("BracerFR_Cuff_r19", CubeListBuilder.create().texOffs(32, 16).addBox(-3.0F, 6.4F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.33F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SleeveL = LeftArm.addOrReplaceChild("Outfit_SleeveL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveL.addOrReplaceChild("SleeveFL_Main_r20", CubeListBuilder.create().texOffs(16, 16).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 10.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_BracerL = LeftArm.addOrReplaceChild("Outfit_BracerL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_BracerL.addOrReplaceChild("BracerFL_Cuff_r21", CubeListBuilder.create().texOffs(48, 16).addBox(-1.0F, 6.4F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.33F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-0.1F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        PartDefinition LegFR_Open = RightLeg.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegFR_Open.addOrReplaceChild("SkirtFR_Open_r22", CubeListBuilder.create().texOffs(64, 16).addBox(-1.9F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(-0.1F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LegFR_Closed = RightLeg.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegFR_Closed.addOrReplaceChild("SkirtFR_Closed_r22", CubeListBuilder.create().texOffs(64, 104).addBox(-1.9F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(-0.1F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.1F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        PartDefinition LegFL_Open = LeftLeg.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegFL_Open.addOrReplaceChild("SkirtFL_Open_r23", CubeListBuilder.create().texOffs(80, 16).addBox(-2.1F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(0.1F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LegFL_Closed = LeftLeg.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LegFL_Closed.addOrReplaceChild("SkirtFL_Closed_r23", CubeListBuilder.create().texOffs(80, 104).addBox(-2.1F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(0.1F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition grizzly() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Head = root.addOrReplaceChild("Head", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_CoatBody = Torso.addOrReplaceChild("Outfit_CoatBody", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("Grizzly_BroadYoke_r1", CubeListBuilder.create().texOffs(0, 64).addBox(-3.5F, 0.0F, -2.5F, 7.0F, 5.0F, 5.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("Grizzly_ParkaBody_r2", CubeListBuilder.create().texOffs(24, 64).addBox(-4.0F, 3.7F, -2.85F, 8.0F, 8.0F, 5.0F, new CubeDeformation(0.3F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_CoatBody.addOrReplaceChild("Grizzly_LoadBelt_r3", CubeListBuilder.create().texOffs(50, 64).addBox(-4.0F, -1.0F, -2.5F, 8.0F, 2.0F, 5.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(0.0F, 10.1F, -0.35F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Collar = Torso.addOrReplaceChild("Outfit_Collar", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Collar.addOrReplaceChild("Grizzly_FurCollarBack_r4", CubeListBuilder.create().texOffs(50, 72).addBox(-4.0F, -1.0F, -1.0F, 8.0F, 2.0F, 2.0F, new CubeDeformation(0.44F)), PartPose.offsetAndRotation(0.0F, 0.1F, 1.75F, 0.0F, 0.0F, 0.0F));
        Outfit_Collar.addOrReplaceChild("Grizzly_FurCollarSideR_r5", CubeListBuilder.create().texOffs(76, 64).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(-3.3F, 0.1F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Collar.addOrReplaceChild("Grizzly_FurCollarSideL_r6", CubeListBuilder.create().texOffs(88, 64).addBox(-1.0F, -1.0F, -2.0F, 2.0F, 2.0F, 4.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(3.3F, 0.1F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Grizzly_OpenFlapR_r7", CubeListBuilder.create().texOffs(80, 80).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 11.0F, 1.0F, new CubeDeformation(-0.06F)), PartPose.offsetAndRotation(-2.55F, 0.8F, -2.95F, 0.0F, 0.122173F, 0.0F));
        State_Open.addOrReplaceChild("Grizzly_OpenFlapL_r8", CubeListBuilder.create().texOffs(86, 80).addBox(-1.0F, 0.0F, -0.5F, 2.0F, 11.0F, 1.0F, new CubeDeformation(-0.06F)), PartPose.offsetAndRotation(2.55F, 0.8F, -2.95F, 0.0F, -0.122173F, 0.0F));
        State_Open.addOrReplaceChild("Grizzly_HarnessStrapR_r9", CubeListBuilder.create().texOffs(104, 80).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(-0.12F)), PartPose.offsetAndRotation(-1.35F, 5.3F, -2.82F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Grizzly_HarnessStrapL_r10", CubeListBuilder.create().texOffs(104, 80).addBox(-0.5F, -5.0F, -0.5F, 1.0F, 10.0F, 1.0F, new CubeDeformation(-0.12F)), PartPose.offsetAndRotation(1.35F, 5.3F, -2.82F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Grizzly_ClosedPlacket_r11", CubeListBuilder.create().texOffs(92, 80).addBox(-2.5F, -5.5F, -0.5F, 5.0F, 11.0F, 1.0F, new CubeDeformation(-0.04F)), PartPose.offsetAndRotation(0.0F, 6.3F, -3.05F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Grizzly_ClosedCollarFront_r12", CubeListBuilder.create().texOffs(76, 72).addBox(-3.5F, -1.0F, -0.5F, 7.0F, 2.0F, 1.0F, new CubeDeformation(-0.05F)), PartPose.offsetAndRotation(0.0F, 0.3F, -2.95F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Grizzly_Dosimeter_r13", CubeListBuilder.create().texOffs(64, 96).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.18F)), PartPose.offsetAndRotation(2.45F, 3.3F, -3.35F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Grizzly_ToolPackR_r14", CubeListBuilder.create().texOffs(100, 64).addBox(-1.5F, -2.0F, -1.0F, 3.0F, 4.0F, 2.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(-3.05F, 10.3F, 3.05F, -0.10472F, 0.174533F, 0.0F));
        Outfit_Props.addOrReplaceChild("Grizzly_CoolantPouchL_r15", CubeListBuilder.create().texOffs(110, 64).addBox(-1.0F, -1.5F, -1.0F, 2.0F, 3.0F, 2.0F, new CubeDeformation(-0.16F)), PartPose.offsetAndRotation(2.4F, 10.4F, -2.95F, 0.10472F, -0.20944F, 0.069813F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-4.5F, 3.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SleeveR = RightArm.addOrReplaceChild("Outfit_SleeveR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveR.addOrReplaceChild("Grizzly_ShoulderYokePadR_r16", CubeListBuilder.create().texOffs(32, 96).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 2.5F, 4.0F, new CubeDeformation(0.34F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveR.addOrReplaceChild("Grizzly_ShortSleeveR_r17", CubeListBuilder.create().texOffs(0, 80).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveR.addOrReplaceChild("Grizzly_RolledCuffR_r18", CubeListBuilder.create().texOffs(32, 80).addBox(-3.0F, 2.2F, -2.0F, 4.0F, 1.8F, 4.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_BracerR = RightArm.addOrReplaceChild("Outfit_BracerR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_BracerR.addOrReplaceChild("Grizzly_WorkBracerR_r19", CubeListBuilder.create().texOffs(32, 86).addBox(-3.0F, 6.2F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(4.5F, 3.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SleeveL = LeftArm.addOrReplaceChild("Outfit_SleeveL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveL.addOrReplaceChild("Grizzly_ShoulderYokePadL_r20", CubeListBuilder.create().texOffs(48, 96).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 2.5F, 4.0F, new CubeDeformation(0.34F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveL.addOrReplaceChild("Grizzly_ShortSleeveL_r21", CubeListBuilder.create().texOffs(16, 80).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.25F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveL.addOrReplaceChild("Grizzly_CautionArmbandL_r22", CubeListBuilder.create().texOffs(64, 80).addBox(-1.0F, 0.5F, -2.0F, 4.0F, 1.7F, 4.0F, new CubeDeformation(0.36F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SleeveL.addOrReplaceChild("Grizzly_RolledCuffL_r23", CubeListBuilder.create().texOffs(48, 80).addBox(-1.0F, 2.2F, -2.0F, 4.0F, 1.8F, 4.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_BracerL = LeftArm.addOrReplaceChild("Outfit_BracerL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_BracerL.addOrReplaceChild("Grizzly_WorkBracerL_r24", CubeListBuilder.create().texOffs(48, 86).addBox(-1.0F, 6.2F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLowerLeg = RightLeg.addOrReplaceChild("RightLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SkirtR = RightLeg.addOrReplaceChild("Outfit_SkirtR", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SkirtR.addOrReplaceChild("Grizzly_ShortHemR_r25", CubeListBuilder.create().texOffs(0, 96).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.32F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.025F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.5F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLowerLeg = LeftLeg.addOrReplaceChild("LeftLowerLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 6.375F, -3.45F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_SkirtL = LeftLeg.addOrReplaceChild("Outfit_SkirtL", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_SkirtL.addOrReplaceChild("Grizzly_ShortHemL_r26", CubeListBuilder.create().texOffs(16, 96).addBox(-2.0F, 2.0F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.32F)), PartPose.offsetAndRotation(0.0F, 0.0F, -0.025F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition bipedalReference() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("Torso", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("RightArm", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("LeftArm", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("cube_1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("cube_2_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }

    public static LayerDefinition femaleReference() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        root.addOrReplaceChild("Head", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, -8.0F, -4.0F, 8.0F, 8.0F, 8.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("Torso", CubeListBuilder.create().texOffs(0, 0).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("RightArm", CubeListBuilder.create().texOffs(0, 0).addBox(-3.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(-5.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        root.addOrReplaceChild("LeftArm", CubeListBuilder.create().texOffs(0, 0).addBox(-1.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(5.0F, 2.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("cube_1_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("cube_2_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 7.0F, 4.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 64, 64);
    }
}
