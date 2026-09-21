package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.block.LabTable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A table is too high to step onto, so keep land paths off its top cell. */
@Mixin(WalkNodeEvaluator.class)
public abstract class LabTablePathfindingMixin {
    @Inject(
            method = {"getBlockPathTypeRaw", "m_77643_"},
            at = @At("HEAD"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$avoidLabTables(
            BlockGetter level,
            BlockPos position,
            CallbackInfoReturnable<BlockPathTypes> callback) {
        if (level.getBlockState(position).getBlock() instanceof LabTable
                || level.getBlockState(position.below()).getBlock()
                        instanceof LabTable) {
            callback.setReturnValue(BlockPathTypes.BLOCKED);
        }
    }
}
