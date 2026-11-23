package net.parkabird.changedsynergy.client.model;

import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;

/** Authored Burrow Shawl layers tailored for Manul, Gray Fox, female, and standard bipedal cuts. */
public final class BurrowShawlLayers {
    private BurrowShawlLayers() {}

    public static LayerDefinition manulMale() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Shawl = Torso.addOrReplaceChild("Outfit_Shawl", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Shawl_ScarfWrap_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 0.5F, -2.0F, 7.0F, 2.0F, 4.0F, new CubeDeformation(0.56F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Shawl_BackCape_r2", CubeListBuilder.create().texOffs(23, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.5F, 2.65F, 0.20944F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Shawl_ScarfTailBack_r3", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(1.8F, 1.5F, 2.82F, 0.279253F, 0.0F, -0.139626F));
        Outfit_Shawl.addOrReplaceChild("Shawl_FrontDrapeR_r4", CubeListBuilder.create().texOffs(45, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-2.6F, 1.9F, -2.65F, -0.15708F, 0.0F, 0.069813F));
        Outfit_Shawl.addOrReplaceChild("Shawl_FrontDrapeL_r5", CubeListBuilder.create().texOffs(45, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.6F, 1.9F, -2.65F, -0.15708F, 0.0F, -0.069813F));
        PartDefinition Outfit_Straps = Torso.addOrReplaceChild("Outfit_Straps", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Straps.addOrReplaceChild("Strap_CrossFront_r6", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.3F, -2.78F, -0.05236F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Strap_CrossBack_r7", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.3F, 2.78F, 0.05236F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Belt_Waist_r8", CubeListBuilder.create().texOffs(53, 0).addBox(-4.0F, 9.7F, -2.5F, 8.0F, 1.0F, 5.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_BrassKeyRing_r9", CubeListBuilder.create().texOffs(105, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(-2.5F, 10.6F, -3.08F, -0.10472F, 0.0F, 0.069813F));
        Outfit_Props.addOrReplaceChild("Prop_LogisticsPouch_r10", CubeListBuilder.create().texOffs(110, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.04F)), PartPose.offsetAndRotation(2.4F, 10.9F, -3.15F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_ScarfKnot_r11", CubeListBuilder.create().texOffs(117, 0).addBox(-1.5F, 1.2F, -3.12F, 3.0F, 2.0F, 1.0F, new CubeDeformation(-0.14F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_GaiterWrap_r12", CubeListBuilder.create().texOffs(80, 6).addBox(-3.5F, -1.0F, -4.0F, 7.0F, 2.0F, 6.0F, new CubeDeformation(0.34F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_GaiterJawCup_r13", CubeListBuilder.create().texOffs(107, 4).addBox(-2.0F, -1.1F, -5.0F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition manulFemale() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 1.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Shawl = Torso.addOrReplaceChild("Outfit_Shawl", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Shawl_ScarfWrap_r1", CubeListBuilder.create().texOffs(0, 0).addBox(-3.5F, 0.5F, -2.0F, 7.0F, 2.0F, 4.0F, new CubeDeformation(0.6F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Shawl_BackCape_r2", CubeListBuilder.create().texOffs(23, 0).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.5F, 2.68F, 0.226893F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Shawl_ScarfTailBack_r3", CubeListBuilder.create().texOffs(40, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(1.8F, 1.5F, 2.86F, 0.296706F, 0.0F, -0.139626F));
        Outfit_Shawl.addOrReplaceChild("Shawl_FrontDrapeR_r4", CubeListBuilder.create().texOffs(45, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-2.7F, 1.9F, -2.72F, -0.139626F, 0.0F, 0.069813F));
        Outfit_Shawl.addOrReplaceChild("Shawl_FrontDrapeL_r5", CubeListBuilder.create().texOffs(45, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.7F, 1.9F, -2.72F, -0.139626F, 0.0F, -0.069813F));
        PartDefinition Outfit_Straps = Torso.addOrReplaceChild("Outfit_Straps", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Straps.addOrReplaceChild("Strap_CrossFront_r6", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.3F, -2.52F, 0.02618F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Strap_CrossBack_r7", CubeListBuilder.create().texOffs(50, 0).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.3F, 2.48F, -0.043633F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Belt_Waist_r8", CubeListBuilder.create().texOffs(80, 0).addBox(-4.0F, 9.7F, -2.0F, 8.0F, 1.0F, 4.0F, new CubeDeformation(0.38F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_BrassKeyRing_r9", CubeListBuilder.create().texOffs(105, 0).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.02F)), PartPose.offsetAndRotation(-2.5F, 10.6F, -2.62F, -0.10472F, 0.0F, 0.069813F));
        Outfit_Props.addOrReplaceChild("Prop_LogisticsPouch_r10", CubeListBuilder.create().texOffs(110, 0).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(0.04F)), PartPose.offsetAndRotation(2.4F, 10.9F, -2.68F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_ScarfKnot_r11", CubeListBuilder.create().texOffs(117, 0).addBox(-1.5F, 0.8F, -3.22F, 3.0F, 2.0F, 1.0F, new CubeDeformation(-0.15F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_GaiterWrap_r12", CubeListBuilder.create().texOffs(80, 6).addBox(-3.5F, -1.0F, -4.0F, 7.0F, 2.0F, 6.0F, new CubeDeformation(0.34F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_GaiterJawCup_r13", CubeListBuilder.create().texOffs(107, 4).addBox(-2.0F, -1.1F, -5.0F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition grayFoxMale() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Shawl = Torso.addOrReplaceChild("Outfit_Shawl", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Lookout_ScarfWrap_r1", CubeListBuilder.create().texOffs(0, 42).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.58F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Lookout_LeftShoulderCape_r2", CubeListBuilder.create().texOffs(25, 42).addBox(0.0F, -0.3F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Lookout_LeftShoulderDrape_r3", CubeListBuilder.create().texOffs(42, 42).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.8F, 1.0F, -2.72F, -0.122173F, 0.0F, -0.087266F));
        Outfit_Shawl.addOrReplaceChild("Lookout_BackHalfCloak_r4", CubeListBuilder.create().texOffs(49, 42).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(1.4F, 1.5F, 2.7F, 0.20944F, 0.0F, -0.10472F));
        Outfit_Shawl.addOrReplaceChild("Lookout_WindTail_r5", CubeListBuilder.create().texOffs(62, 42).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.2F, 1.5F, 2.86F, 0.296706F, 0.0F, -0.174533F));
        PartDefinition Outfit_Straps = Torso.addOrReplaceChild("Outfit_Straps", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Straps.addOrReplaceChild("Harness_XFrontA_r6", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, -2.5F, 0.0F, 0.0F, -0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_XFrontB_r7", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, -2.54F, 0.0F, 0.0F, 0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_XBackA_r8", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, 2.5F, 0.0F, 0.0F, -0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_XBackB_r9", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, 2.54F, 0.0F, 0.0F, 0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_WaistLoop_r10", CubeListBuilder.create().texOffs(70, 42).addBox(-4.0F, 9.5F, -2.0F, 8.0F, 1.0F, 4.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_SteelCarabiner_r11", CubeListBuilder.create().texOffs(95, 42).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.025F)), PartPose.offsetAndRotation(2.2F, 5.5F, -2.74F, 0.0F, 0.0F, -0.383972F));
        Outfit_Props.addOrReplaceChild("Prop_ScopePitonPack_r12", CubeListBuilder.create().texOffs(100, 42).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.04F)), PartPose.offsetAndRotation(-2.6F, 10.4F, -2.65F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_BandanaFold_r13", CubeListBuilder.create().texOffs(107, 42).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 4.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.3F, -2.7F, -0.15708F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_MaskCheeks_r14", CubeListBuilder.create().texOffs(95, 47).addBox(-4.0F, -1.2F, -4.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.24F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_MaskSnout_r15", CubeListBuilder.create().texOffs(70, 48).addBox(-2.0F, -1.4F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.18F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition grayFoxFemale() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Shawl = Torso.addOrReplaceChild("Outfit_Shawl", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Lookout_ScarfWrap_r1", CubeListBuilder.create().texOffs(0, 42).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.62F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Lookout_LeftShoulderCape_r2", CubeListBuilder.create().texOffs(25, 42).addBox(0.0F, -0.3F, -2.0F, 4.0F, 3.0F, 4.0F, new CubeDeformation(0.46F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Lookout_LeftShoulderDrape_r3", CubeListBuilder.create().texOffs(42, 42).addBox(-1.5F, 0.0F, 0.0F, 3.0F, 3.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.8F, 1.0F, -2.84F, -0.122173F, 0.0F, -0.087266F));
        Outfit_Shawl.addOrReplaceChild("Lookout_BackHalfCloak_r4", CubeListBuilder.create().texOffs(49, 42).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(1.4F, 1.5F, 2.74F, 0.226893F, 0.0F, -0.10472F));
        Outfit_Shawl.addOrReplaceChild("Lookout_WindTail_r5", CubeListBuilder.create().texOffs(62, 42).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 7.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.2F, 1.5F, 2.9F, 0.314159F, 0.0F, -0.174533F));
        PartDefinition Outfit_Straps = Torso.addOrReplaceChild("Outfit_Straps", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Straps.addOrReplaceChild("Harness_XFrontA_r6", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, -2.62F, 0.043633F, 0.0F, -0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_XFrontB_r7", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, -2.66F, 0.043633F, 0.0F, 0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_XBackA_r8", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, 2.6F, -0.034907F, 0.0F, -0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_XBackB_r9", CubeListBuilder.create().texOffs(67, 42).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, 2.64F, -0.034907F, 0.0F, 0.558505F));
        Outfit_Straps.addOrReplaceChild("Harness_WaistLoop_r10", CubeListBuilder.create().texOffs(70, 42).addBox(-4.0F, 9.5F, -2.0F, 8.0F, 1.0F, 4.0F, new CubeDeformation(0.42F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_SteelCarabiner_r11", CubeListBuilder.create().texOffs(95, 42).addBox(-1.0F, -1.5F, 0.0F, 2.0F, 3.0F, 0.0F, new CubeDeformation(0.025F)), PartPose.offsetAndRotation(2.2F, 5.5F, -2.86F, 0.0F, 0.0F, -0.383972F));
        Outfit_Props.addOrReplaceChild("Prop_ScopePitonPack_r12", CubeListBuilder.create().texOffs(100, 42).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.04F)), PartPose.offsetAndRotation(-2.6F, 10.4F, -2.7F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_BandanaFold_r13", CubeListBuilder.create().texOffs(107, 42).addBox(-3.0F, 0.0F, 0.0F, 6.0F, 4.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.3F, -2.82F, -0.15708F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_MaskCheeks_r14", CubeListBuilder.create().texOffs(95, 47).addBox(-4.0F, -1.2F, -4.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.24F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_MaskSnout_r15", CubeListBuilder.create().texOffs(70, 48).addBox(-2.0F, -1.4F, -6.0F, 4.0F, 2.0F, 2.0F, new CubeDeformation(0.18F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition bipedal() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, -0.5F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Shawl = Torso.addOrReplaceChild("Outfit_Shawl", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Trail_ScarfWrap_r1", CubeListBuilder.create().texOffs(0, 85).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.58F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Trail_BackTriCape_r2", CubeListBuilder.create().texOffs(25, 85).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.5F, 2.65F, 0.20944F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Trail_ScarfTailBack_r3", CubeListBuilder.create().texOffs(42, 85).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-2.0F, 1.5F, 2.82F, 0.279253F, 0.0F, 0.139626F));
        Outfit_Shawl.addOrReplaceChild("Trail_FrontDrapeR_r4", CubeListBuilder.create().texOffs(47, 85).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-2.8F, 1.5F, -2.65F, -0.139626F, 0.0F, 0.069813F));
        Outfit_Shawl.addOrReplaceChild("Trail_FrontDrapeL_r5", CubeListBuilder.create().texOffs(47, 85).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.8F, 1.5F, -2.65F, -0.139626F, 0.0F, -0.069813F));
        PartDefinition Outfit_Straps = Torso.addOrReplaceChild("Outfit_Straps", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Straps.addOrReplaceChild("Trail_CrossStrapFront_r6", CubeListBuilder.create().texOffs(52, 85).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, -2.52F, 0.0F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Trail_CrossStrapBack_r7", CubeListBuilder.create().texOffs(52, 85).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, 2.52F, 0.0F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Trail_WaistBelt_r8", CubeListBuilder.create().texOffs(58, 85).addBox(-4.0F, 9.5F, -2.0F, 8.0F, 1.0F, 4.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_BrassClasp_r9", CubeListBuilder.create().texOffs(83, 85).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.24F)), PartPose.offsetAndRotation(-2.7F, 2.1F, -2.62F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_SideSatchel_r10", CubeListBuilder.create().texOffs(90, 85).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.04F)), PartPose.offsetAndRotation(2.6F, 10.4F, -2.65F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_CollarFolds_r11", CubeListBuilder.create().texOffs(97, 85).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.4F, -2.68F, -0.15708F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_WarmerWrap_r12", CubeListBuilder.create().texOffs(97, 89).addBox(-4.0F, -1.3F, -4.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_WarmerFront_r13", CubeListBuilder.create().texOffs(106, 85).addBox(-2.0F, -1.3F, -5.2F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }

    public static LayerDefinition female() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();
        PartDefinition Torso = root.addOrReplaceChild("Torso", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Shawl = Torso.addOrReplaceChild("Outfit_Shawl", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Trail_ScarfWrap_r1", CubeListBuilder.create().texOffs(0, 85).addBox(-4.0F, 0.0F, -2.0F, 8.0F, 2.0F, 4.0F, new CubeDeformation(0.58F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Trail_BackTriCape_r2", CubeListBuilder.create().texOffs(25, 85).addBox(-4.0F, 0.0F, 0.0F, 8.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 1.5F, 2.65F, 0.20944F, 0.0F, 0.0F));
        Outfit_Shawl.addOrReplaceChild("Trail_ScarfTailBack_r3", CubeListBuilder.create().texOffs(42, 85).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 6.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-2.0F, 1.5F, 2.82F, 0.279253F, 0.0F, 0.139626F));
        Outfit_Shawl.addOrReplaceChild("Trail_FrontDrapeR_r4", CubeListBuilder.create().texOffs(47, 85).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-2.9F, 0.7F, -2.65F, -0.287979F, 0.0F, 0.069813F));
        Outfit_Shawl.addOrReplaceChild("Trail_FrontDrapeL_r5", CubeListBuilder.create().texOffs(47, 85).addBox(-1.0F, 0.0F, 0.0F, 2.0F, 4.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(2.9F, 0.7F, -2.65F, -0.287979F, 0.0F, -0.069813F));
        PartDefinition Outfit_Straps = Torso.addOrReplaceChild("Outfit_Straps", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Straps.addOrReplaceChild("Trail_CrossStrapUpper_r6", CubeListBuilder.create().texOffs(55, 85).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-0.4F, 4.5F, -3.52F, -0.200713F, 0.0F, -0.558505F));
        Outfit_Straps.addOrReplaceChild("Trail_CrossStrapLower_r7", CubeListBuilder.create().texOffs(55, 85).addBox(-0.5F, 0.0F, 0.0F, 1.0F, 5.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(-0.4F, 4.5F, -3.52F, 0.235619F, 0.0F, -0.558505F));
        Outfit_Straps.addOrReplaceChild("Trail_CrossStrapBack_r8", CubeListBuilder.create().texOffs(52, 85).addBox(-0.5F, -5.0F, 0.0F, 1.0F, 10.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 5.2F, 2.52F, 0.0F, 0.0F, -0.593412F));
        Outfit_Straps.addOrReplaceChild("Trail_WaistBelt_r9", CubeListBuilder.create().texOffs(58, 85).addBox(-4.0F, 9.4F, -2.0F, 8.0F, 1.0F, 4.0F, new CubeDeformation(0.4F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        PartDefinition Outfit_Props = Torso.addOrReplaceChild("Outfit_Props", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_BrassClasp_r10", CubeListBuilder.create().texOffs(83, 85).addBox(-1.0F, -1.0F, -0.5F, 2.0F, 2.0F, 1.0F, new CubeDeformation(-0.26F)), PartPose.offsetAndRotation(-2.8F, 1.6F, -2.88F, -0.279253F, 0.0F, 0.0F));
        Outfit_Props.addOrReplaceChild("Prop_SideSatchel_r11", CubeListBuilder.create().texOffs(90, 85).addBox(-1.0F, -1.5F, -0.5F, 2.0F, 3.0F, 1.0F, new CubeDeformation(0.04F)), PartPose.offsetAndRotation(2.6F, 10.3F, -2.65F, 0.0F, 0.0F, 0.0F));
        PartDefinition State_Open = Torso.addOrReplaceChild("State_Open", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Open.addOrReplaceChild("Open_CollarFolds_r12", CubeListBuilder.create().texOffs(97, 85).addBox(-2.0F, 0.0F, 0.0F, 4.0F, 3.0F, 0.0F, new CubeDeformation(0.0125F)), PartPose.offsetAndRotation(0.0F, 0.9F, -2.68F, -0.279253F, 0.0F, 0.0F));
        PartDefinition State_Closed = Torso.addOrReplaceChild("State_Closed", CubeListBuilder.create(), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_WarmerWrap_r13", CubeListBuilder.create().texOffs(97, 89).addBox(-4.0F, -1.3F, -4.0F, 8.0F, 2.0F, 6.0F, new CubeDeformation(0.28F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        State_Closed.addOrReplaceChild("Closed_WarmerFront_r14", CubeListBuilder.create().texOffs(106, 85).addBox(-2.0F, -1.3F, -5.2F, 4.0F, 2.0F, 1.0F, new CubeDeformation(0.22F)), PartPose.offsetAndRotation(0.0F, 0.0F, 0.0F, 0.0F, 0.0F, 0.0F));
        return LayerDefinition.create(mesh, 128, 128);
    }
}
