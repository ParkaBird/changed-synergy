package net.parkabird.changedsynergy.mixin;

import net.foxyas.changedaddon.entity.bosses.Experiment009BossEntity;
import net.parkabird.changedsynergy.ai.AddonCarriedPlayerMotionGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** The boss thunder dash is independent of the Alpha leap and thunder dive. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.entity.ai.goals.exp9.ThunderDashAttack", remap = false)
public abstract class AddonThunderDashCarryMixin {
    @Shadow(remap = false) @Final protected Experiment009BossEntity dasher;

    @Inject(method = {"canUse", "m_8036_", "canContinueToUse", "m_8045_"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void changedSynergy$stopDashWhileHolding(CallbackInfoReturnable<Boolean> callback) {
        if (AddonCarriedPlayerMotionGuard.isCarryingPlayer(dasher)) {
            callback.setReturnValue(false);
        }
    }
}
