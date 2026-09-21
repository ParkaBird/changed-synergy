package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.BondedPetSettings;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops protected player targets before ChangedEntity writes its separate
 * client-synchronised target id. Cancelling Forge's Mob target event alone is
 * too late for that field because Changed writes it after the superclass call.
 */
@Mixin(value = ChangedEntity.class, remap = false)
public abstract class SocialTargetGuardMixin {
    @Inject(
            method = {"setTarget", "m_6710_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$rejectProtectedSocialTarget(
            LivingEntity target,
            CallbackInfo callback) {
        ChangedEntity mob = (ChangedEntity)(Object)this;
        if (mob.level().isClientSide) {
            return;
        }
        if (target != null
                && LatexCreatureCombatRules.mustRejectVillageTarget(mob, target)) {
            LatexCreatureCombatRules.disengage(mob, target);
            callback.cancel();
            return;
        }
        if (!LatexSocialMemory.isSocialLatex(mob)) {
            return;
        }
        if (target instanceof ServerPlayer contained
                && TakeoverService.contains(mob, contained)) {
            LatexSocialMemory.clearRevengeMemoryToward(mob, contained);
            callback.cancel();
            return;
        }
        if (LatexSocialMemory.rejectsFollowingCombatTarget(mob, target)) {
            callback.cancel();
            return;
        }
        if (target != null
                && BondedPetSettings.usesFallbackBackend(mob)
                && LatexSocialMemory.hasActiveBond(mob)
                && !BondedPetSettings.isValidConfiguredTarget(
                        mob, LatexSocialMemory.getPetOwner(mob), target)) {
            callback.cancel();
            return;
        }
        if (target instanceof ServerPlayer player
                && !LatexSocialMemory.isPetDefenseAuthorized(mob, player)) {
            boolean sociallyRejected = NpcDispositionEvents.mustRejectTarget(mob, player);
            if (sociallyRejected
                    || !NpcDispositionEvents.mayAcceptTargetAssignment(mob, player)) {
                if (sociallyRejected) {
                    LatexSocialMemory.clearRevengeMemoryToward(mob, player);
                }
                callback.cancel();
            }
        } else if (target instanceof ChangedEntity other
                && LatexCreatureCombatRules.mustRejectTarget(mob, other)) {
            LatexCreatureCombatRules.disengage(mob, other);
            callback.cancel();
        }
    }
}
