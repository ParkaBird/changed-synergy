package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel;
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
import net.parkabird.changedsynergy.client.model.*;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.*;

@Mod.EventBusSubscriber(modid="changed_synergy", bus=Mod.EventBusSubscriber.Bus.MOD, value=Dist.CLIENT)
public final class ForestCanopyRenderer extends ClothingModelFit implements AccessoryRenderer {
    private static final String[] VARIANTS = {"four_arm", "bipedal", "female", "synergy_female"};
    private final ModelPart[] upper = new ModelPart[4], lower = new ModelPart[4];
    private final ModelPart reference, femaleReference, humanReference;

    public ForestCanopyRenderer() {
        var models = Minecraft.getInstance().getEntityModels();
        for (int i=0;i<4;i++) { upper[i]=models.bakeLayer(layer(i,"upper")); lower[i]=models.bakeLayer(layer(i,"lower")); }
        reference=models.bakeLayer(LatexSilkMothModel.LAYER_LOCATION);
        femaleReference=models.bakeLayer(layer(2,"reference"));
        humanReference=models.bakeLayer(ModelLayers.PLAYER);
    }
    private static ModelLayerLocation layer(int variant, String section) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("changed_synergy", "forest_canopy/"+VARIANTS[variant]+"/"+section),"main");
    }
    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(layer(0,"upper"),ForestCanopyLayers::fourArmUpper);
        event.registerLayerDefinition(layer(0,"lower"),ForestCanopyLayers::fourArmLower);
        event.registerLayerDefinition(layer(1,"upper"),ForestCanopyLayers::bipedalUpper);
        event.registerLayerDefinition(layer(1,"lower"),ForestCanopyLayers::bipedalLower);
        event.registerLayerDefinition(layer(3,"upper"),ForestCanopyLayers::synergyFemaleUpper);
        event.registerLayerDefinition(layer(3,"lower"),ForestCanopyLayers::synergyFemaleLower);
        event.registerLayerDefinition(layer(2,"upper"),ForestCanopyLayers::femaleUpper);
        event.registerLayerDefinition(layer(2,"lower"),ForestCanopyLayers::femaleLower);
        event.registerLayerDefinition(layer(2,"reference"),ForestCanopyLayers::femaleReference);
    }
    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(()->AccessoryLayer.registerRenderer(ChangedSynergyItems.FOREST_CANOPY_OUTFIT.get(),ForestCanopyRenderer::new));
    }
    private static int variant(LivingEntity wearer, EntityModel<?> model) {
        return HyenaTrailclothItem.synergyFemale(wearer)?3:HyenaTrailclothItem.female(wearer)?2:model instanceof DoubleArmedModel<?>?0:1;
    }
    private static ResourceLocation texture(int variant) {
        return ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/models/forest_canopy_"
                +(variant==0?"four_arm":variant==2?"female":"outfit")+".png");
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void render(AccessorySlotContext<T> context,PoseStack pose,
            RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,float swing,float amount,float partial,float age,float yaw,float pitch) {
        if(!ForestCanopyItem.fits(context.wearer()))return;
        int variant=variant(context.wearer(),parent.getModel());var top=upper[variant];var bottom=lower[variant];
        var fitReference=variant==2?femaleReference:reference;
        top.getAllParts().forEach(ModelPart::resetPose);bottom.getAllParts().forEach(ModelPart::resetPose);
        boolean human=humanBody(context.wearer());float padding=(human?.2F:0)+(context.slotType()==ChangedAccessorySlots.FULL_BODY.get()?.12F:0);
        var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(variant)));
        if(parent.getModel() instanceof net.ltxprogrammer.changed.client.renderer.model.MilkPuddingModel pudding){
            var root=pudding.root();var middle=root.getChild("Middle");pose.pushPose();root.translateAndRotate(pose);
            if(!EagleOwlAviatorItem.juvenile(context.wearer()))renderPart(top.getChild("Torso"),fitReference.getChild("Torso"),middle,pose,buffer,light,null,0,padding);
            middle.translateAndRotate(pose);renderPart(top.getChild("Head"),fitReference.getChild("Head"),middle.getChild("Top"),pose,buffer,light,null,0,padding);
            pose.popPose();return;
        }
        pose.pushPose();
        if(parent.getModel() instanceof AdvancedHumanoidModel<?> advanced)advanced.scaleForBody(pose);
        renderPart(top.getChild("Head"),fitReference.getChild("Head"),part(parent.getModel(),"Head"),pose,buffer,light,null,0,padding);
        if(!EagleOwlAviatorItem.juvenile(context.wearer())) {
            for(var name:new String[]{"Torso","LeftArm","RightArm"})renderPart(top.getChild(name),fitReference.getChild(name),part(parent.getModel(),name),pose,buffer,light,null,0,padding);
            if(parent.getModel() instanceof DoubleArmedModel<?>) {
                var extraArms=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(0)));
                for(var name:new String[]{"LeftArm2","RightArm2"})
                    renderPart(upper[0].getChild(name),reference.getChild(name),part(parent.getModel(),name),pose,extraArms,light,null,0,padding);
                buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(variant)));
            }
            if(HyenaTrailclothItem.hasLowerSection(context.wearer()))for(var name:new String[]{"Torso","LeftLeg","RightLeg"}) {
                if(human&&!name.equals("Torso"))bottom.getChild(name).getAllParts().forEach(part->part.xRot=0);
                var base=human&&!name.equals("Torso")?humanReference.getChild(name.equals("LeftLeg")?"left_leg":"right_leg"):fitReference.getChild(name);
                renderPart(bottom.getChild(name),base,part(parent.getModel(),name),pose,buffer,light,null,name.equals("Torso")?waistOffset(parent.getModel(),fitReference):0,padding);
            }
        }
        pose.popPose();
    }
    @Override public <T extends LivingEntity,M extends EntityModel<T>> void renderFirstPersonOnArms(AccessorySlotContext<T> context,PoseStack pose,
            RenderLayerParent<T,M> parent,MultiBufferSource buffers,int light,HumanoidArm arm,PartPose armPose,float partial) {
        if(!ForestCanopyItem.fits(context.wearer())||EagleOwlAviatorItem.juvenile(context.wearer()))return;
        String name=arm==HumanoidArm.LEFT?"LeftArm":"RightArm";
        int variant=variant(context.wearer(),parent.getModel());
        var buffer=buffers.getBuffer(RenderType.entityCutoutNoCull(texture(variant)));
        renderPart(upper[variant].getChild(name),(variant==2?femaleReference:reference).getChild(name),part(parent.getModel(),name),pose,buffer,light,armPose,0,
                (humanBody(context.wearer())?.2F:0)+(context.slotType()==ChangedAccessorySlots.FULL_BODY.get()?.12F:0));
    }
}
