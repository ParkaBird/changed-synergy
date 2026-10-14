package net.parkabird.changedsynergy.compat.addon;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Block reads for Addon's server-only lightning scans, without chunk tickets. */
public final class AddonLoadedBlockAccess {
    private AddonLoadedBlockAccess() {
    }

    public static BlockState getBlockState(Level level, BlockPos position) {
        if (!(level instanceof ServerLevel server) || level.isOutsideBuildHeight(position)) {
            return Blocks.AIR.defaultBlockState();
        }
        var chunk = server.getChunkSource().getChunkNow(
                position.getX() >> 4, position.getZ() >> 4);
        return chunk == null
                ? Blocks.AIR.defaultBlockState()
                : chunk.getBlockState(position);
    }
}
