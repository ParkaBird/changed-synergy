package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.layers.LatexHeldEntityLayer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The native held-entity offset assumes a player-sized victim. */
@Mixin(value = LatexHeldEntityLayer.class, remap = false)
public abstract class HeldCreaturePositionMixin {
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
        double heightDifference = holder.getBbHeight() - held.getBbHeight();
        if (heightDifference > 0.0D) {
            pose.translate(0.0D, Math.min(0.6D, heightDifference * 0.35D), 0.0D);
        }
    }
}
