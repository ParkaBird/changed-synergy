package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.*;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.entity.variant.ClothingShape;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.*;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.parkabird.changedsynergy.client.model.DesertOutfitLayers;
import net.parkabird.changedsynergy.entity.LatexJerboa;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.*;

@Mod.EventBusSubscriber(modid="changed_synergy",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class DesertOutfitRenderer extends ClothingModelFit implements AccessoryRenderer {
    private static final String[] CUTS={"bipedal","fourarm","tail","pup","jerboa","synergy_female","legacy_female"};
    private final ModelPart[] top=new ModelPart[CUTS.length],bottom=new ModelPart[CUTS.length],reference=new ModelPart[5];
    private final ModelPart humanReference;
    public DesertOutfitRenderer(){
        var models=Minecraft.getInstance().getEntityModels();for(int i=0;i<CUTS.length;i++){top[i]=models.bakeLayer(layer(i,"upper"));bottom[i]=models.bakeLayer(layer(i,"lower"));}
        for(int i=0;i<reference.length;i++)reference[i]=models.bakeLayer(layer(i,"reference"));humanReference=models.bakeLayer(ModelLayers.PLAYER);
    }
    private static ModelLayerLocation layer(int cut,String section){return new ModelLayerLocation(ResourceLocation.parse("changed_synergy:desert_outfit/"+CUTS[cut]+"/"+section),"main");}
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event){
        event.registerLayerDefinition(layer(0,"upper"),DesertOutfitLayers::bipedalUpper);event.registerLayerDefinition(layer(0,"lower"),DesertOutfitLayers::bipedalLower);event.registerLayerDefinition(layer(0,"reference"),DesertOutfitLayers::bipedalReference);
        event.registerLayerDefinition(layer(1,"upper"),DesertOutfitLayers::fourarmUpper);event.registerLayerDefinition(layer(1,"lower"),DesertOutfitLayers::fourarmLower);event.registerLayerDefinition(layer(1,"reference"),DesertOutfitLayers::fourarmReference);
        event.registerLayerDefinition(layer(2,"upper"),DesertOutfitLayers::tailUpper);event.registerLayerDefinition(layer(2,"lower"),DesertOutfitLayers::tailLower);event.registerLayerDefinition(layer(2,"reference"),DesertOutfitLayers::tailReference);
        event.registerLayerDefinition(layer(3,"upper"),DesertOutfitLayers::pupUpper);event.registerLayerDefinition(layer(3,"lower"),DesertOutfitLayers::pupLower);event.registerLayerDefinition(layer(3,"reference"),DesertOutfitLayers::pupReference);
        event.registerLayerDefinition(layer(4,"upper"),DesertOutfitLayers::jerboaUpper);event.registerLayerDefinition(layer(4,"lower"),DesertOutfitLayers::jerboaLower);event.registerLayerDefinition(layer(4,"reference"),DesertOutfitLayers::jerboaReference);
        event.registerLayerDefinition(layer(5,"upper"),DesertOutfitLayers::synergyFemaleUpper);event.registerLayerDefinition(layer(5,"lower"),DesertOutfitLayers::synergyFemaleLower);
        event.registerLayerDefinition(layer(6,"upper"),DesertOutfitLayers::legacyFemaleUpper);event.registerLayerDefinition(layer(6,"lower"),DesertOutfitLayers::legacyFemaleLower);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{
        AccessoryLayer.registerRenderer(ChangedSynergyItems.DESERT_NOMAD_OUTFIT.get(),DesertOutfitRenderer::new);
    });}
    private static int cut(LivingEntity wearer,EntityModel<?> model){
        var body=HyenaTrailclothItem.body(wearer);
        if(EagleOwlAviatorItem.juvenile(wearer))return 3;
        if(body!=null&&body.getEntityShape().legsShape==ClothingShape.Legs.TAIL)return 2;
        if(body instanceof LatexJerboa)return 4;
        if(HyenaTrailclothItem.synergyFemale(wearer))return 5;
        if(HyenaTrailclothItem.female(wearer))return 6;
        return model instanceof DoubleArmedModel<?>?1:0;
    }
    private static ResourceLocation texture(int cut){String name=CUTS[cut>=5?0:cut];return ResourceLocation.parse("changed_synergy:textures/models/desert_"+name+".png");}
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void render(AccessorySlotContext<T> c,PoseStack pose,RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(!DesertOutfitItem.fits(c.wearer()))return;int cut=cut(c.wearer(),parent.getModel());var ref=reference[cut>=5?0:cut];
        top[cut].getAllParts().forEach(ModelPart::resetPose);bottom[cut].getAllParts().forEach(ModelPart::resetPose);
        top[cut].getChild("Head").getChild("Outfit_Circlet").y-=cut==4?1.0F:1.25F;
        boolean human=humanBody(c.wearer());float padding=(human?.2F:0)+(c.slotType()==ChangedAccessorySlots.FULL_BODY.get()?.12F:0);
        var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(cut)));
        if(parent.getModel() instanceof MilkPuddingModel pudding){var root=pudding.root();var middle=root.getChild("Middle");pose.pushPose();root.translateAndRotate(pose);renderPart(top[cut].getChild("Torso"),ref.getChild("Torso"),middle,pose,buffer,light,null,0,padding);middle.translateAndRotate(pose);renderPart(top[cut].getChild("Head"),ref.getChild("Head"),middle.getChild("Top"),pose,buffer,light,null,0,padding);pose.popPose();return;}
        pose.pushPose();if(parent.getModel() instanceof AdvancedHumanoidModel<?> advanced)advanced.scaleForBody(pose);
        for(String name:new String[]{"Head","Torso","LeftArm","RightArm"})renderPart(top[cut].getChild(name),ref.getChild(name),part(parent.getModel(),name),pose,buffer,light,null,0,padding);
        if(parent.getModel() instanceof DoubleArmedModel<?>)for(String name:new String[]{"LeftArm2","RightArm2"})renderPart(top[1].getChild(name),reference[1].getChild(name),part(parent.getModel(),name),pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture(1))),light,null,0,padding);
        if(cut==2)renderPart(bottom[cut].getChild("Torso"),ref.getChild("Torso"),part(parent.getModel(),"Torso"),pose,buffer,light,null,0,padding);
        else if(HyenaTrailclothItem.hasLowerSection(c.wearer()))for(String name:new String[]{"Torso","LeftLeg","RightLeg"}){
            var base=human&&!name.equals("Torso")?humanReference.getChild(name.equals("LeftLeg")?"left_leg":"right_leg"):ref.getChild(name);
            if(human&&!name.equals("Torso"))bottom[cut].getChild(name).getAllParts().forEach(p->p.xRot=0);
            renderPart(bottom[cut].getChild(name),base,part(parent.getModel(),name),pose,buffer,light,null,name.equals("Torso")?waistOffset(parent.getModel(),ref):0,padding);
        }
        pose.popPose();
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void renderFirstPersonOnArms(AccessorySlotContext<T> c,PoseStack pose,RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,HumanoidArm arm,PartPose armPose,float partial){
        if(!DesertOutfitItem.fits(c.wearer()))return;int cut=cut(c.wearer(),parent.getModel());String name=arm==HumanoidArm.LEFT?"LeftArm":"RightArm";
        renderPart(top[cut].getChild(name),reference[cut>=5?0:cut].getChild(name),part(parent.getModel(),name),pose,buffers.getBuffer(RenderType.entityCutoutNoCull(texture(cut))),light,armPose,0,(humanBody(c.wearer())?.2F:0)+(c.slotType()==ChangedAccessorySlots.FULL_BODY.get()?.12F:0));
    }
}
