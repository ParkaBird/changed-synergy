package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.BurrowShawlLayers;
import net.parkabird.changedsynergy.client.model.FieldCoatLayers;
import net.parkabird.changedsynergy.client.model.LatexGrayFoxModel;
import net.parkabird.changedsynergy.client.model.LatexManulModel;
import net.parkabird.changedsynergy.entity.LatexGrayFox;
import net.parkabird.changedsynergy.entity.LatexGrayFoxFemale;
import net.parkabird.changedsynergy.entity.LatexManul;
import net.parkabird.changedsynergy.entity.LatexManulFemale;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.BurrowShawlItem;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;

/** Fits the Burrow Shawl across Manul, Gray Fox, female, and standard bipedal cuts without blocking casual wear. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class BurrowShawlRenderer extends ClothingModelFit implements AccessoryRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/models/burrow_shawl.png");

    private final ModelPart manulMale = bake(layer("manul_male"));
    private final ModelPart manulFemale = bake(layer("manul_female"));
    private final ModelPart grayFoxMale = bake(layer("gray_fox_male"));
    private final ModelPart grayFoxFemale = bake(layer("gray_fox_female"));
    private final ModelPart female = bake(layer("female"));
    private final ModelPart bipedal = bake(layer("bipedal"));

    private final ModelPart manulMaleReference = bake(LatexManulModel.MALE_LAYER);
    private final ModelPart manulFemaleReference = bake(LatexManulModel.FEMALE_LAYER);
    private final ModelPart grayFoxMaleReference = bake(LatexGrayFoxModel.MALE_LAYER);
    private final ModelPart grayFoxFemaleReference = bake(LatexGrayFoxModel.FEMALE_LAYER);
    private final ModelPart femaleReference = bake(layer("female_ref"));
    private final ModelPart bipedalReference = bake(layer("bipedal_ref"));

    public BurrowShawlRenderer() {}

    private static ModelPart bake(ModelLayerLocation layer) {
        return Minecraft.getInstance().getEntityModels().bakeLayer(layer);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "burrow_shawl/" + name), "main");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(layer("manul_male"), BurrowShawlLayers::manulMale);
        event.registerLayerDefinition(layer("manul_female"), BurrowShawlLayers::manulFemale);
        event.registerLayerDefinition(layer("gray_fox_male"), BurrowShawlLayers::grayFoxMale);
        event.registerLayerDefinition(layer("gray_fox_female"), BurrowShawlLayers::grayFoxFemale);
        event.registerLayerDefinition(layer("female"), BurrowShawlLayers::female);
        event.registerLayerDefinition(layer("bipedal"), BurrowShawlLayers::bipedal);
        event.registerLayerDefinition(layer("female_ref"), FieldCoatLayers::femaleReference);
        event.registerLayerDefinition(layer("bipedal_ref"), FieldCoatLayers::bipedalReference);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AccessoryLayer.registerRenderer(ChangedSynergyItems.BURROW_SHAWL.get(), BurrowShawlRenderer::new));
    }

    private enum Cut { MANUL_MALE, MANUL_FEMALE, GRAY_FOX_MALE, GRAY_FOX_FEMALE, FEMALE, BIPEDAL }

    private static Cut cut(LivingEntity wearer, ItemStack stack) {
        if (HyenaTrailclothItem.isArmorStand(wearer) && stack.hasTag() && stack.getTag().contains(BurrowShawlItem.DISPLAY_CUT_TAG)) {
            return switch (stack.getTag().getString(BurrowShawlItem.DISPLAY_CUT_TAG)) {
                case "manul_male" -> Cut.MANUL_MALE;
                case "manul_female" -> Cut.MANUL_FEMALE;
                case "gray_fox_male" -> Cut.GRAY_FOX_MALE;
                case "gray_fox_female" -> Cut.GRAY_FOX_FEMALE;
                case "female" -> Cut.FEMALE;
                default -> Cut.BIPEDAL;
            };
        }
        var body = HyenaTrailclothItem.body(wearer);
        if (body instanceof LatexManulFemale) return Cut.MANUL_FEMALE;
        if (body instanceof LatexManul) return Cut.MANUL_MALE;
        if (body instanceof LatexGrayFoxFemale) return Cut.GRAY_FOX_FEMALE;
        if (body instanceof LatexGrayFox) return Cut.GRAY_FOX_MALE;
        if (HyenaTrailclothItem.female(wearer) && !HyenaTrailclothItem.synergyFemale(wearer)) return Cut.FEMALE;
        return Cut.BIPEDAL;
    }

    private ModelPart modelFor(Cut cut) {
        return switch (cut) {
            case MANUL_MALE -> manulMale;
            case MANUL_FEMALE -> manulFemale;
            case GRAY_FOX_MALE -> grayFoxMale;
            case GRAY_FOX_FEMALE -> grayFoxFemale;
            case FEMALE -> female;
            case BIPEDAL -> bipedal;
        };
    }

    private ModelPart referenceFor(Cut cut) {
        return switch (cut) {
            case MANUL_MALE -> manulMaleReference;
            case MANUL_FEMALE -> manulFemaleReference;
            case GRAY_FOX_MALE -> grayFoxMaleReference;
            case GRAY_FOX_FEMALE -> grayFoxFemaleReference;
            case FEMALE -> femaleReference;
            case BIPEDAL -> bipedalReference;
        };
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            AccessorySlotContext<T> context, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, float swing, float amount, float partial, float age, float yaw, float pitch) {
        LivingEntity wearer = context.wearer();
        if (!BurrowShawlItem.fits(wearer)) return;

        Cut cut = cut(wearer, context.stack());
        ModelPart active = modelFor(cut);
        ModelPart ref = referenceFor(cut);
        active.getAllParts().forEach(ModelPart::resetPose);

        boolean closed = BurrowShawlItem.closed(context.stack());
        ModelPart torso = active.getChild("Torso");
        ModelPart head = active.getChild("Head");
        torso.getChild("State_Open").visible = !closed;
        head.getChild("State_Closed").visible = closed;

        boolean hasShirt = AccessorySlots.getForEntity(wearer)
                .flatMap(slots -> slots.getItem(ChangedAccessorySlots.BODY.get()))
                .filter(stack -> !stack.isEmpty())
                .isPresent();
        float padding = (humanBody(wearer) ? 0.2F : 0.0F) + (hasShirt ? 0.08F : 0.0F);
        EntityModel<?> model = parent.getModel();

        pose.pushPose();
        if (model instanceof AdvancedHumanoidModel<?> advanced) advanced.scaleForBody(pose);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        renderPart(torso, ref.getChild("Torso"), part(model, "Torso"), pose, buffer, light, null, 0, padding);
        pose.popPose();

        if (closed) {
            ModelPart targetHead = part(model, "Head");
            if (targetHead != null) {
                pose.pushPose();
                if (model instanceof AdvancedHumanoidModel<?> advanced) advanced.scaleForHead(pose);
                renderPart(head, ref.getChild("Head"), targetHead, pose, buffer, light, null, 0, padding);
                pose.popPose();
            }
        }
    }
}
