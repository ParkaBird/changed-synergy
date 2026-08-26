package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.entity.beast.DarkLatexWolfPup;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.ai.CreatureMorphContinuity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Changed grows a dark-latex pup by replacing it with an adult entity. Its
 * native copyTraitsFrom path already preserves owner, follow mode and native
 * inventory; this hook carries only Synergy's identity/life compatibility
 * data and player-side UUID references across the same replacement.
 */
@Mixin(DarkLatexWolfPup.class)
public abstract class DarkLatexWolfPupMorphMixin {
    @Inject(method = "variantTick", at = @At("RETURN"))
    private void changedSynergy$preserveNativePetAcrossGrowth(
            Level level,
            CallbackInfo callback) {
        DarkLatexWolfPup previous = (DarkLatexWolfPup)(Object)this;
        if (level.isClientSide || !previous.isRemoved()) {
            return;
        }

        AABB replacementArea = previous.getBoundingBox().inflate(0.35D);
        ChangedEntity replacement = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        replacementArea,
                        candidate -> candidate != previous
                                && candidate instanceof AbstractDarkLatexEntity
                                && !(candidate instanceof DarkLatexWolfPup)
                                && candidate.tickCount <= 1)
                .stream()
                .min((left, right) -> Double.compare(
                        left.distanceToSqr(previous),
                        right.distanceToSqr(previous)))
                .orElse(null);
        if (replacement != null) {
            CreatureMorphContinuity.transfer(previous, replacement);
        }
    }
}
