package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.parkabird.changedsynergy.ai.AddonCarriedPlayerMotionGuard;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Exp009's thunder dive is a separate goal from the generic Alpha leap. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.entity.ai.goals.exp9.ThunderDiveGoal", remap = false)
public abstract class AddonThunderDiveCarryMixin {
    @Shadow(remap = false) @Final private PathfinderMob mob;

    @Inject(method = {"canUse", "m_8036_", "canContinueToUse", "m_8045_"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void changedSynergy$stopDiveWhileHolding(CallbackInfoReturnable<Boolean> callback) {
        if (mob instanceof ChangedEntity changed
                && AddonCarriedPlayerMotionGuard.isCarryingPlayer(changed)) {
            callback.setReturnValue(false);
        }
    }
}
