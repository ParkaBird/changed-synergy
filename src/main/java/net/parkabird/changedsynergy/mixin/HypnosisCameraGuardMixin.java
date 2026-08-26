package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.util.CameraUtil;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a completed escape from being overwritten by the next server ability tick. */
@Mixin(value = CameraUtil.class, remap = false)
public abstract class HypnosisCameraGuardMixin {
    @Inject(
            method = "tugEntityLookDirection(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;D)V",
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$guardServerCameraTug(
            LivingEntity target,
            LivingEntity hypnotist,
            double strength,
            CallbackInfo callback) {
        if (HypnosisQteService.shouldBlockCameraTug(target, hypnotist)) {
            callback.cancel();
        }
    }
}
