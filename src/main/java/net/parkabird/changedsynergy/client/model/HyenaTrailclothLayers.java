package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;

/** Imported from the supplied bbmodels with original pivots, geometry and UVs. */
public final class HyenaTrailclothLayers {
    private HyenaTrailclothLayers() {}
    public static LayerDefinition maleUpper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_Torso_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 12.0F, 4.0F, new CubeDeformation(0.2F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("Trailcloth_LeftWristWrap_r1", CubeListBuilder.create().texOffs(27, 1).addBox(0.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.19F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("Trailcloth_LeftShoulder_r1", CubeListBuilder.create().texOffs(63, 1).addBox(0.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.32F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("Trailcloth_RightWristWrap_r1", CubeListBuilder.create().texOffs(45, 1).addBox(-4.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.19F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static LayerDefinition maleLower() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_WaistBelt_r1", CubeListBuilder.create().texOffs(81, 1).addBox(-4.0F, 9.1F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_IvoryClasp_r1", CubeListBuilder.create().texOffs(19, 19).addBox(-1.0F, 9.75F, -3.1F, 2.0F, 1.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_HipPouch_r1", CubeListBuilder.create().texOffs(27, 19).addBox(-5.1F, 10.9F, 2.3F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.06F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("Trailcloth_LeftSplitWrap_r1", CubeListBuilder.create().texOffs(107, 1).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.5F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("Trailcloth_RightSplitWrap_r1", CubeListBuilder.create().texOffs(1, 19).addBox(-2.0F, 0.0F, -2.0F, 4.0F, 5.0F, 4.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static LayerDefinition femaleUpper() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -1.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Plantoids = Torso.addOrReplaceChild("Plantoids", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        Plantoids.addOrReplaceChild("Trailcloth_LeftPlantoid_r1", CubeListBuilder.create().texOffs(53, 1).addBox(0.25F, -1.7F, -0.8F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.17F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.279253F, 0.0F, 0.0F));
        Plantoids.addOrReplaceChild("Trailcloth_RightPlantoid_r1", CubeListBuilder.create().texOffs(67, 1).addBox(-4.25F, -1.7F, -0.8F, 4.0F, 4.0F, 2.0F, new CubeDeformation(0.17F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.279253F, 0.0F, 0.0F));
        Plantoids.addOrReplaceChild("Trailcloth_Center_r1", CubeListBuilder.create().texOffs(81, 1).addBox(-0.5F, -1.3F, -0.1F, 1.0F, 3.0F, 1.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 2.0F, 0.0F, -0.191986F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_Torso_r1", CubeListBuilder.create().texOffs(1, 1).addBox(-4.0F, -13.2F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 13.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_TorsoLower_r1", CubeListBuilder.create().texOffs(27, 1).addBox(-4.0F, -8.0F, -2.0F, 8.0F, 6.0F, 4.0F, new CubeDeformation(-0.1F)), PartPose.offsetAndRotation(0.0F, 13.2F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.3F, 0.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("Trailcloth_LeftWristWrap_r1", CubeListBuilder.create().texOffs(87, 1).addBox(0.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.29F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftArm.addOrReplaceChild("Trailcloth_LeftShoulder_r1", CubeListBuilder.create().texOffs(1, 19).addBox(0.0F, -2.0F, -2.0F, 4.0F, 2.0F, 4.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(-1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.3F, 0.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightArm.addOrReplaceChild("Trailcloth_RightWristWrap_r1", CubeListBuilder.create().texOffs(105, 1).addBox(-4.0F, -2.0F, -2.0F, 4.0F, 12.0F, 4.0F, new CubeDeformation(0.29F)), PartPose.offsetAndRotation(1.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
    public static LayerDefinition femaleLower() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -1.7F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Plantoids = Torso.addOrReplaceChild("Plantoids", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, -2.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_WaistBelt_r1", CubeListBuilder.create().texOffs(19, 19).addBox(-4.0F, 10.3F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_IvoryClasp_r1", CubeListBuilder.create().texOffs(81, 19).addBox(-1.0F, 9.95F, -3.1F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.0F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Torso.addOrReplaceChild("Trailcloth_HipPouch_r1", CubeListBuilder.create().texOffs(89, 19).addBox(-5.1F, 12.1F, 2.3F, 2.0F, 2.0F, 2.0F, new CubeDeformation(0.06F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftArm = root.addOrReplaceChild("LeftArm", CubeListBuilder.create(), PartPose.offsetAndRotation(5.3F, 0.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition RightArm = root.addOrReplaceChild("RightArm", CubeListBuilder.create(), PartPose.offsetAndRotation(-5.3F, 0.3F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition LeftLeg = root.addOrReplaceChild("LeftLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        LeftLeg.addOrReplaceChild("Trailcloth_LeftSplitWrap_r1", CubeListBuilder.create().texOffs(45, 19).addBox(-2.1F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.38F)), PartPose.offsetAndRotation(0.1F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        PartDefinition RightLeg = root.addOrReplaceChild("RightLeg", CubeListBuilder.create(), PartPose.offsetAndRotation(-2.4F, 10.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        RightLeg.addOrReplaceChild("Trailcloth_RightSplitWrap_r1", CubeListBuilder.create().texOffs(63, 19).addBox(-1.9F, 0.0F, -2.0F, 4.0F, 6.0F, 4.0F, new CubeDeformation(0.38F)), PartPose.offsetAndRotation(-0.1F, 0.0F, 0.0F, -0.218166F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
