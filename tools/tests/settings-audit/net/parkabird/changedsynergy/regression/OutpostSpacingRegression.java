package net.parkabird.changedsynergy.regression;

import java.util.UUID;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;

/** Exercise both creation orders against real saved community claims. */
final class OutpostSpacingRegression {
    static void run(MinecraftServer server) {
        var level = server.overworld();
        var camps = PlayerOutpostData.get(server);
        var setting = ChangedSynergyConfig.COMMON.playerWildOutpostMinimumSpacing;
        int previousSpacing = setting.get();
        var owner = UUID.randomUUID();
        var other = UUID.randomUUID();
        var cache = new BlockPos(480, 90, 480);
        var creature = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        creature.setPos(cache.getX(), cache.getY(), cache.getZ());
        var marker = cache.offset(32, 0, 0);
        try {
            level.setBlock(cache, Blocks.BARREL.defaultBlockState(), 3);
            CreatureCommunityData.markCache(creature, cache);
            level.setBlock(marker, Blocks.CAMPFIRE.defaultBlockState(), 3);
            setting.set(48);
            check(camps.claim(level, owner, marker).equals("overlap"), "Default spacing rejects a nearby real wild cache");
            setting.set(16);
            check(camps.claim(level, owner, marker).equals("claimed"), "Lowered spacing applies when the player camp is created second");
            setting.set(128);
            var distant = cache.offset(96, 0, 0);
            level.setBlock(distant, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(camps.claim(level, owner, distant).equals("overlap"), "Raised spacing rejects a new camp beyond the old hardcoded range");
            check(camps.byOwner(owner).orElseThrow().marker.equals(marker), "A rejected move preserves the existing home");
            check(camps.claim(level, owner, marker).equals("claimed"), "An existing home remains usable after spacing increases");

            setting.set(16);
            var inside = cache.offset(15, 0, 0);
            var boundary = cache.offset(16, 0, 0);
            level.setBlock(inside, Blocks.CAMPFIRE.defaultBlockState(), 3);
            level.setBlock(boundary, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(camps.claim(level, owner, inside).equals("overlap"), "A site below the configured minimum is rejected");
            check(camps.claim(level, owner, boundary).equals("claimed"), "The exact configured minimum is allowed");
            check(!PlayerOutpostData.isAreaClaimed(level, cache, 16), "The reciprocal wild-creation check agrees at the exact boundary");
            var raised = cache.offset(0, 100, 0);
            level.setBlock(raised, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(camps.claim(level, owner, raised).equals("overlap"), "Vertical separation does not bypass the horizontal spacing rule");

            var nether = server.getLevel(net.minecraft.world.level.Level.NETHER);
            level.setBlock(inside, Blocks.CAMPFIRE.defaultBlockState(), 3);
            nether.setBlock(inside, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(camps.claim(nether, owner, inside).equals("claimed"), "A cache in another dimension does not block the same coordinates");
            check(camps.claim(level, owner, inside).equals("overlap"), "Returning to the cache's dimension still enforces its claim");

            level.setBlock(cache, Blocks.AIR.defaultBlockState(), 3);
            check(camps.claim(level, owner, inside).equals("claimed"), "Removing a loaded wild cache releases its area immediately");
            check(CreatureCommunityData.snapshotAtCache(level, cache).isEmpty(), "A destroyed cache does not retain a ghost claim");
            var nearPlayer = inside.offset(32, 0, 0);
            level.setBlock(nearPlayer, Blocks.CAMPFIRE.defaultBlockState(), 3);
            check(camps.claim(level, other, nearPlayer).equals("overlap"), "Lower wild spacing does not shrink the player/player buffer");
            level.setBlock(inside, Blocks.AIR.defaultBlockState(), 3);
            check(camps.claim(level, other, nearPlayer).equals("claimed"), "A destroyed loaded player marker does not block another camp");

            var unloaded = new BlockPos(-300000, 90, -300000);
            check(!level.hasChunkAt(unloaded), "The remote claim fixture is unloaded");
            CreatureCommunityData.markCache(creature, unloaded);
            int chunks = level.getChunkSource().getLoadedChunksCount();
            check(CreatureCommunityData.isCacheAreaClaimed(level, unloaded.offset(15, 0, 0), 16), "Unloaded valid claims remain protected at negative coordinates");
            check(!CreatureCommunityData.isCacheAreaClaimed(level, unloaded.offset(16, 0, 0), 16), "The exact boundary also works at negative coordinates");
            check(!CreatureCommunityData.isCacheAreaClaimed(level, cache, 256), "A distant unloaded record cannot block a local camp");
            check(level.getChunkSource().getLoadedChunksCount() == chunks, "Spacing checks do not load remote chunks");
            System.out.println("OUTPOST_SPACING_REGRESSION_PASS: configurable reciprocal spacing, boundaries, dimensions, existing homes and stale/unloaded claims");
        } finally {
            setting.set(previousSpacing);
            camps.disband(owner); camps.disband(other);
            CreatureCommunityData.clearCache(creature); CreatureCommunityData.detach(creature); creature.discard();
        }
    }

    private static void check(boolean passed, String reason) {
        if (!passed) throw new AssertionError(reason);
    }
}
