package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents Addon's auto-suit favor from racing Synergy's release and other suits. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.entity.ai.LatexSuitOwnerGoal", remap = false)
public abstract class AddonNativeSuitGoalGuardMixin {
    @Shadow(remap = false) @Final protected ChangedEntity entity;

    @Inject(method = {"canUse", "m_8036_"}, at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private void changedSynergy$waitForStableOwner(CallbackInfoReturnable<Boolean> callback) {
        ServerPlayer owner = LatexSocialMemory.getPetOwner(entity);
        if (owner != null && BondedSuitService.shouldBlockNativeSuit(entity, owner)) {
            callback.setReturnValue(false);
        }
    }
}
