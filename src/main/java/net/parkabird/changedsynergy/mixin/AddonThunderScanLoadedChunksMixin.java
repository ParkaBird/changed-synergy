package net.parkabird.changedsynergy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.parkabird.changedsynergy.compat.addon.AddonLoadedBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Random AoE strikes must not generate terrain while searching for a conductor. */
@Pseudo
@Mixin(targets = "net.foxyas.changedaddon.entity.ai.goals.exp9.AoEThunderStrikeGoal", remap = false)
public abstract class AddonThunderScanLoadedChunksMixin {
    @Redirect(
            method = {"thunderStorm", "findConductiveBlocks"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/Level;getBlockState(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 0,
            remap = false)
    private BlockState changedSynergy$readMappedLoadedBlock(Level level, BlockPos position) {
        return AddonLoadedBlockAccess.getBlockState(level, position);
    }

    @Redirect(
            method = {"thunderStorm", "findConductiveBlocks"},
            at = @At(value = "INVOKE", target =
                    "Lnet/minecraft/world/level/Level;m_8055_(Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/state/BlockState;"),
            require = 0,
            remap = false)
    private BlockState changedSynergy$readSrgLoadedBlock(Level level, BlockPos position) {
        return AddonLoadedBlockAccess.getBlockState(level, position);
    }
}
