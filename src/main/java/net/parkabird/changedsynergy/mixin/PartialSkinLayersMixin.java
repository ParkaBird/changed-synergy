package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.AbstractClientPlayer;
import net.parkabird.changedsynergy.client.PartialSkinLayersCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = {
        "net.ltxprogrammer.changed.client.renderer.model.LatexHumanModel",
        "net.ltxprogrammer.changed.client.renderer.model.DarkLatexWolfPartialModel",
        "net.foxyas.changedaddon.client.model.SnowLeopardPartialModel",
        "net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel"
}, remap = false)
public abstract class PartialSkinLayersMixin {
    @Inject(method = "setModelProperties", at = @At("RETURN"), require = 0)
    private void changedSynergy$playerSkinLayers(AbstractClientPlayer player, CallbackInfo callback) {
        PartialSkinLayersCompat.apply(this, player);
    }

    @Inject(method = "defaultModelProperties", at = @At("RETURN"), require = 0)
    private void changedSynergy$clearPlayerSkinLayers(CallbackInfo callback) {
        PartialSkinLayersCompat.apply(this, null);
    }

    @Inject(
            method = {
                    "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                    "m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"
            },
            at = @At("HEAD"),
            require = 0,
            remap = false
    )
    private void changedSynergy$beforeRenderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo callback) {
        PartialSkinLayersCompat.beforeRenderToBuffer(this);
    }

    @Inject(
            method = {
                    "renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V",
                    "m_7695_(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;IIFFFF)V"
            },
            at = @At("RETURN"),
            require = 0,
            remap = false
    )
    private void changedSynergy$afterRenderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha,
            CallbackInfo callback) {
        PartialSkinLayersCompat.afterRenderToBuffer(
                this, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }
}
