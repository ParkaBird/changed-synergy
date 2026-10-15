package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.block.LabTable;
import net.ltxprogrammer.changed.block.AbstractLabDoor;
import net.ltxprogrammer.changed.block.AbstractLargeLabDoor;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** A table is too high to step onto, so keep land paths off its top cell. */
@Mixin(WalkNodeEvaluator.class)
public abstract class LabTablePathfindingMixin extends NodeEvaluator {
    @Inject(method = {"getBlockPathType(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;",
            "m_8086_(Lnet/minecraft/world/level/BlockGetter;III)Lnet/minecraft/world/level/pathfinder/BlockPathTypes;"},
            at = @At("HEAD"), cancellable = true, remap = false)
    private void changedSynergy$recognizePoweredLabDoors(BlockGetter level, int x, int y, int z,
            CallbackInfoReturnable<BlockPathTypes> callback) {
        if (!(mob instanceof ChangedEntity) || !canPassDoors()) return;
        BlockPos pos = new BlockPos(x, y, z);
        var state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof AbstractLabDoor)
                && !(state.getBlock() instanceof AbstractLargeLabDoor)) return;
        // Forge's raw path query passes null for the walker, making even powered
        // automatic doors look like locked iron doors. Use Changed's own access check.
        if (state.getBlockPathType(level, pos, mob) == BlockPathTypes.WALKABLE_DOOR)
            callback.setReturnValue(BlockPathTypes.WALKABLE_DOOR);
    }

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
