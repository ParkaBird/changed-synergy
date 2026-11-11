package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Map;
import java.util.ArrayList;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.HyenaTrailclothLayers;
import net.parkabird.changedsynergy.client.model.LatexSpottedHyenaModel;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/** Bind clothing directly to the wearer's final pose, including social holds and extra body scaling. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HyenaTrailclothRenderer extends ClothingModelFit implements AccessoryRenderer {
    public static final ModelLayerLocation MALE_UPPER = layer("male_upper"), MALE_LOWER = layer("male_lower"),
            FEMALE_UPPER = layer("female_upper"), FEMALE_LOWER = layer("female_lower");
    public static final ModelLayerLocation SYNERGY_UPPER = layer("synergy_upper"), SYNERGY_LOWER = layer("synergy_lower"),
            HYENA_UPPER = layer("hyena_upper"), HYENA_LOWER = layer("hyena_lower");
    private final ModelPart synergyUpper, synergyLower, hyenaUpper, hyenaLower;
    private final ModelPart maleUpper, maleLower, femaleUpper, femaleLower, maleBody, femaleBody, humanBody;

    public HyenaTrailclothRenderer() {
        var models = Minecraft.getInstance().getEntityModels();
        maleUpper = models.bakeLayer(MALE_UPPER); maleLower = models.bakeLayer(MALE_LOWER);
        femaleUpper = models.bakeLayer(FEMALE_UPPER); femaleLower = models.bakeLayer(FEMALE_LOWER);
        synergyUpper = models.bakeLayer(SYNERGY_UPPER); synergyLower = models.bakeLayer(SYNERGY_LOWER);
        hyenaUpper = models.bakeLayer(HYENA_UPPER); hyenaLower = models.bakeLayer(HYENA_LOWER);
        maleBody = models.bakeLayer(LatexSpottedHyenaModel.MALE_LAYER);
        femaleBody = models.bakeLayer(LatexSpottedHyenaModel.FEMALE_LAYER);
        humanBody = models.bakeLayer(ModelLayers.PLAYER);
    }

    private static ModelLayerLocation layer(String section) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath("changed_synergy", "hyena_trailcloth/" + section), "main");
    }

    @SubscribeEvent public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SYNERGY_UPPER, net.parkabird.changedsynergy.client.model.SynergyFemaleOutfitLayers::trailclothUpper);
        event.registerLayerDefinition(SYNERGY_LOWER, net.parkabird.changedsynergy.client.model.SynergyFemaleOutfitLayers::trailclothLower);
        event.registerLayerDefinition(HYENA_UPPER, net.parkabird.changedsynergy.client.model.SynergyFemaleOutfitLayers::hyenaTrailclothUpper);
        event.registerLayerDefinition(HYENA_LOWER, net.parkabird.changedsynergy.client.model.SynergyFemaleOutfitLayers::hyenaTrailclothLower);
        event.registerLayerDefinition(MALE_UPPER, HyenaTrailclothLayers::maleUpper);
        event.registerLayerDefinition(MALE_LOWER, HyenaTrailclothLayers::maleLower);
        event.registerLayerDefinition(FEMALE_UPPER, HyenaTrailclothLayers::femaleUpper);
        event.registerLayerDefinition(FEMALE_LOWER, HyenaTrailclothLayers::femaleLower);
    }

    @SubscribeEvent public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AccessoryLayer.registerRenderer(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get(), HyenaTrailclothRenderer::new));
    }

    private ModelPart upper(LivingEntity wearer) {
        if (HyenaTrailclothItem.synergyFemale(wearer)) return HyenaTrailclothItem.body(wearer) instanceof net.parkabird.changedsynergy.entity.LatexSpottedHyena ? hyenaUpper : synergyUpper;
        return HyenaTrailclothItem.female(wearer) ? femaleUpper : maleUpper;
    }
    private ModelPart lower(LivingEntity wearer) {
        if (HyenaTrailclothItem.synergyFemale(wearer)) return HyenaTrailclothItem.body(wearer) instanceof net.parkabird.changedsynergy.entity.LatexSpottedHyena ? hyenaLower : synergyLower;
        return HyenaTrailclothItem.female(wearer) ? femaleLower : maleLower;
    }

    @Override public <T extends LivingEntity, M extends EntityModel<T>> void render(AccessorySlotContext<T> context,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (!HyenaTrailclothItem.fits(context.wearer())) return;
        boolean female = HyenaTrailclothItem.female(context.wearer());
        var upper = upper(context.wearer()); var lower = lower(context.wearer());
        var reference = female ? femaleBody : maleBody;
        upper.getAllParts().forEach(ModelPart::resetPose); lower.getAllParts().forEach(ModelPart::resetPose);
        boolean human = humanBody(context.wearer());
        float outer=context.slotType()==net.ltxprogrammer.changed.init.ChangedAccessorySlots.FULL_BODY.get()?.12F:0;
        if (human) for (var side : new String[]{"Left", "Right"})
            lower.getChild(side + "Leg").getChild("Trailcloth_" + side + "SplitWrap_r1").xRot = 0;
        var item = ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get();
        var buffer = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.entityCutoutNoCull(item.getTexture(context.stack(), context.wearer())), false, context.stack().hasFoil());
        pose.pushPose();
        if (parent.getModel() instanceof AdvancedHumanoidModel<?> advanced) advanced.scaleForBody(pose);
        for (var name : new String[]{"Torso", "LeftArm", "RightArm"})
            renderPart(upper.getChild(name), reference.getChild(name), part(parent.getModel(), name), pose, buffer, light, null, 0, (human ? .2F : 0)+outer);
        if (HyenaTrailclothItem.hasLowerSection(context.wearer())) for (var name : new String[]{"Torso", "LeftLeg", "RightLeg"})
            renderPart(lower.getChild(name), human && !name.equals("Torso") ? humanBody.getChild(name.equals("LeftLeg") ? "left_leg" : "right_leg") : reference.getChild(name), part(parent.getModel(), name), pose, buffer, light, null,
                    name.equals("Torso") ? waistOffset(parent.getModel(), reference) : 0, (human ? .2F : 0)+outer);
        pose.popPose();
    }

    @Override public <T extends LivingEntity, M extends EntityModel<T>> void renderFirstPersonOnArms(AccessorySlotContext<T> context,
            PoseStack pose, RenderLayerParent<T, M> parent, MultiBufferSource buffers, int light, HumanoidArm arm, PartPose armPose, float partial) {
        if (!HyenaTrailclothItem.fits(context.wearer())) return;
        boolean female = HyenaTrailclothItem.female(context.wearer());
        var upper = upper(context.wearer()); var reference = female ? femaleBody : maleBody;
        String name = arm == HumanoidArm.LEFT ? "LeftArm" : "RightArm";
        var item = ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get();
        var buffer = ItemRenderer.getArmorFoilBuffer(buffers, RenderType.entityCutoutNoCull(item.getTexture(context.stack(), context.wearer())), false, context.stack().hasFoil());
        // Human skin jackets, sleeves and trousers have an outer layer beyond the base limb.
        renderPart(upper.getChild(name), reference.getChild(name), part(parent.getModel(), name), pose, buffer, light, armPose, 0,
                (humanBody(context.wearer()) ? .2F : 0)+(context.slotType()==net.ltxprogrammer.changed.init.ChangedAccessorySlots.FULL_BODY.get()?.12F:0));
    }
}
