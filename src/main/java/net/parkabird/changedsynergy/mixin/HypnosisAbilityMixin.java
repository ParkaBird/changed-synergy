package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.HypnosisAbility;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = HypnosisAbility.class, remap = false)
public abstract class HypnosisAbilityMixin {
    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void changedSynergy$observeHypnosis(
            IAbstractChangedEntity entity,
            CallbackInfo callback) {
        HypnosisQteService.observeAbilityTick(entity);
    }
}
