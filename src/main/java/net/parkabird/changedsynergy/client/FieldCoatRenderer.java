package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel;
import net.ltxprogrammer.changed.client.renderer.model.TripleArmedModel;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.FieldCoatLayers;
import net.parkabird.changedsynergy.client.model.LatexGrizzlyModel;
import net.parkabird.changedsynergy.entity.LatexGrizzly;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.FieldCoatItem;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;

/** Fits the Field Coat across Grizzly, female, and standard bipedal cuts, with upper-only wear for non-bipeds/taurs. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class FieldCoatRenderer extends ClothingModelFit implements AccessoryRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/models/field_coat.png");
    private static final ResourceLocation EMISSIVE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/models/field_coat_emissive.png");

    private final ModelPart bipedal = bake(layer("bipedal"));
    private final ModelPart female = bake(layer("female"));
    private final ModelPart grizzly = bake(layer("grizzly"));
    private final ModelPart bipedalReference = bake(layer("bipedal_ref"));
    private final ModelPart femaleReference = bake(layer("female_ref"));
    private final ModelPart grizzlyReference = bake(LatexGrizzlyModel.LAYER_LOCATION);
    private final ModelPart humanReference = bake(ModelLayers.PLAYER);

    public FieldCoatRenderer() {}

    private static ModelPart bake(ModelLayerLocation layer) {
        return Minecraft.getInstance().getEntityModels().bakeLayer(layer);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "field_coat/" + name), "main");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(layer("bipedal"), FieldCoatLayers::bipedal);
        event.registerLayerDefinition(layer("female"), FieldCoatLayers::female);
        event.registerLayerDefinition(layer("grizzly"), FieldCoatLayers::grizzly);
        event.registerLayerDefinition(layer("bipedal_ref"), FieldCoatLayers::bipedalReference);
        event.registerLayerDefinition(layer("female_ref"), FieldCoatLayers::femaleReference);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AccessoryLayer.registerRenderer(ChangedSynergyItems.FIELD_COAT.get(), FieldCoatRenderer::new));
    }

    private enum Cut { BIPEDAL, FEMALE, GRIZZLY }

    private static Cut cut(LivingEntity wearer, net.minecraft.world.item.ItemStack stack) {
        if (HyenaTrailclothItem.body(wearer) instanceof LatexGrizzly
                || (HyenaTrailclothItem.isArmorStand(wearer) && FieldCoatItem.isGrizzlyDisplay(stack))) {
            return Cut.GRIZZLY;
        }
        if (HyenaTrailclothItem.female(wearer) && !HyenaTrailclothItem.synergyFemale(wearer)) return Cut.FEMALE;
        return Cut.BIPEDAL;
    }

    private ModelPart modelFor(Cut cut) {
        return switch (cut) {
            case GRIZZLY -> grizzly;
            case FEMALE -> female;
            case BIPEDAL -> bipedal;
        };
    }

    private ModelPart referenceFor(Cut cut) {
        return switch (cut) {
            case GRIZZLY -> grizzlyReference;
            case FEMALE -> femaleReference;
            case BIPEDAL -> bipedalReference;
        };
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            AccessorySlotContext<T> context, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, float swing, float amount, float partial, float age, float yaw, float pitch) {
        LivingEntity wearer = context.wearer();
        if (!FieldCoatItem.fits(wearer)) return;

        Cut cut = cut(wearer, context.stack());
        ModelPart active = modelFor(cut);
        ModelPart ref = referenceFor(cut);
        active.getAllParts().forEach(ModelPart::resetPose);

        boolean closed = FieldCoatItem.closed(context.stack());
        ModelPart torso = active.getChild("Torso");
        torso.getChild("State_Open").visible = !closed;
        torso.getChild("State_Closed").visible = closed;

        boolean human = humanBody(wearer);
        float padding = (human ? 0.2F : 0.0F) + (context.slotType() == ChangedAccessorySlots.FULL_BODY.get() ? 0.12F : 0.0F);
        EntityModel<?> model = parent.getModel();

        pose.pushPose();
        if (model instanceof AdvancedHumanoidModel<?> advanced) advanced.scaleForBody(pose);

        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        renderPart(torso, ref.getChild("Torso"), part(model, "Torso"), pose, buffer, light, null, 0, padding);
        for (String armName : new String[]{"LeftArm", "RightArm"}) {
            renderPart(active.getChild(armName), ref.getChild(armName), part(model, armName), pose, buffer, light, null, 0, padding);
        }
        if (model instanceof DoubleArmedModel<?> dbl) {
            renderPart(active.getChild("LeftArm"), ref.getChild("LeftArm"), dbl.getOtherArm(HumanoidArm.LEFT), pose, buffer, light, null, 0, padding);
            renderPart(active.getChild("RightArm"), ref.getChild("RightArm"), dbl.getOtherArm(HumanoidArm.RIGHT), pose, buffer, light, null, 0, padding);
        }
        if (model instanceof TripleArmedModel<?> triple) {
            renderPart(active.getChild("LeftArm"), ref.getChild("LeftArm"), triple.getMiddleArm(HumanoidArm.LEFT), pose, buffer, light, null, 0, padding);
            renderPart(active.getChild("RightArm"), ref.getChild("RightArm"), triple.getMiddleArm(HumanoidArm.RIGHT), pose, buffer, light, null, 0, padding);
            renderPart(active.getChild("LeftArm"), ref.getChild("LeftArm"), triple.getOtherArm(HumanoidArm.LEFT), pose, buffer, light, null, 0, padding);
            renderPart(active.getChild("RightArm"), ref.getChild("RightArm"), triple.getOtherArm(HumanoidArm.RIGHT), pose, buffer, light, null, 0, padding);
        }

        if (FieldCoatItem.hasLowerSection(wearer)) {
            for (String legName : new String[]{"LeftLeg", "RightLeg"}) {
                ModelPart legPart = active.getChild(legName);
                if (legPart.hasChild("State_Open")) legPart.getChild("State_Open").visible = !closed;
                if (legPart.hasChild("State_Closed")) legPart.getChild("State_Closed").visible = closed;
                if (human) legPart.getAllParts().forEach(part -> part.xRot = 0);
                ModelPart base = human
                        ? humanReference.getChild(legName.equals("LeftLeg") ? "left_leg" : "right_leg")
                        : ref.getChild(legName);
                renderPart(legPart, base, part(model, legName), pose, buffer, light, null, 0, padding);
            }
        }

        VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE));
        renderPart(torso, ref.getChild("Torso"), part(model, "Torso"), pose, glow, LightTexture.FULL_BRIGHT, null, 0, padding);

        pose.popPose();
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void renderFirstPersonOnArms(
            AccessorySlotContext<T> context, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, HumanoidArm arm, PartPose armPose, float partial) {
        LivingEntity wearer = context.wearer();
        if (!FieldCoatItem.fits(wearer)) return;

        Cut cut = cut(wearer, context.stack());
        ModelPart active = modelFor(cut);
        ModelPart ref = referenceFor(cut);
        active.getAllParts().forEach(ModelPart::resetPose);

        String armName = arm == HumanoidArm.LEFT ? "LeftArm" : "RightArm";
        float padding = (humanBody(wearer) ? 0.2F : 0.0F) + (context.slotType() == ChangedAccessorySlots.FULL_BODY.get() ? 0.12F : 0.0F);
        renderPart(active.getChild(armName), ref.getChild(armName), part(parent.getModel(), armName),
                pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)), light, armPose, 0, padding);
    }
}
