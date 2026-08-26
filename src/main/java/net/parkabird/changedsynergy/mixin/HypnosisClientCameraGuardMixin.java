package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.util.CameraUtil;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mirrors the server escape window for local Changed ability ticks. */
@Mixin(value = CameraUtil.class, remap = false)
public abstract class HypnosisClientCameraGuardMixin {
    @Inject(
            method = "tugEntityLookDirection(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;D)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$guardClientCameraTug(
            LivingEntity target,
            LivingEntity hypnotist,
            double strength,
            CallbackInfo callback) {
        if (target instanceof Player
                && HypnosisQteClientState.shouldBlockCameraTug(hypnotist)) {
            callback.cancel();
        }
    }
}
