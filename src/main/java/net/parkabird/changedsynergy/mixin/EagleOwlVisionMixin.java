package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.parkabird.changedsynergy.client.EagleOwlVisionClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Includes first-person hands while keeping the HUD and inventory in their original colors. */
@Mixin(GameRenderer.class)
public abstract class EagleOwlVisionMixin {
    @Inject(method={"renderLevel","m_109089_"},at=@At("RETURN"),remap=false,require=1)
    private void changedSynergy$owlWorldAndHands(float partialTick,long finishTime,PoseStack pose,CallbackInfo ci){
        EagleOwlVisionClient.render(partialTick);
    }
}
