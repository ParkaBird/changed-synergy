package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.compat.ChangedExtrasCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Makes Changed Extras' independent brain honor Synergy's social ceasefires. */
@Pseudo
@Mixin(
        targets = "com.katt.changedextras.common.ai.LatexBrain",
        remap = false)
public abstract class ChangedExtrasLatexBrainMixin {
    @Inject(
            method = "isValidAggroTarget",
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$rejectProtectedAggro(
            ChangedEntity mob,
            LivingEntity target,
            @Coerce Object mind,
            CallbackInfoReturnable<Boolean> callback) {
        if (ChangedExtrasCompat.rejectsTarget(mob, target)) {
            callback.setReturnValue(false);
        }
    }

    @Inject(
            method = "resolveTarget",
            at = @At("RETURN"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$rejectProtectedResolvedTarget(
            ChangedEntity mob,
            @Coerce Object mind,
            CallbackInfoReturnable<LivingEntity> callback) {
        LivingEntity target = callback.getReturnValue();
        if (target != null && ChangedExtrasCompat.rejectsTarget(mob, target)) {
            mob.setTarget(null);
            callback.setReturnValue(null);
        }
    }
}
