package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import net.parkabird.changedsynergy.client.PlayerSocialClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hide remote shells before Changed substitutes their named creature model. */
@Mixin(EntityRenderDispatcher.class)
public abstract class InstinctTechnicalPlayerRenderMixin {
    @Inject(method = {"render", "m_114384_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private <E extends Entity> void changedSynergy$hideTechnicalPlayer(E entity,
            double x, double y, double z, float yaw, float partialTicks,
            PoseStack pose, MultiBufferSource buffers, int light, CallbackInfo callback) {
        if (PlayerSocialClient.isHiddenTechnicalPlayer(entity)) callback.cancel();
    }
}
