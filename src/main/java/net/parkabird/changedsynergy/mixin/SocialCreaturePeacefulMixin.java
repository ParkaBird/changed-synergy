package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.Unique;

/** Keep social creatures on Peaceful without making all wild creatures persistent. */
@Mixin(Mob.class)
public abstract class SocialCreaturePeacefulMixin {
    @Redirect(method = {"checkDespawn", "m_6043_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;getDifficulty()Lnet/minecraft/world/Difficulty;"),
            require = 0, remap = false)
    private Difficulty changedSynergy$mappedDespawnDifficulty(Level level) {
        return changedSynergy$despawnDifficulty(level);
    }

    @Redirect(method = {"checkDespawn", "m_6043_"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/Level;m_46791_()Lnet/minecraft/world/Difficulty;"),
            require = 0, remap = false)
    private Difficulty changedSynergy$srgDespawnDifficulty(Level level) {
        return changedSynergy$despawnDifficulty(level);
    }

    @Unique
    private Difficulty changedSynergy$despawnDifficulty(Level level) {
        Difficulty actual = level.getDifficulty();
        // Addon overrides shouldDespawnInPeaceful on ChangedEntity. Bypass only
        // checkDespawn's difficulty-removal branch, then retain its distance checks.
        if ((Object) this instanceof ChangedEntity creature
                && actual == Difficulty.PEACEFUL
                && LatexSocialMemory.isSocialLatex(creature)) {
            return Difficulty.NORMAL;
        }
        return actual;
    }
}
