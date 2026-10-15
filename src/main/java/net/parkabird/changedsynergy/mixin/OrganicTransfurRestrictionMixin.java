package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.ai.BondedPetSettings;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.OrganicCombatRules;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gates organic swipe transfur so social protection and grapple-only combat remain authoritative. */
@Mixin(value = ChangedEntity.class, remap = false)
public abstract class OrganicTransfurRestrictionMixin {
    /** Restores the ordinary Changed swipe amount before shield blocking is calculated. */
    @Inject(
            method = "makeLatexAssimilationDecision",
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private void changedSynergy$normalizeOrganicSwipeProgress(
            TransfurCause cause,
            LivingEntity target,
            CallbackInfoReturnable<LatexAssimilationDecision<?>> callback) {
        ChangedEntity self = (ChangedEntity)(Object)this;
        LatexAssimilationDecision<?> decision = callback.getReturnValue();
        if (!(target instanceof ServerPlayer player)
                || decision == null
                || self.getUnderlyingPlayer() != null
                || !LatexSocialMemory.isSocialLatex(self)
                || !LatexSocialMemory.isOrganic(self)
                || LatexSocialMemory.isSecondaryGrabActive(self, player)
                || cause != TransfurCause.GRAB_REPLICATE
                        && cause != TransfurCause.GRAB_ABSORB) {
            return;
        }

        float standardProgress = OrganicCombatRules.standardLatexSwipeProgress(self);
        if (decision.transfurProgress() < standardProgress) {
            callback.setReturnValue(decision.withTransfurProgress(standardProgress));
        }
    }

    @Inject(method = "tryTransfurTarget", at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$requireOrganicGrapple(
            Entity target,
            CallbackInfoReturnable<Boolean> callback) {
        ChangedEntity self = (ChangedEntity)(Object)this;
        if (target instanceof ServerPlayer player && net.parkabird.changedsynergy.ai.TakeoverService.hidesPlayer(player)) {
            // Consume the attempted swipe without falling back to health damage.
            callback.setReturnValue(true);return;
        }
        if (target instanceof LivingEntity living && net.parkabird.changedsynergy.ai.InstinctConfinementService.protectsHumanForm(self, living)) {
            // The final-conversion interceptor, rather than this swipe gate,
            // protects identity. Keep Changed's normal transfur damage intact.
            return;
        }
        if (ChangedAddonCompat.suppressBondedTransfurAttack(self)
                || BondedPetSettings.suppressTransfurAttack(self)) {
            callback.setReturnValue(false);
            return;
        }
        // Only hostile NPC conversion is gated; voluntary changes and Changed
        // fusion recipes keep their separate paths.
        if (self.getUnderlyingPlayer() == null
                && target instanceof ServerPlayer player
                && LatexSocialMemory.isSocialLatex(self)
                && !LatexSocialMemory.allowsHostilePlayerTransfur(player)
                && !LatexFusionIntent.nativeFusionAvailable(self, player)) {
            callback.setReturnValue(false);
            return;
        }
        if (!(target instanceof ServerPlayer player)
                || !LatexSocialMemory.isSocialLatex(self)
                || !LatexSocialMemory.isOrganic(self)
                || LatexSocialMemory.isSecondaryGrabActive(self, player)) {
            return;
        }
        boolean defendingCache =
                CreatureCacheGuardService.isDefendingAgainst(self, player);
        if (!defendingCache
                && (LatexSocialMemory.shouldRemainNeutral(self, player)
                        || LatexSocialMemory.isNeutralOrganicHumanContact(self, player))
                || OrganicCombatRules.shouldUseOnlyGrapple(self, player)) {
            callback.setReturnValue(false);
        }
    }
}
