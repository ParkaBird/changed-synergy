package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps sleeping ChangedEntity residents anchored instead of letting bumps shove them off beds. */
@Mixin(Entity.class)
public abstract class SleepingEntityPushMixin {
    @Inject(method = {"push(Lnet/minecraft/world/entity/Entity;)V", "m_7334_(Lnet/minecraft/world/entity/Entity;)V"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$ignorePushWhileSleeping(Entity other, CallbackInfo ci) {
        if ((Object) this instanceof ChangedEntity entity && entity.getUnderlyingPlayer() == null && entity.isSleeping()) {
            ci.cancel();
        }
    }

    @Inject(method = {"push(DDD)V", "m_5997_(DDD)V"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$ignoreVectorPushWhileSleeping(double x, double y, double z, CallbackInfo ci) {
        if ((Object) this instanceof ChangedEntity entity && entity.getUnderlyingPlayer() == null && entity.isSleeping()) {
            ci.cancel();
        }
    }
}
