package net.parkabird.changedsynergy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.parkabird.changedsynergy.world.ChangedSpawnPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Marks the exact interval in which a Changed natural-spawn predicate runs. */
@Mixin(SpawnPlacements.class)
public abstract class SpawnPlacementsDaylightContextMixin {
    @Inject(method = {"checkSpawnRules", "m_217074_"}, at = @At("HEAD"))
    private static void changedSynergy$beginSpawnRule(
            EntityType<?> type,
            ServerLevelAccessor level,
            MobSpawnType reason,
            BlockPos position,
            RandomSource random,
            CallbackInfoReturnable<Boolean> callback) {
        ChangedSpawnPlacementContext.push(type, reason);
    }

    @Inject(method = {"checkSpawnRules", "m_217074_"}, at = @At("RETURN"))
    private static void changedSynergy$endSpawnRule(
            EntityType<?> type,
            ServerLevelAccessor level,
            MobSpawnType reason,
            BlockPos position,
            RandomSource random,
            CallbackInfoReturnable<Boolean> callback) {
        ChangedSpawnPlacementContext.pop();
    }
}
