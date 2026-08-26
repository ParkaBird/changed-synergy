package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.LatexCreatureCombatRules;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Rejects invalid social targets during goal selection, before a melee goal can
 * briefly start and display an attack pose on the spawn tick.
 */
@Mixin(LivingEntity.class)
public abstract class SocialMobCombatGuardMixin {
    @Inject(method = {"canAttack", "m_6779_"}, at = @At("HEAD"), cancellable = true)
    private void changedSynergy$rejectProtectedSocialAttack(
            LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        LivingEntity attacker = (LivingEntity)(Object)this;
        if (!(attacker instanceof ChangedEntity changed)
                || changed.level().isClientSide
                || !LatexSocialMemory.isSocialLatex(changed)) {
            return;
        }
        // Friendly/native pairs are normally rejected before Changed can
        // consult its fusion table. A valid, non-polite fusion approach is a
        // social action rather than combat, so let the original pipeline run.
        if (target instanceof ServerPlayer player
                && LatexFusionIntent.mayInitiate(changed, player)) {
            callback.setReturnValue(true);
            return;
        }
        if (target instanceof ServerPlayer player
                && !LatexSocialMemory.isPetDefenseAuthorized(changed, player)
                && NpcDispositionEvents.mustRejectTarget(changed, player)) {
            LatexSocialMemory.clearRevengeMemoryToward(changed, player);
            callback.setReturnValue(false);
        } else if (target instanceof ChangedEntity other
                && LatexCreatureCombatRules.mustRejectTarget(changed, other)) {
            LatexCreatureCombatRules.disengage(changed, other);
            callback.setReturnValue(false);
        }
    }
}
