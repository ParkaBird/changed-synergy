package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurMode;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Changed's arm-hold ability always asks for GRAB_REPLICATE. Addon's universal
 * NPC grab therefore used to turn native absorption creatures into replicators.
 * The hold remains visual only; the source creature's own mode chooses the
 * final assimilation decision.
 */
@Mixin(value = GrabEntityAbilityInstance.class, remap = false)
public abstract class GrabAssimilationModeMixin {
    @Inject(
            method = "makeAssimilationDecision",
            at = @At("RETURN"),
            cancellable = true,
            require = 1)
    private void changedSynergy$preserveNativeNpcTransfurMode(
            CallbackInfoReturnable<LatexAssimilationDecision<?>> callback) {
        GrabEntityAbilityInstance ability =
                (GrabEntityAbilityInstance)(Object)this;
        if (ability.suited || ability.grabbedEntity == null) {
            return;
        }

        ChangedEntity source = ability.entity.getChangedEntity();
        if (source == null
                || source.getUnderlyingPlayer() != null
                || source.getTransfurMode() != TransfurMode.ABSORPTION) {
            return;
        }

        LatexAssimilationDecision<?> absorption =
                source.makeLatexAssimilationDecision(
                        TransfurCause.GRAB_ABSORB,
                        ability.grabbedEntity);
        if (absorption != null) {
            callback.setReturnValue(absorption);
        }
    }
}
