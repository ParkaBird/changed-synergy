package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.GrabEscapeStunService;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers ChangedEntity's transfur-attack override, which can bypass Mob.doHurtTarget. */
@Mixin(value = ChangedEntity.class, remap = false)
public abstract class HypnosisChangedEntityAttackGuardMixin {
    @Inject(
            method = {"doHurtTarget", "m_7327_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$pauseTransfurAttackDuringHypnosis(
            Entity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (target instanceof LivingEntity living
                && (HypnosisQteService.shouldSuppressHypnotistAttack(
                                (ChangedEntity)(Object)this, living)
                        || GrabEscapeStunService.shouldSuppressAttack(
                                (ChangedEntity)(Object)this, living))) {
            callback.setReturnValue(false);
        }
    }
}
