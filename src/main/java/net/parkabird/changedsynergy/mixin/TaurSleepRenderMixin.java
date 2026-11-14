package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AdvancedHumanoidRenderer.class)
public abstract class TaurSleepRenderMixin<T extends ChangedEntity, M extends AdvancedHumanoidModel<T>> {
    @SuppressWarnings("unchecked")
    @Inject(
            method = "setupRotations(Lnet/ltxprogrammer/changed/entity/ChangedEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V",
            at = @At("TAIL"),
            remap = false,
            require = 1
    )
    private void changedSynergy$cancelSleepingForwardOffset(
            T entity, PoseStack poseStack, float ageInTicks, float yBodyRot, float partialTicks, CallbackInfo ci) {
        if (entity == null || !entity.hasPose(Pose.SLEEPING)) {
            return;
        }
        M model = ((LivingEntityRenderer<T, M>) (Object) this).getModel();
        if (model == null) {
            return;
        }
        HumanoidAnimator<T, ?> animator = model.getAnimator(entity);
        if (animator != null && animator.forwardOffset != 0.0F) {
            poseStack.translate(0.0D, 0.0D, -(double) animator.forwardOffset / 16.0D);
        }
    }
}
