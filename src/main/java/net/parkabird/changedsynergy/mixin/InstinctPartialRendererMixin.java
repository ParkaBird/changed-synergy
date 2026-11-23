package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.parkabird.changedsynergy.client.InstinctPlayerSkinClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Applies host player model properties (outer skin toggles + 3D skin layers) for Instinct shells. */
@Mixin(value = AdvancedHumanoidRenderer.class, remap = false)
public abstract class InstinctPartialRendererMixin {
    @Inject(method = "render(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V",
            at = @At("HEAD"))
    private void changedSynergy$applyInstinctModelProperties(
            ChangedEntity entity, float yRot, float partialTick,
            PoseStack pose, MultiBufferSource buffers, int packedLight, CallbackInfo callback) {
        InstinctPlayerSkinClient.applyModelPropertiesIfInstinct(
                ((AdvancedHumanoidRenderer<?, ?>) (Object) this).getModel(), entity);
    }
}
