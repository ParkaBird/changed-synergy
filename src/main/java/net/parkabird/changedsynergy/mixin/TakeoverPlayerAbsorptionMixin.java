package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.ability.ILatexAssimilatedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.AssimilationBehavior;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.TakeoverAssimilationBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Changed's dedicated player assimilation behavior does not call either
 * LatexAssimilationDecision.transfurByAbsorption overload for an NPC source.
 * This is therefore the authoritative NPC -> player interception point.
 */
@Mixin(targets = "net.ltxprogrammer.changed.entity.ai.EntityAssimilationBehavior$PlayerAssimilationBehavior",
        remap = false)
public abstract class TakeoverPlayerAbsorptionMixin {
    @Inject(
            method = "latexAssimilateVictimBehavior(Lnet/minecraft/world/entity/player/Player;"
                    + "Lnet/ltxprogrammer/changed/entity/ai/LatexAssimilationDecision;)"
                    + "Lnet/ltxprogrammer/changed/entity/ai/AssimilationBehavior;",
            at = @At("RETURN"), cancellable = true, require = 1)
    private void changedSynergy$wrapNpcPlayerAbsorption(Player target,
            LatexAssimilationDecision<?> decision,
            CallbackInfoReturnable<AssimilationBehavior> callback) {
        if (!(target instanceof ServerPlayer player)
                || decision == null
                || decision.method() != LatexAssimilationDecision.Method.ABSORPTION
                || callback.getReturnValue() == null
                || decision.context().source() == null) return;
        LivingEntity source = decision.context().source().map(
                IAbstractChangedEntity::getEntity, ILatexAssimilatedEntity::getEntity);
        if (!(source instanceof ChangedEntity carrier)
                || carrier.getUnderlyingPlayer() != null) return;
        callback.setReturnValue(new TakeoverAssimilationBehavior(
                callback.getReturnValue(), carrier, player,
                IAbstractChangedEntity.forEntity(carrier)));
    }
}
