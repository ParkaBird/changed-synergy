package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Hides contained/technical players from native look-at and proximity goals. */
@Mixin(value = TargetingConditions.class, remap = false)
public abstract class TrueTransfurPlayerPerceptionMixin {
    @Inject(method = {"test", "m_26885_"}, at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void changedSynergy$ignoreInstinctShell(LivingEntity observer, LivingEntity target,
            CallbackInfoReturnable<Boolean> callback) {
        if (observer instanceof ChangedEntity && target instanceof ServerPlayer player
                && (TrueTransfurCompat.isInstinctShell(player)
                        || TakeoverService.hidesPlayer(player))) callback.setReturnValue(false);
    }
}
