package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.client.gui.GrabOverlay;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.parkabird.changedsynergy.client.GrabQteOverlayRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces only the hostile player-side pieces of Changed's grab overlay. */
@Mixin(value = GrabOverlay.class, remap = false)
public abstract class GrabOverlayAnimationMixin {
    @Inject(
            method = "renderProgressBars",
            at = @At("HEAD"),
            remap = false)
    private static void changedSynergy$beginAnimatedGrabFrame(
            Gui gui,
            GuiGraphics graphics,
            float partialTick,
            int width,
            int height,
            CallbackInfo callback) {
        GrabQteOverlayRenderer.beginFrame(partialTick);
    }

    @Inject(
            method = "renderProgressBarPlayer",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$renderAnimatedPlayerBar(
            GuiGraphics graphics,
            float partialTick,
            int width,
            int height,
            CallbackInfo callback) {
        if (GrabQteOverlayRenderer.renderProgress(
                graphics, partialTick, width, height)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "renderEscapeKeys(Lnet/minecraft/client/gui/Gui;Lnet/minecraft/client/gui/GuiGraphics;FIILnet/ltxprogrammer/changed/ability/GrabEntityAbilityInstance;)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$renderAnimatedEscapeKeys(
            Gui gui,
            GuiGraphics graphics,
            float partialTick,
            int width,
            int height,
            GrabEntityAbilityInstance ability,
            CallbackInfo callback) {
        if (GrabQteOverlayRenderer.renderKeys(
                gui, graphics, partialTick, width, height, ability)) {
            callback.cancel();
        }
    }

    @Inject(
            method = "renderProgressBars",
            at = @At("TAIL"),
            remap = false)
    private static void changedSynergy$renderGrabRelease(
            Gui gui,
            GuiGraphics graphics,
            float partialTick,
            int width,
            int height,
            CallbackInfo callback) {
        GrabQteOverlayRenderer.renderExit(graphics, width, height);
    }
}
