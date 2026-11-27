package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity;
import net.minecraft.world.entity.Pose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * AbstractAquaticEntity#updateSwimming resets Pose to STANDING/SWIMMING every server tick,
 * which previously overrode Pose.SLEEPING on sleeping aquatic residents.
 */
@Mixin(AbstractAquaticEntity.class)
public abstract class AquaticSleepPoseMixin {
    @Inject(method = {"updateSwimming", "m_5844_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$preserveSleepingPose(CallbackInfo ci) {
        AbstractAquaticEntity self = (AbstractAquaticEntity) (Object) this;
        if (self.getUnderlyingPlayer() == null && self.isSleeping()) {
            self.setSwimming(false);
            if (!self.hasPose(Pose.SLEEPING)) {
                self.setPose(Pose.SLEEPING);
            }
            ci.cancel();
        }
    }
}
