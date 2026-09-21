package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.event.RandomizedTransfurMethodEvents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Selects the encounter method before any Forge listener observes the decision. */
@Mixin(value = TransfurEvents.LatexAssimilationDecisionEvent.class, remap = false)
public abstract class RandomizedTransfurDecisionMixin {
    @Inject(method = "<init>", at = @At("RETURN"), require = 1)
    private void changedSynergy$randomizeMethod(
            LivingEntity target,
            LatexAssimilationDecision<?> decision,
            CallbackInfo callback) {
        RandomizedTransfurMethodEvents.randomize(
                (TransfurEvents.LatexAssimilationDecisionEvent)(Object)this);
    }
}
