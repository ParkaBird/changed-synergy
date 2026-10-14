package net.parkabird.changedsynergy.regression;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.foxyas.changedaddon.entity.ai.goals.exp9.AoEThunderStrikeGoal;
import net.foxyas.changedaddon.entity.bosses.Experiment009Entity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.compat.addon.AddonLoadedBlockAccess;

/** Exercises the real remapped Addon goal, including both optional redirect sites. */
public final class AddonThunderScanRegression {
    private static int checks;

    public static void run(MinecraftServer server) {
        checks = 0;
        var level = server.overworld();
        var type = ForgeRegistries.ENTITY_TYPES.getValue(
                ResourceLocation.parse("changed_addon:experiment_009"));
        check(type != null, "Installed Addon registers normal Exp009");
        var creature = (Experiment009Entity) type.create(level);
        check(creature != null, "Real Exp009 can be constructed on a dedicated server");
        creature.setNoAi(true);
        var probe = new Probe(creature);
        Map<BlockPos, BlockState> originalBlocks = new LinkedHashMap<>();
        try {
            var spawn = level.getSharedSpawnPos();
            var center = new BlockPos((spawn.getX() >> 4) * 16 + 8,
                    level.getMaxBuildHeight() - 16, (spawn.getZ() >> 4) * 16 + 8);
            level.getChunkAt(center); // Intentional setup: the fixture owns this loaded chunk.
            for (BlockPos position : BlockPos.betweenClosed(center.offset(-1, -2, -1),
                    center.offset(1, 2, 1))) {
                BlockPos fixed = position.immutable();
                originalBlocks.put(fixed, level.getBlockState(fixed));
                level.setBlock(fixed, Blocks.AIR.defaultBlockState(), 2);
            }
            var copper = center.offset(-1, 0, 0);
            var rod = center.offset(1, 0, 0);
            level.setBlock(copper, Blocks.COPPER_BLOCK.defaultBlockState(), 2);
            level.setBlock(rod, Blocks.LIGHTNING_ROD.defaultBlockState(), 2);
            check(AddonLoadedBlockAccess.getBlockState(level, copper)
                    .equals(level.getBlockState(copper)), "Loaded block reads retain the native state");
            check(probe.conductors(level, center, 1).equals(List.of(copper, rod)),
                    "Real native conductor search retains loaded copper/rod results and ordering");
            check(AddonLoadedBlockAccess.getBlockState(level,
                    center.atY(level.getMaxBuildHeight())).isAir(), "Above-world read returns air");
            check(AddonLoadedBlockAccess.getBlockState(level,
                    center.atY(level.getMinBuildHeight() - 1)).isAir(), "Below-world read returns air");

            var distant = new BlockPos(1_000_008, 64, 1_000_008);
            assertUnloaded(level, distant);
            int loadedBefore = level.getChunkSource().getLoadedChunksCount();
            check(AddonLoadedBlockAccess.getBlockState(level, distant).isAir(),
                    "Unloaded block read returns air");
            check(probe.conductors(level, distant, 16).isEmpty(),
                    "Actual Addon radius-16 search ignores unloaded conductors");
            assertUnloaded(level, distant);
            check(level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                    "Conductive search does not load or generate chunks");

            var stormCenter = distant.offset(1_000_000, 0, 1_000_000);
            assertUnloaded(level, stormCenter);
            creature.setPos(stormCenter.getX() + 0.5,
                    stormCenter.getY(), stormCenter.getZ() + 0.5);
            creature.getRandom().setSeed(42L);
            loadedBefore = level.getChunkSource().getLoadedChunksCount();
            probe.storm();
            check(probe.strikes == 0, "Unloaded terrain cannot become a lightning strike surface");
            assertUnloaded(level, stormCenter);
            check(level.getChunkSource().getLoadedChunksCount() == loadedBefore,
                    "Actual AoE terrain search does not load or generate chunks");
            System.out.println("ADDON_THUNDER_SCAN_REGRESSION_PASS: " + checks + " checks");
        } finally {
            originalBlocks.forEach((position, state) -> level.setBlock(position, state, 2));
            creature.getNavigation().stop();
            creature.discard();
        }
    }

    private static void assertUnloaded(net.minecraft.server.level.ServerLevel level, BlockPos center) {
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                check(level.getChunkSource().getChunkNow((center.getX() >> 4) + x,
                        (center.getZ() >> 4) + z) == null, "Remote fixture chunks stay unloaded");
            }
        }
    }

    private static final class Probe extends AoEThunderStrikeGoal {
        private int strikes;

        private Probe(Experiment009Entity creature) {
            super(creature, ConstantInt.of(0), ConstantInt.of(4), 1.5, 200);
        }

        private List<BlockPos> conductors(Level level, BlockPos center, int radius) {
            return super.findConductiveBlocks(level, center, radius);
        }

        private void storm() {
            super.thunderStorm();
        }

        @Override
        public void spawnThunderBolt(BlockPos position) {
            // Verify terrain selection without spawning lightning or damaging the world.
            strikes++;
        }
    }

    private static void check(boolean passed, String description) {
        if (!passed) throw new AssertionError(description);
        checks++;
    }
}
