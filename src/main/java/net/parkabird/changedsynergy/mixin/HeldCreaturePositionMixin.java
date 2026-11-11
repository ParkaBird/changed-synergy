package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.LivingEntityRendererExtender;
import net.ltxprogrammer.changed.client.renderer.layers.LatexHeldEntityLayer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native held-entity offset assumes a player-sized victim. */
@Mixin(value = LatexHeldEntityLayer.class, remap = false)
public abstract class HeldCreaturePositionMixin {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/ltxprogrammer/changed/client/LivingEntityRendererExtender;directRender(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"), remap = false)
    private void changedSynergy$prepareHeldModel(LivingEntityRendererExtender direct,
            LivingEntity held, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light,
            PoseStack outerPose, MultiBufferSource outerBuffers, int outerLight, ChangedEntity holder,
            float walk, float speed, float partialTicks, float age, float headYaw, float headPitch) {
        var dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        var renderer = dispatcher.getRenderer(held);
        if (!(renderer instanceof LivingEntityRenderer living)) {
            direct.directRender(held, yaw, partial, pose, buffers, light);
            return;
        }
        var model = living.getModel();
        // Changed's direct renderer skips preparation and otherwise reuses the previous creature's animator state.
        model.prepareMobModel(held, 0, 0, partial);
        try {
            direct.directRender(held, yaw, partial, pose, buffers, light);
        } finally {
            if (dispatcher.getRenderer(holder) instanceof LivingEntityRenderer parent && parent.getModel() == model) {
                // Same-species nested rendering shares a model; later layers still belong to the holder.
                model.prepareMobModel(holder, walk, speed, partialTicks);
                model.setupAnim(holder, walk, speed, age, headYaw, headPitch);
            }
        }
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/ltxprogrammer/changed/client/LivingEntityRendererExtender;directRender(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"),
            require = 0, remap = false)
    private void changedSynergy$liftSmallHeldCreature(
            PoseStack pose, MultiBufferSource buffers, int light,
            ChangedEntity holder, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float headYaw,
            float headPitch, CallbackInfo callback) {
        var ability = BondedSuitService.ability(holder);
        LivingEntity held = ability == null ? null : ability.grabbedEntity;
        if (held == null || ability.suited) return;
        if(holder instanceof net.parkabird.changedsynergy.entity.LatexEagleOwl owl
                && net.parkabird.changedsynergy.client.EagleOwlCarryClient.position(pose,owl,ability,partialTicks))return;
        if (net.parkabird.changedsynergy.ai.PlayerHugService.active(ability)) {
            net.parkabird.changedsynergy.client.PlayerHugPoses.position(pose, holder, ability, partialTicks);
            return;
        }
        double heightDifference = holder.getBbHeight() - held.getBbHeight();
        if (heightDifference > 0.0D) {
            // Living model space points downward; a negative Y offset lifts the held body.
            pose.translate(0.0D, -Math.min(0.6D, heightDifference * 0.35D), 0.0D);
        }
    }
}
