package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.latex.SpreadingLatexType;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.minecraft.util.RandomSource;
import net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Mirrors dark latex: a fallen white latex container yields the partial wolf. */
@Mixin(value = SpreadingLatexType.WhiteLatex.class, remap = false)
public abstract class WhiteLatexPartialTransfurMixin {
    @Inject(method = "getTransfurVariant", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$containerFellPartial(
            TransfurCause cause,
            RandomSource random,
            CallbackInfoReturnable<TransfurVariant<?>> callback) {
        if (cause == TransfurCause.LATEX_CONTAINER_FELL) {
            callback.setReturnValue(
                    ChangedSynergyTransfurVariants.PURE_WHITE_LATEX_WOLF_PARTIAL.get());
        }
    }
}
