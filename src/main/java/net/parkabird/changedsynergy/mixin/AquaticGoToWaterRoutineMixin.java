package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.RoutineState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Changed's priority-one aquatic return goal otherwise pre-empts Synergy's
 * lower-priority work goal the instant a near-shore fisher climbs onto land.
 * It is suppressed only for the duration of that fishing routine; ordinary
 * aquatic survival behaviour remains untouched at every other time.
 */
@Mixin(targets =
        "net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity$GoToWaterGoal")
public abstract class AquaticGoToWaterRoutineMixin {
    @Shadow @Final private AbstractAquaticEntity mob;

    @Inject(
            method = {
                    "canUse", "m_8036_",
                    "canContinueToUse", "m_8045_"
            },
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$finishShoreFishing(
            CallbackInfoReturnable<Boolean> callback) {
        if (CreatureLifeMemory.routine(mob) == RoutineState.FISHING) {
            callback.setReturnValue(false);
        }
    }
}
