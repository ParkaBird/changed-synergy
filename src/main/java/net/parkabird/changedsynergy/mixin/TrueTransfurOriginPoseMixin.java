package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ignore old remote pose packets immediately when the world policy changes. */
@Pseudo
@Mixin(targets = "com.lugert.true_transfur.client.TransfurClientPoseState", remap = false)
public abstract class TrueTransfurOriginPoseMixin {
    @Inject(method = {"applySwing", "applyRotations"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$onlyNativePose(Player player, @Coerce Object pose, CallbackInfo callback) {
        if (!NativeOriginPolicy.allowsInstinct(player)) callback.cancel();
    }
}
