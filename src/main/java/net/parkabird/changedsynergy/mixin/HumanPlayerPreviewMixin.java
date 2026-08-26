package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.EventHandlerClient;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.parkabird.changedsynergy.client.HumanPlayerPreviewRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps Changed from replacing the negotiation preview with the active form. */
@Mixin(value = EventHandlerClient.class, remap = false)
public abstract class HumanPlayerPreviewMixin {
    @Inject(
            method = "onRenderPlayerPre",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$renderOriginalPlayerSkin(
            RenderPlayerEvent.Pre event,
            CallbackInfo callback) {
        if (HumanPlayerPreviewRenderState.isActive()
                && event.getEntity() == Minecraft.getInstance().player) {
            callback.cancel();
        }
    }
}
