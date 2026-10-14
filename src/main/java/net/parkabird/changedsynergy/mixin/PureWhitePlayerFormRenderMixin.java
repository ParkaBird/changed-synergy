package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.FormRenderHandler;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.PureWhitePlayerAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Changes only the object passed to the renderer, including first-person arms. */
@Mixin(value = FormRenderHandler.class, remap = false)
public abstract class PureWhitePlayerFormRenderMixin {
    @ModifyVariable(method = {
            "renderLiving(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IF)V",
            "renderHand(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/HumanoidArm;Lnet/minecraft/client/model/geom/PartPose;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IFZ)V"
        }, at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private static LivingEntity changedSynergy$renderAdaptedWolf(
            LivingEntity source) {
        return PureWhitePlayerAppearance.forRender(source);
    }
}
