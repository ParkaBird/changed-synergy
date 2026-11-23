package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel;
import net.ltxprogrammer.changed.client.renderer.model.MilkPuddingModel;
import net.ltxprogrammer.changed.client.renderer.model.TripleArmedModel;
import net.ltxprogrammer.changed.client.renderer.accessory.AccessoryRenderer;
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
import net.parkabird.changedsynergy.client.model.CaveMinerOutfitLayers;
import net.parkabird.changedsynergy.client.model.LatexSpidtaurModel;
import net.parkabird.changedsynergy.init.ChangedSynergyItems;
import net.parkabird.changedsynergy.item.CaveMinerOutfitItem;
import net.parkabird.changedsynergy.item.EagleOwlAviatorItem;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;

/** Fits the Cave Miner outfit across juveniles, legless, bipedal, female, multi-armed, other-taur, and Spidtaur bodies with emissive light overlays. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CaveMinerOutfitRenderer extends ClothingModelFit implements AccessoryRenderer {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/models/cave_miner_outfit.png");
    private static final ResourceLocation EMISSIVE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/models/cave_miner_outfit_emissive.png");

    private static final String[] LEG_SIDES = {"LeftLeg", "RightLeg"};

    private final ModelPart koboldReference = bake(layer("kobold_ref"));
    private final ModelPart stigerReference = bake(layer("stiger_ref"));
    private final ModelPart humanReference = bake(ModelLayers.PLAYER);
    private final ModelPart bipedal = bake(layer("bipedal"));
    private final ModelPart bipedalFemale = bake(layer("bipedal_female"));
    private final ModelPart bipedalHumanLegs = bake(layer("bipedal_human_legs"));
    private final ModelPart stiger = bake(layer("stiger"));
    private final ModelPart spidtaur = bake(layer("spidtaur"));

    public CaveMinerOutfitRenderer() {}

    private static ModelPart bake(ModelLayerLocation layer) {
        return Minecraft.getInstance().getEntityModels().bakeLayer(layer);
    }

    private static ModelLayerLocation layer(String name) {
        return new ModelLayerLocation(ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "cave_miner/" + name), "main");
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(layer("kobold_ref"), CaveMinerOutfitLayers::koboldReference);
        event.registerLayerDefinition(layer("stiger_ref"), CaveMinerOutfitLayers::stigerReference);
        event.registerLayerDefinition(layer("bipedal"), CaveMinerOutfitLayers::bipedal);
        event.registerLayerDefinition(layer("bipedal_female"), CaveMinerOutfitLayers::bipedalFemale);
        event.registerLayerDefinition(layer("bipedal_human_legs"), CaveMinerOutfitLayers::bipedalHumanLegs);
        event.registerLayerDefinition(layer("stiger"), CaveMinerOutfitLayers::stiger);
        event.registerLayerDefinition(layer("spidtaur"), CaveMinerOutfitLayers::spidtaur);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> AccessoryLayer.registerRenderer(ChangedSynergyItems.CAVE_MINER_OUTFIT.get(), CaveMinerOutfitRenderer::new));
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void render(
            AccessorySlotContext<T> context, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, float swing, float amount, float partial, float age, float yaw, float pitch) {
        LivingEntity wearer = context.wearer();
        if (!CaveMinerOutfitItem.fits(wearer)) return;

        boolean lit = CaveMinerOutfitItem.lit(context.stack());
        boolean young = EagleOwlAviatorItem.juvenile(wearer);
        boolean human = humanBody(wearer);
        float padding = (human ? 0.2F : 0.0F) + (context.slotType() == ChangedAccessorySlots.FULL_BODY.get() ? 0.12F : 0.0F);
        EntityModel<?> model = parent.getModel();

        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        if (model instanceof MilkPuddingModel pudding) {
            ModelPart root = pudding.root();
            ModelPart middle = root.getChild("Middle");
            bipedal.getAllParts().forEach(ModelPart::resetPose);
            setPuddingParts(bipedal);
            pose.pushPose();
            root.translateAndRotate(pose);
            if (!young) renderPart(bipedal.getChild("Torso"), koboldReference.getChild("Torso"), middle, pose, buffer, light, null, 0, padding);
            middle.translateAndRotate(pose);
            renderPart(bipedal.getChild("Head"), koboldReference.getChild("Head"), middle.getChild("Top"), pose, buffer, light, null, 0, padding);
            if (lit) {
                VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE));
                renderPart(bipedal.getChild("Head"), koboldReference.getChild("Head"), middle.getChild("Top"), pose, glow, LightTexture.FULL_BRIGHT, null, 0, padding);
            }
            pose.popPose();
            return;
        }

        if (model instanceof LatexSpidtaurModel spidtaurModel) {
            renderSpidtaur(spidtaurModel, young, lit, pose, buffers, buffer, light);
            return;
        }

        pose.pushPose();
        if (model instanceof AdvancedHumanoidModel<?> advanced) advanced.scaleForBody(pose);

        boolean tripleArm = model instanceof TripleArmedModel<?>;
        boolean doubleArm = model instanceof DoubleArmedModel<?>;
        boolean multiArm = tripleArm || doubleArm;
        boolean otherTaur = CaveMinerOutfitItem.isTaur(wearer);
        boolean female = !multiArm && !otherTaur && (HyenaTrailclothItem.synergyFemale(wearer) || HyenaTrailclothItem.female(wearer));

        ModelPart active = multiArm ? stiger : female ? bipedalFemale : bipedal;
        ModelPart ref = multiArm ? stigerReference : koboldReference;
        active.getAllParts().forEach(ModelPart::resetPose);
        configureTorsoVisibility(active, wearer, otherTaur);

        renderPart(active.getChild("Head"), ref.getChild("Head"), part(model, "Head"), pose, buffer, light, null, 0, padding);
        if (lit) {
            VertexConsumer glow = buffers.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE));
            renderPart(active.getChild("Head"), ref.getChild("Head"), part(model, "Head"), pose, glow, LightTexture.FULL_BRIGHT, null, 0, padding);
        }

        if (!young) {
            renderPart(active.getChild("Torso"), ref.getChild("Torso"), part(model, "Torso"), pose, buffer, light, null, 0, padding);
            if (tripleArm) {
                TripleArmedModel<?> triple = (TripleArmedModel<?>) model;
                renderArmPair(active, ref, "LeftArm3", "RightArm3", part(model, "LeftArm"), part(model, "RightArm"), pose, buffer, light, padding);
                renderArmPair(active, ref, "LeftArm2", "RightArm2", triple.getMiddleArm(HumanoidArm.LEFT), triple.getMiddleArm(HumanoidArm.RIGHT), pose, buffer, light, padding);
                renderArmPair(active, ref, "LeftArm", "RightArm", triple.getOtherArm(HumanoidArm.LEFT), triple.getOtherArm(HumanoidArm.RIGHT), pose, buffer, light, padding);
            } else if (doubleArm) {
                DoubleArmedModel<?> dbl = (DoubleArmedModel<?>) model;
                renderArmPair(active, ref, "LeftArm3", "RightArm3", part(model, "LeftArm"), part(model, "RightArm"), pose, buffer, light, padding);
                renderArmPair(active, ref, "LeftArm2", "RightArm2", dbl.getOtherArm(HumanoidArm.LEFT), dbl.getOtherArm(HumanoidArm.RIGHT), pose, buffer, light, padding);
            } else {
                renderPart(active.getChild("LeftArm"), ref.getChild("LeftArm"), part(model, "LeftArm"), pose, buffer, light, null, 0, padding);
                renderPart(active.getChild("RightArm"), ref.getChild("RightArm"), part(model, "RightArm"), pose, buffer, light, null, 0, padding);
            }

            if (!otherTaur && HyenaTrailclothItem.hasLowerSection(wearer)) {
                renderLegs(model, active, ref, human, pose, buffer, light, padding);
            }
        }
        pose.popPose();
    }

    @Override
    public <T extends LivingEntity, M extends EntityModel<T>> void renderFirstPersonOnArms(
            AccessorySlotContext<T> context, PoseStack pose, RenderLayerParent<T, M> parent,
            MultiBufferSource buffers, int light, HumanoidArm arm, PartPose armPose, float partial) {
        LivingEntity wearer = context.wearer();
        if (!CaveMinerOutfitItem.fits(wearer) || EagleOwlAviatorItem.juvenile(wearer)) return;

        EntityModel<?> model = parent.getModel();
        float padding = (humanBody(wearer) ? 0.2F : 0.0F) + (context.slotType() == ChangedAccessorySlots.FULL_BODY.get() ? 0.12F : 0.0F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));

        if (model instanceof LatexSpidtaurModel spidtaurModel) {
            spidtaur.getAllParts().forEach(ModelPart::resetPose);
            String armName = arm == HumanoidArm.LEFT ? "LeftArm" : "RightArm";
            String forearmName = arm == HumanoidArm.LEFT ? "LeftForearm" : "RightForearm";
            ModelPart dstArm = spidtaur.getChild(armName);
            ModelPart srcArm = spidtaurModel.getArm(arm);
            if (srcArm != null && srcArm.hasChild(forearmName)) {
                dstArm.getChild(forearmName).loadPose(srcArm.getChild(forearmName).storePose());
            }
            dstArm.loadPose(armPose);
            dstArm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            return;
        }

        if (model instanceof TripleArmedModel<?> || model instanceof DoubleArmedModel<?>) {
            stiger.getAllParts().forEach(ModelPart::resetPose);
            String stigerArmName = arm == HumanoidArm.LEFT ? "LeftArm3" : "RightArm3";
            String modelArmName = arm == HumanoidArm.LEFT ? "LeftArm" : "RightArm";
            renderPart(stiger.getChild(stigerArmName), stigerReference.getChild(stigerArmName), part(model, modelArmName), pose, buffer, light, armPose, 0, padding);
            return;
        }

        bipedal.getAllParts().forEach(ModelPart::resetPose);
        String armName = arm == HumanoidArm.LEFT ? "LeftArm" : "RightArm";
        renderPart(bipedal.getChild(armName), koboldReference.getChild(armName), part(model, armName), pose, buffer, light, armPose, 0, padding);
    }

    private void renderSpidtaur(
            LatexSpidtaurModel model, boolean young, boolean lit,
            PoseStack pose, MultiBufferSource buffers, VertexConsumer buffer, int light) {
        spidtaur.getAllParts().forEach(ModelPart::resetPose);

        pose.pushPose();
        model.scaleForBody(pose);
        ModelPart head = spidtaur.getChild("Head");
        head.loadPose(model.getHead().storePose());
        head.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
        if (lit) {
            head.render(pose, buffers.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        }

        if (!young) {
            ModelPart torso = spidtaur.getChild("Torso");
            torso.loadPose(model.getTorso().storePose());
            torso.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);

            renderSpidtaurArm(model.getArm(HumanoidArm.LEFT), spidtaur.getChild("LeftArm"), "LeftForearm", pose, buffer, light);
            renderSpidtaurArm(model.getArm(HumanoidArm.RIGHT), spidtaur.getChild("RightArm"), "RightForearm", pose, buffer, light);
            renderSpidtaurArm(model.getOtherArm(HumanoidArm.LEFT), spidtaur.getChild("LeftArm2"), "LeftForearm2", pose, buffer, light);
            renderSpidtaurArm(model.getOtherArm(HumanoidArm.RIGHT), spidtaur.getChild("RightArm2"), "RightForearm2", pose, buffer, light);
        }
        pose.popPose();

        if (!young) {
            pose.pushPose();
            model.swapResetPoseStack(pose);
            ModelPart srcLower = model.getLowerTorso();
            ModelPart dstLower = spidtaur.getChild("LowerTorso");
            dstLower.loadPose(srcLower.storePose());
            copyChildPose(srcLower, dstLower, "Abdomen");
            copyChildLegPose(model.getLeg(HumanoidArm.LEFT), dstLower.getChild("LeftLeg"), "LeftLowerLeg");
            copyChildLegPose(model.getLeg(HumanoidArm.RIGHT), dstLower.getChild("RightLeg"), "RightLowerLeg");
            dstLower.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
            if (lit) {
                // Only Abdomen carries emissive pixels in cave_miner_outfit_emissive.png.
                dstLower.render(pose, buffers.getBuffer(RenderType.eyes(EMISSIVE_TEXTURE)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
            }
            model.swapResetPoseStack(pose);
            pose.popPose();
        }
    }

    private static void renderSpidtaurArm(
            ModelPart srcArm, ModelPart dstArm, String forearmName,
            PoseStack pose, VertexConsumer buffer, int light) {
        if (srcArm == null) return;
        dstArm.loadPose(srcArm.storePose());
        copyChildPose(srcArm, dstArm, forearmName);
        dstArm.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
    }

    private static void copyChildLegPose(ModelPart srcLeg, ModelPart dstLeg, String lowerName) {
        if (srcLeg == null) return;
        dstLeg.loadPose(srcLeg.storePose());
        copyChildPose(srcLeg, dstLeg, lowerName);
    }

    private static void copyChildPose(ModelPart srcParent, ModelPart dstParent, String childName) {
        if (srcParent.hasChild(childName) && dstParent.hasChild(childName)) {
            dstParent.getChild(childName).loadPose(srcParent.getChild(childName).storePose());
        }
    }

    private void renderArmPair(
            ModelPart clothing, ModelPart ref, String leftName, String rightName,
            ModelPart targetLeft, ModelPart targetRight,
            PoseStack pose, VertexConsumer buffer, int light, float padding) {
        if (targetLeft != null) renderPart(clothing.getChild(leftName), ref.getChild(leftName), targetLeft, pose, buffer, light, null, 0, padding);
        if (targetRight != null) renderPart(clothing.getChild(rightName), ref.getChild(rightName), targetRight, pose, buffer, light, null, 0, padding);
    }

    private void renderLegs(
            EntityModel<?> model, ModelPart active, ModelPart ref, boolean human,
            PoseStack pose, VertexConsumer buffer, int light, float padding) {
        for (int i = 0; i < LEG_SIDES.length; i++) {
            String legName = LEG_SIDES[i];
            boolean left = i == 0;
            String thighGroup = left ? "Outfit_ThighL" : "Outfit_ThighR";
            String lowerName = left ? "LeftLowerLeg" : "RightLowerLeg";
            String shinGroup = left ? "Outfit_ShinL" : "Outfit_ShinR";
            ModelPart targetLeg = part(model, legName);
            if (targetLeg == null) continue;

            if (human || !targetLeg.hasChild(lowerName)) {
                bipedalHumanLegs.getAllParts().forEach(ModelPart::resetPose);
                ModelPart humanRef = humanReference.getChild(left ? "left_leg" : "right_leg");
                renderPart(bipedalHumanLegs.getChild(legName), humanRef, targetLeg, pose, buffer, light, null, 0, padding);
            } else {
                ModelPart activeLeg = active.getChild(legName);
                ModelPart refLeg = ref.getChild(legName);
                renderPart(activeLeg.getChild(thighGroup), refLeg, targetLeg, pose, buffer, light, null, 0, padding);

                ModelPart targetLower = targetLeg.getChild(lowerName);
                if (activeLeg.hasChild(lowerName) && refLeg.hasChild(lowerName)) {
                    ModelPart shinPart = activeLeg.getChild(lowerName).getChild(shinGroup);
                    pose.pushPose();
                    targetLeg.translateAndRotate(pose);
                    renderPart(shinPart, refLeg.getChild(lowerName), targetLower, pose, buffer, light, null, 0, padding);
                    pose.popPose();
                }
            }
        }
    }

    private static void setPuddingParts(ModelPart active) {
        ModelPart torso = active.getChild("Torso");
        if (torso.hasChild("Outfit_HipApron")) torso.getChild("Outfit_HipApron").visible = false;
        if (torso.hasChild("Outfit_Belt")) {
            ModelPart belt = torso.getChild("Outfit_Belt");
            setChildVisible(belt, "Miner_Satchel_r1", false);
            setChildVisible(belt, "Miner_CrystalFlask_r1", false);
            setChildVisible(belt, "Miner_SashTab_r1", false);
        }
    }

    private static void configureTorsoVisibility(ModelPart active, LivingEntity wearer, boolean otherTaur) {
        ModelPart torso = active.getChild("Torso");
        if (otherTaur) {
            if (torso.hasChild("Outfit_Belt")) torso.getChild("Outfit_Belt").visible = false;
            if (torso.hasChild("Outfit_HipApron")) torso.getChild("Outfit_HipApron").visible = false;
            return;
        }
        if (!HyenaTrailclothItem.hasLowerSection(wearer)) {
            if (torso.hasChild("Outfit_HipApron")) torso.getChild("Outfit_HipApron").visible = false;
            if (torso.hasChild("Outfit_Belt")) {
                ModelPart belt = torso.getChild("Outfit_Belt");
                setChildVisible(belt, "Miner_Satchel_r1", false);
                setChildVisible(belt, "Miner_CrystalFlask_r1", false);
                setChildVisible(belt, "Miner_SashTab_r1", false);
            }
        }
    }

    private static void setChildVisible(ModelPart parent, String child, boolean visible) {
        if (parent.hasChild(child)) parent.getChild(child).visible = visible;
    }
}
