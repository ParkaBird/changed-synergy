package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.parkabird.changedsynergy.client.RadialWheelAnimations;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** AbilityRadialScreen closes from inside handleClicked instead of its base. */
@Mixin(value = AbilityRadialScreen.class, remap = false)
public abstract class AbilityRadialScreenCloseMixin {
    @Redirect(
            method = "handleClicked",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/ltxprogrammer/changed/util/SingleRunnable;run()V",
                    remap = false),
            remap = false)
    private void changedSynergy$animateAbilitySelectionClose(
            SingleRunnable close) {
        if (!RadialWheelAnimations.requestClose(this, close::run)) {
            close.run();
        }
    }
}
