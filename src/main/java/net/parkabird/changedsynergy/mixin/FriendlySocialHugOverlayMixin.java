package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.gui.GrabOverlay;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.GrabQteOverlayRenderer;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Friendly play hugs deliberately have no escape-QTE progress bar. */
@Mixin(value = GrabOverlay.class, remap = false)
public abstract class FriendlySocialHugOverlayMixin {
    @Inject(
            method = "renderProgressBars",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$hideFriendlyHugProgress(
            Gui gui,
            GuiGraphics graphics,
            float partialTick,
            int width,
            int height,
            CallbackInfo callback) {
        var player = Minecraft.getInstance().player;
        if (!(player instanceof LivingEntityDataExtension extension)) {
            return;
        }
        LivingEntity grabber = extension.getGrabbedBy();
        if (grabber != null
                && FriendlySocialHugState.isActive(grabber.getId(), player.getId())) {
            GrabQteOverlayRenderer.clearForFriendlyHold();
            callback.cancel();
        }
    }
}
