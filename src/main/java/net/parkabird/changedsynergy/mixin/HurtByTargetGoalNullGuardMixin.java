package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Some target-event combinations can clear a mob's revenge target between
 * HurtByTargetGoal.canUse and start. Vanilla's ally alert assumes it survived.
 */
@Mixin(HurtByTargetGoal.class)
public abstract class HurtByTargetGoalNullGuardMixin
        extends TargetGoal {
    protected HurtByTargetGoalNullGuardMixin(
            Mob mob,
            boolean mustSee) {
        super(mob, mustSee);
    }

    @Inject(
            method = {"alertOthers", "m_26047_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private void changedSynergy$skipAlertWithoutTarget(
            CallbackInfo callback) {
        if (mob.getTarget() == null) {
            callback.cancel();
        }
    }
}
