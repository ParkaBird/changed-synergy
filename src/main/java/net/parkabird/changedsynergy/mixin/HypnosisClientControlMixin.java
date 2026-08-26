package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Stops attack, mining, and use packets while hypnosis has locked local controls. */
@Mixin(Minecraft.class)
public abstract class HypnosisClientControlMixin {
    @Inject(
            method = {"startAttack", "m_202354_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$blockAttack(CallbackInfoReturnable<Boolean> callback) {
        if (HypnosisQteClientState.isControlLocked()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = {"continueAttack", "m_91386_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$blockContinuousAttack(
            boolean leftClick,
            CallbackInfo callback) {
        if (HypnosisQteClientState.isControlLocked()) {
            callback.cancel();
        }
    }

    @Inject(
            method = {"startUseItem", "m_91277_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$blockUseItem(CallbackInfo callback) {
        if (HypnosisQteClientState.isControlLocked()) {
            callback.cancel();
        }
    }
}
