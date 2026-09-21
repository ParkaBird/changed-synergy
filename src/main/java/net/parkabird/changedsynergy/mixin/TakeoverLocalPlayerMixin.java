package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.player.LocalPlayer;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LocalPlayer.class)
public abstract class TakeoverLocalPlayerMixin {
    @Inject(method = {"drop", "m_108700_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$drop(boolean all, CallbackInfoReturnable<Boolean> ci) {
        if (TakeoverClientState.active()) ci.setReturnValue(false);
    }
}
