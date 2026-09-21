package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.Mob;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Times the full vanilla, Changed, addon, and Synergy AI pass for Changed entities. */
@Mixin(Mob.class)
public abstract class ChangedEntityAiTimingMixin {
    @Unique
    private long changedSynergy$aiStartedNanos;

    @Inject(
            method = {"serverAiStep()V", "m_8024_()V"},
            at = @At("HEAD"),
            require = 1)
    private void changedSynergy$beginAiTiming(CallbackInfo callback) {
        if ((Object)this instanceof ChangedEntity) {
            changedSynergy$aiStartedNanos = SynergyPerformanceTracker.beginLatexAi();
        }
    }

    @Inject(
            method = {"serverAiStep()V", "m_8024_()V"},
            at = @At("RETURN"),
            require = 1)
    private void changedSynergy$endAiTiming(CallbackInfo callback) {
        if ((Object)this instanceof ChangedEntity) {
            SynergyPerformanceTracker.endLatexAi(changedSynergy$aiStartedNanos);
            changedSynergy$aiStartedNanos = 0L;
        }
    }
}
