package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class TakeoverMinecraftMixin {
    @Inject(method = {"startAttack", "m_202354_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$attack(CallbackInfoReturnable<Boolean> ci) {
        if (TakeoverClientState.active()) ci.setReturnValue(false);
    }
    @Inject(method = {"continueAttack", "m_91386_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$mine(boolean held, CallbackInfo ci) {
        if (TakeoverClientState.active()) ci.cancel();
    }
    @Inject(method = {"startUseItem", "m_91277_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$use(CallbackInfo ci) {
        if (TakeoverClientState.active() && !TakeoverClientState.allowsCurrentBedUse()) ci.cancel();
    }
}
