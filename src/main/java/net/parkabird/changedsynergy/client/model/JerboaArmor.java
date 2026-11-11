package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.*;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Native UVs on the jerboa's 80% body; limb pivots follow the final wearer pose. */
public final class JerboaArmor extends ArmorLatexDeerModel<ChangedEntity> {
    public static final ArmorModelSet<ChangedEntity,JerboaArmor> SET=ArmorModelSet.of(ResourceLocation.parse("changed_synergy:jerboa_armor"),JerboaArmor::layer,JerboaArmor::new);
    private JerboaArmor(ModelPart root,ArmorModel layer){
        super(root,layer);
        root.getChild("Torso").getChild("Shell").xScale=.8F;
        root.getChild("Torso").getChild("Shell").yScale=.8F;
        root.getChild("Torso").getChild("Shell").zScale=.8F;
        for(String side:new String[]{"Left","Right"}){
            var sleeve=root.getChild(side+"Arm").getChild("Shell");sleeve.xScale=.6F;sleeve.yScale=sleeve.zScale=.8F;
            var leg=root.getChild(side+"Leg");
            var lower=leg.getChild(side+"LowerLeg");var foot=lower.getChild(side+"Foot");
            for(var part:new ModelPart[]{leg.getChild(side+"Thigh_r1"),lower.getChild(side+"Calf_r1"),foot.getChild(side+"Arch_r1"),foot.getChild(side+"Pad").getChild("Shell")}){
                part.x*=.8F;part.y*=.8F;part.z*=.8F;part.xScale=part.yScale=part.zScale=.8F;
            }
        }
    }
    private static LayerDefinition layer(ArmorModel layer){
        var mesh=new MeshDefinition();var root=mesh.getRoot();ArmorLatexDeerModel.addUnifiedLegs(root,layer);
        var torso=root.addOrReplaceChild("Torso",CubeListBuilder.create(),PartPose.offset(0,4.4F,0));
        torso.addOrReplaceChild("Shell",CubeListBuilder.create().texOffs(16,16).addBox(-4,0,-2,8,12,4,layer.dualDeformation),PartPose.ZERO);
        var tail=torso.addOrReplaceChild("Tail",CubeListBuilder.create(),PartPose.offset(0,8.4F,0));tail.addOrReplaceChild("TailPrimary",CubeListBuilder.create(),PartPose.ZERO);
        root.addOrReplaceChild("Head",CubeListBuilder.create().texOffs(0,0).addBox(-4,-8,-4,8,8,8,layer.dualDeformation),PartPose.offset(0,4.4F,0));
        for(boolean left:new boolean[]{false,true}){
            String side=left?"Left":"Right";
            var arm=root.addOrReplaceChild(side+"Arm",CubeListBuilder.create(),PartPose.offset(left?4:-4,6,0));
            arm.addOrReplaceChild("Shell",CubeListBuilder.create().texOffs(40,16).mirror(left).addBox(left?-1:-3,-2,-2,4,12,4,layer.dualDeformation),PartPose.offset(left?-.2F:.2F,0,0));
            var foot=root.getChild(side+"Leg").getChild(side+"LowerLeg").getChild(side+"Foot");
            var pad=foot.addOrReplaceChild(side+"Pad",CubeListBuilder.create(),PartPose.offset(0,3.46F,-3.54F));
            pad.addOrReplaceChild("Shell",CubeListBuilder.create().texOffs(21,21).mirror(left).addBox(-2,0,-2.5F,4,2,5,layer.deformation.extend(-.25F)),PartPose.ZERO);
        }
        return LayerDefinition.create(mesh,64,32);
    }
    @Override public void renderForSlot(ChangedEntity entity,RenderLayerParent<? super ChangedEntity,?> parent,
            ItemStack stack,EquipmentSlot slot,PoseStack pose,VertexConsumer buffer,int light,int overlay,
            float red,float green,float blue,float alpha){
        if(parent.getModel() instanceof AdvancedHumanoidModel<?> body)ArmorPoseFit.copy(this,body);
        super.renderForSlot(entity,parent,stack,slot,pose,buffer,light,overlay,red,green,blue,alpha);
    }
}
