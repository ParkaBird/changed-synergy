package net.parkabird.changedsynergy.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.parkabird.changedsynergy.ability.HypnosisToggle;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(value=AbstractAbility.UseType.class, remap=false)
public abstract class HypnosisInputMixin {
    @WrapMethod(method="check")
    private void changedSynergy$latch(boolean down, boolean wasDown, boolean unique, AbstractAbility.Controller controller, Operation<Void> original) {
        var toggle=(HypnosisToggle)controller;
        if ((Object)this==AbstractAbility.UseType.HOLD && toggle.changedSynergy$isPlayerHypnosis()) {
            boolean previous=toggle.changedSynergy$isLatched();
            toggle.changedSynergy$input(down,wasDown);
            original.call(toggle.changedSynergy$isLatched(),previous,unique,controller);
        } else original.call(down,wasDown,unique,controller);
    }
}
