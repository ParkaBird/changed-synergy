package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Addon dive shockwaves can divide by zero when a suited player shares the carrier's position. */
@Mixin(Entity.class)
public abstract class FiniteEntityPushMixin {
    @Inject(method = {"push(DDD)V", "m_5997_(DDD)V"}, at = @At("HEAD"),
            cancellable = true, require = 0, remap = false)
    private void changedSynergy$rejectNonFinitePush(double x, double y, double z, CallbackInfo callback) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)) {
            callback.cancel();
        }
    }
}
