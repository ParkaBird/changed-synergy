package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
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
import net.parkabird.changedsynergy.client.model.EagleOwlModelLayers;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.*;

@Mod.EventBusSubscriber(modid="changed_synergy",bus=Mod.EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class EagleOwlAviatorRenderer extends ClothingModelFit implements AccessoryRenderer {
    public static final ModelLayerLocation LAYER=new ModelLayerLocation(ResourceLocation.parse("changed_synergy:eagle_owl_aviator"),"main");
    public static final ModelLayerLocation FEMALE_LAYER=new ModelLayerLocation(ResourceLocation.parse("changed_synergy:eagle_owl_aviator_synergy_female"),"main");
    public static final ModelLayerLocation LEGACY_FEMALE_LAYER=new ModelLayerLocation(ResourceLocation.parse("changed_synergy:eagle_owl_aviator_legacy_female"),"main");
    private final ModelPart outfit,femaleOutfit,legacyFemaleOutfit,reference,humanReference;
    public EagleOwlAviatorRenderer(){var models=Minecraft.getInstance().getEntityModels();outfit=models.bakeLayer(LAYER);femaleOutfit=models.bakeLayer(FEMALE_LAYER);legacyFemaleOutfit=models.bakeLayer(LEGACY_FEMALE_LAYER);reference=models.bakeLayer(net.parkabird.changedsynergy.client.model.LatexEagleOwlModel.LAYER);humanReference=models.bakeLayer(ModelLayers.PLAYER);}
    @SubscribeEvent public static void layer(EntityRenderersEvent.RegisterLayerDefinitions e){e.registerLayerDefinition(LAYER,EagleOwlModelLayers::outfit);e.registerLayerDefinition(FEMALE_LAYER,net.parkabird.changedsynergy.client.model.SynergyFemaleOutfitLayers::aviator);e.registerLayerDefinition(LEGACY_FEMALE_LAYER,net.parkabird.changedsynergy.client.model.SynergyFemaleOutfitLayers::legacyAviator);}
    @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->AccessoryLayer.registerRenderer(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get(),EagleOwlAviatorRenderer::new));}
    @SubscribeEvent public static void leglessLayer(EntityRenderersEvent.AddLayers e){
        var renderer=e.getRenderer(net.ltxprogrammer.changed.init.ChangedEntities.MILK_PUDDING.get());
        if(renderer instanceof net.ltxprogrammer.changed.client.renderer.MilkPuddingRenderer pudding) pudding.addLayer(new AccessoryLayer<>(pudding));
    }
    private ModelPart outfit(LivingEntity wearer) {
        return HyenaTrailclothItem.synergyFemale(wearer)?femaleOutfit:HyenaTrailclothItem.female(wearer)?legacyFemaleOutfit:outfit;
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void render(AccessorySlotContext<T> c,PoseStack pose,RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,float swing,float amount,float partial,float age,float yaw,float pitch){
        if(!EagleOwlAviatorItem.fits(c.wearer()))return;
        var outfit=outfit(c.wearer());
        outfit.getAllParts().forEach(ModelPart::resetPose);
        var head=outfit.getChild("Head");boolean lowered=EagleOwlAviatorItem.lowered(c.stack());
        head.getChild("Outfit_Goggles").visible=!lowered;head.getChild("Outfit_Goggles_DownState").visible=lowered;
        var torso=outfit.getChild("Torso");torso.getChild("Outfit_BreechesWaist").visible=HyenaTrailclothItem.hasLowerSection(c.wearer());
        var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get().getTexture(c.stack(),c.wearer())));
        if(parent.getModel() instanceof net.parkabird.changedsynergy.client.model.LatexEagleOwlModel model){
            // The authored owl cut already shares these bones; bounding-box fitting distorts folded thighs.
            for(String name:new String[]{"Head","Torso","LeftArm","RightArm","LeftLeg","RightLeg"})
                outfit.getChild(name).copyFrom(part(model,name));
            outfit.render(pose,buffer,light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);
            return;
        }
        boolean human=humanBody(c.wearer());
        float padding=(human?.2F:0)+(c.slotType()==ChangedAccessorySlots.FULL_BODY.get()?.12F:0);
        if(human){
            torso.getChild("Outfit_BreechesWaist").y+=waistOffset(parent.getModel(),reference);
            for(var side:new String[]{"Left","Right"})outfit.getChild(side+"Leg").getChild("Outfit_Breeches"+side.charAt(0)).getAllParts().forEach(part->part.xRot=0);
        }
        if(parent.getModel() instanceof net.ltxprogrammer.changed.client.renderer.model.MilkPuddingModel pudding){
            var root=pudding.root();var middle=root.getChild("Middle");
            pose.pushPose();root.translateAndRotate(pose);
            renderPart(torso,reference.getChild("Torso"),middle,pose,buffer,light,null,0,padding);
            middle.translateAndRotate(pose);
            renderPart(head,reference.getChild("Head"),middle.getChild("Top"),pose,buffer,light,null,0,padding);
            pose.popPose();return;
        }
        pose.pushPose();
        if(parent.getModel() instanceof net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel<?> advanced)advanced.scaleForBody(pose);
        renderPart(head,reference.getChild("Head"),part(parent.getModel(),"Head"),pose,buffer,light,null,0,padding);
        if(!EagleOwlAviatorItem.juvenile(c.wearer())){
            for(var name:new String[]{"Torso","LeftArm","RightArm"})renderPart(outfit.getChild(name),reference.getChild(name),part(parent.getModel(),name),pose,buffer,light,null,0,padding);
            if(parent.getModel() instanceof net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel<?>)for(var name:new String[]{"LeftArm","RightArm"})
                renderPart(outfit.getChild(name),reference.getChild(name),part(parent.getModel(),name+"2"),pose,buffer,light,null,0,padding);
            if(HyenaTrailclothItem.hasLowerSection(c.wearer()))for(var name:new String[]{"LeftLeg","RightLeg"}){
                var base=human?humanReference.getChild(name.equals("LeftLeg")?"left_leg":"right_leg"):reference.getChild(name);
                renderPart(outfit.getChild(name),base,part(parent.getModel(),name),pose,buffer,light,null,0,padding);
            }
        }
        pose.popPose();
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void renderFirstPersonOnArms(AccessorySlotContext<T> c,PoseStack pose,RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,HumanoidArm arm,PartPose armPose,float partial){
        if(!EagleOwlAviatorItem.fits(c.wearer())||EagleOwlAviatorItem.juvenile(c.wearer()))return;String name=arm==HumanoidArm.LEFT?"LeftArm":"RightArm";
        var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT.get().getTexture(c.stack(),c.wearer())));
        var outfit=outfit(c.wearer());
        renderPart(outfit.getChild(name),reference.getChild(name),part(parent.getModel(),name),pose,buffer,light,armPose,0,(humanBody(c.wearer())?.2F:0)+(c.slotType()==ChangedAccessorySlots.FULL_BODY.get()?.12F:0));
    }
}
