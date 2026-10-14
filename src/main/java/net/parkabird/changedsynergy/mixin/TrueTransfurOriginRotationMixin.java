package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The backend's client rotation code does not consult its active-player flag. */
@Pseudo
@Mixin(targets = "com.lugert.true_transfur.client.TransfurClientRotationController", remap = false)
public abstract class TrueTransfurOriginRotationMixin {
    @Shadow(remap = false) public static void discardCapture() { throw new AssertionError(); }

    @Inject(method = {"captureBody", "tick"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$onlyNativePose(Player player, CallbackInfo callback) {
        if (!NativeOriginPolicy.allowsInstinct(player)) {
            discardCapture();
            callback.cancel();
        }
    }
}
