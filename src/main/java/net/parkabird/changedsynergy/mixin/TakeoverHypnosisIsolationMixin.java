package net.parkabird.changedsynergy.mixin;

import net.minecraftforge.event.TickEvent;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Takeover owns local input; never start or resume a hypnosis gaze contest under it. */
@Mixin(value = HypnosisQteClientState.class, remap = false)
public abstract class TakeoverHypnosisIsolationMixin {
    @Shadow private static void clearOverlay() { throw new AssertionError(); }
    @Shadow private static void clearRestraint() { throw new AssertionError(); }

    @Inject(method = "isControlLocked", at = @At("HEAD"), cancellable = true, remap = false)
    private static void takeover$singleLock(CallbackInfoReturnable<Boolean> ci) {
        if (TakeoverClientState.active()) ci.setReturnValue(false);
    }
    @Inject(method = "onClientTick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void takeover$noOldTick(TickEvent.ClientTickEvent event, CallbackInfo ci) {
        if (!TakeoverClientState.active()) return;
        clearOverlay();
        clearRestraint();
        ci.cancel();
    }
    @Inject(method = "receive", at = @At("HEAD"), cancellable = true, remap = false)
    private static void takeover$noOldSession(HypnosisQteSyncPacket packet, CallbackInfo ci) {
        if (!TakeoverClientState.active()) return;
        clearOverlay();
        clearRestraint();
        ci.cancel();
    }
}
