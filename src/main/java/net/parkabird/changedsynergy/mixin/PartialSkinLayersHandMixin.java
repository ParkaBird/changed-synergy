package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.FormRenderHandler;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.PartialSkinLayersCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ensures first-person arms of human/partial forms refresh and render 3D skin-layer sleeve meshes cleanly. */
@Mixin(value = FormRenderHandler.class, remap = false)
public abstract class PartialSkinLayersHandMixin {
    @Inject(
            method = "renderHand(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/model/geom/PartPose;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V",
            at = @At("HEAD"),
            remap = false,
            require = 1
    )
    private static void changedSynergy$syncFirstPersonSkinLayers(
            LivingEntity living,
            HumanoidArm arm,
            PartPose pose,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            float partialTick,
            boolean renderLayers,
            CallbackInfo ci) {
        if (!(living instanceof ChangedEntity entity)) {
            return;
        }
        var humanoidRenderer = changedSynergy$resolveHumanoidRenderer(entity);
        if (humanoidRenderer == null) {
            return;
        }
        var model = humanoidRenderer.getModel(entity);
        if (!PartialSkinLayersCompat.isSupportedModel(model)) {
            return;
        }
        AbstractClientPlayer player = entity.getUnderlyingPlayer() instanceof AbstractClientPlayer clientPlayer
                ? clientPlayer
                : null;
        PartialSkinLayersCompat.prepareFirstPersonArm(model, player, arm);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Inject(
            method = "renderHand(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/model/geom/PartPose;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/ltxprogrammer/changed/client/FormRenderHandler;renderModelPartWithTexture(Lnet/minecraft/client/model/geom/ModelPart;Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IF)V",
                    shift = At.Shift.AFTER,
                    remap = false
            ),
            remap = false,
            require = 1
    )
    private static void changedSynergy$renderFirstPersonSkinLayers(
            LivingEntity living,
            HumanoidArm arm,
            PartPose pose,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            float partialTick,
            boolean renderLayers,
            CallbackInfo ci) {
        if (!(living instanceof ChangedEntity entity)) {
            return;
        }
        var humanoidRenderer = changedSynergy$resolveHumanoidRenderer(entity);
        if (humanoidRenderer == null) {
            return;
        }
        var model = humanoidRenderer.getModel(entity);
        if (!PartialSkinLayersCompat.isSupportedModel(model)) {
            return;
        }
        PartialSkinLayersCompat.finishFirstPersonArm(
                model,
                ((EntityRenderer) humanoidRenderer).getTextureLocation(entity),
                arm,
                poseStack,
                bufferSource,
                packedLight);
    }

    @Inject(
            method = "renderHand(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/model/geom/PartPose;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V",
            at = @At("RETURN"),
            remap = false,
            require = 1
    )
    private static void changedSynergy$restoreFirstPersonSkinLayers(
            LivingEntity living,
            HumanoidArm arm,
            PartPose pose,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            float partialTick,
            boolean renderLayers,
            CallbackInfo ci) {
        if (!(living instanceof ChangedEntity entity)) {
            return;
        }
        var humanoidRenderer = changedSynergy$resolveHumanoidRenderer(entity);
        if (humanoidRenderer != null) {
            PartialSkinLayersCompat.restoreFirstPersonArm(humanoidRenderer.getModel(entity));
        }
    }

    @Unique
    private static AdvancedHumanoidRenderer<?, ?> changedSynergy$resolveHumanoidRenderer(ChangedEntity entity) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        return renderer instanceof AdvancedHumanoidRenderer<?, ?> humanoidRenderer ? humanoidRenderer : null;
    }
}
