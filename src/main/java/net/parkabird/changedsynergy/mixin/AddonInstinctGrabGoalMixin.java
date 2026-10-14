package net.parkabird.changedsynergy.mixin;

import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ai.NativeRoleplayService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Human pursuit uses melee progress; Addon's grab wind-up must not interrupt it. */
@Pseudo
@Mixin(targets = {"net.foxyas.changedaddon.entity.ai.goals.abilities.MayGrabTargetGoal",
        "net.foxyas.changedaddon.entity.ai.goals.abilities.LungeAndGrabTargetGoal"}, remap = false)
public abstract class AddonInstinctGrabGoalMixin {
    @Shadow(remap = false) @Final private IGrabberEntity grabber;

    @Inject(method = {"canUse", "m_8036_", "canContinueToUse", "m_8045_"},
            at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void changedSynergy$keepHumanPursuit(CallbackInfoReturnable<Boolean> callback) {
        if (grabber.asMob() instanceof ChangedEntity body && TrueTransfurCompat.isInstinctBody(body)
                && body.getTarget() instanceof ServerPlayer) {
            ServerPlayer owner = TrueTransfurCompat.bodyOwner(body);
            if (owner != null && NativeRoleplayService.active(owner)) callback.setReturnValue(false);
        }
    }
}
