package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.parkabird.changedsynergy.ai.GrabEscapeStunService;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents attack animation and damage while a creature is concentrating on hypnosis. */
@Mixin(Mob.class)
public abstract class HypnosisMobAttackGuardMixin {
    @Inject(
            method = {"doHurtTarget", "m_7327_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$pauseAttackDuringHypnosis(
            Entity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof LivingEntity living
                && (HypnosisQteService.shouldSuppressHypnotistAttack(
                                (Mob)(Object)this, living)
                        || GrabEscapeStunService.shouldSuppressAttack(
                                (Mob)(Object)this, living))) {
            callback.setReturnValue(false);
        }
    }
}
