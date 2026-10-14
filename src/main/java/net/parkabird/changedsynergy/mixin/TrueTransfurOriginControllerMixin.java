package net.parkabird.changedsynergy.mixin;

import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "com.lugert.true_transfur.transfur.TransfurController", remap = false)
public abstract class TrueTransfurOriginControllerMixin {
    @Inject(method = "isTrueTransfurActive", at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$onlyNative(ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        if (!NativeOriginPolicy.allows(player)) callback.setReturnValue(false);
    }
    @Inject(method="isChangedTransfurred",at=@At("HEAD"),cancellable=true,remap=false)
    private static void changedSynergy$controlEligibility(ServerPlayer player,CallbackInfoReturnable<Boolean> callback) {
        if(!NativeOriginPolicy.allows(player))callback.setReturnValue(false);
    }
    @Redirect(method={"switchControlMode","isPlayerControlled","isInstinctControlled"},at=@At(value="INVOKE",
            target="Lcom/lugert/true_transfur/transfur/TransfurController;isTrueTransfurActive(Lnet/minecraft/server/level/ServerPlayer;)Z"),require=0,remap=false)
    private static boolean changedSynergy$separateControlPermission(ServerPlayer player) {
        return net.parkabird.changedsynergy.compat.TrueTransfurCompat.allowsInstinctControl(player);
    }
}
