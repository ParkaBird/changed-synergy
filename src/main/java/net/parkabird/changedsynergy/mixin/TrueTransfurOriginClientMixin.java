package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.lugert.true_transfur.client.TransfurClientState", remap = false)
public abstract class TrueTransfurOriginClientMixin {
    @Inject(method = "isInstinct", at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$humanControl(CallbackInfoReturnable<Boolean> callback) {
        if (!NativeOriginPolicy.allowsInstinct(Minecraft.getInstance().player)) callback.setReturnValue(false);
    }
}
