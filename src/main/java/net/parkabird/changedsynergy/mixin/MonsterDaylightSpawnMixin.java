package net.parkabird.changedsynergy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.parkabird.changedsynergy.world.ChangedSpawnPlacementContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds a low-rate daylight surface exception for Changed and Addon spawns. */
@Mixin(Monster.class)
public abstract class MonsterDaylightSpawnMixin {
    @Inject(
            method = {"isDarkEnoughToSpawn", "m_219009_"},
            at = @At("HEAD"),
            cancellable = true)
    private static void changedSynergy$allowChangedDaylightSpawn(
            ServerLevelAccessor level,
            BlockPos position,
            RandomSource random,
            CallbackInfoReturnable<Boolean> callback) {
        if (ChangedSpawnPlacementContext.passesDaylightSurfaceException(
                level, position, random)) {
            callback.setReturnValue(true);
        }
    }
}
