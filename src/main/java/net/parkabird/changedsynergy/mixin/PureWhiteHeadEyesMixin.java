package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.parkabird.changedsynergy.client.PureWhiteAdaptedEyesLayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Supplies a render-local eye snapshot without modifying the player or creature's appearance. */
@Mixin(value = CustomEyesLayer.class, remap = false)
public abstract class PureWhiteHeadEyesMixin {
    @ModifyArg(method = "render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFFF)V",
            at = @At(value = "INVOKE", target = "Lnet/ltxprogrammer/changed/entity/BasicPlayerInfo;copyFrom(Lnet/ltxprogrammer/changed/entity/BasicPlayerInfo;)V"), index = 0)
    private BasicPlayerInfo changedSynergy$individualHeadEyes(BasicPlayerInfo original) {
        return (Object)this instanceof PureWhiteAdaptedEyesLayer<?> layer ? layer.appearanceForHead(original) : original;
    }
}
