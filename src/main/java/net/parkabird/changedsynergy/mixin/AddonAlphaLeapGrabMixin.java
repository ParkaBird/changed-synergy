package net.parkabird.changedsynergy.mixin;

import net.foxyas.changedaddon.entity.api.IAlphaAbleEntity;
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

/** An Alpha's autonomous dive must not start or continue while carrying a player. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.entity.ai.goals.generic.attacks.LeapDiveGoal", remap = false)
public abstract class AddonAlphaLeapGrabMixin {
    @Shadow(remap = false) @Final protected PathfinderMob mob;

    @Inject(method = {"canUse", "m_8036_", "canContinueToUse", "m_8045_"}, at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private void changedSynergy$stopLeapWhileHolding(CallbackInfoReturnable<Boolean> callback) {
        if (mob instanceof IAlphaAbleEntity alpha && alpha.isAlpha()
                && mob instanceof ChangedEntity changed
                && AddonCarriedPlayerMotionGuard.isCarryingPlayer(changed)) {
            callback.setReturnValue(false);
        }
    }
}
