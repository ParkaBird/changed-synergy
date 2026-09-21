package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.ability.ILatexAssimilatedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.AssimilationBehavior;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.TakeoverAssimilationBehavior;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Intercepts only NPC -> player absorption, before native kill/replace/listeners. */
@Mixin(value = LatexAssimilationDecision.class, remap = false)
public abstract class TakeoverAbsorptionMixin {
    @Inject(
            method = "transfurByAbsorption(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/ltxprogrammer/changed/ability/IAbstractChangedEntity;)"
                    + "Lnet/ltxprogrammer/changed/entity/ai/AssimilationBehavior;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void changedSynergy$wrapOrdinaryAbsorption(
            LivingEntity target, IAbstractChangedEntity transfurSource,
            CallbackInfoReturnable<AssimilationBehavior> callback) {
        if (target instanceof ServerPlayer player
                && transfurSource != null
                && transfurSource.getEntity() instanceof ChangedEntity carrier
                && carrier.getUnderlyingPlayer() == null
                && !LatexFusionIntent.nativeFusionAvailable(carrier, player)) {
            callback.setReturnValue(new TakeoverAssimilationBehavior(callback.getReturnValue(),
                    carrier, player, transfurSource));
        }
    }

    /**
     * NPC assimilation contexts normally use ILatexAssimilatedEntity (the right
     * side of Changed's context Either), while player-backed contexts use
     * IAbstractChangedEntity. Both overloads must be intercepted or ordinary NPC
     * takeover silently falls through to native absorption and negotiation.
     */
    @Inject(
            method = "transfurByAbsorption(Lnet/minecraft/world/entity/LivingEntity;"
                    + "Lnet/ltxprogrammer/changed/ability/ILatexAssimilatedEntity;)"
                    + "Lnet/ltxprogrammer/changed/entity/ai/AssimilationBehavior;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void changedSynergy$wrapAssimilatedEntityAbsorption(
            LivingEntity target, ILatexAssimilatedEntity transfurSource,
            CallbackInfoReturnable<AssimilationBehavior> callback) {
        if (target instanceof ServerPlayer player
                && transfurSource != null
                && transfurSource.getEntity() instanceof ChangedEntity carrier
                && carrier.getUnderlyingPlayer() == null
                && !LatexFusionIntent.nativeFusionAvailable(carrier, player)) {
            callback.setReturnValue(new TakeoverAssimilationBehavior(callback.getReturnValue(),
                    carrier, player, IAbstractChangedEntity.forEntity(carrier)));
        }
    }
}
