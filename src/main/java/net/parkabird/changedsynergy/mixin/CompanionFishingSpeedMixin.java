package net.parkabird.changedsynergy.mixin;

import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MoveToBlockGoal;
import net.parkabird.changedsynergy.ai.CompanionToolEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Apply Lure to the existing native cast cycle without replacing its loot or durability logic. */
@Pseudo
@Mixin(targets = {"net.ltxprogrammer.changed.entity.ai.DarkLatexFishingGoal",
        "net.foxyas.changedaddon.entity.ai.LatexFishingGoal"}, remap = false)
public abstract class CompanionFishingSpeedMixin extends MoveToBlockGoal {
    protected CompanionFishingSpeedMixin(PathfinderMob mob, double speed, int range) { super(mob, speed, range); }

    @ModifyConstant(method = {"tick", "m_8037_"}, constant = @Constant(intValue = 160), remap = false)
    private int changedSynergy$enchantedCastDuration(int original) {
        return CompanionToolEffects.fishingWaitTicks(mob.getMainHandItem());
    }
}
