package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.HashSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.ai.CreatureRoutineGoal;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints;

/** Public off/on behavior plus actual bounded placement calls, in the private world. */
final class WildOutpostRegression {
    private static int checks;

    @SuppressWarnings("unchecked")
    static void run(MinecraftServer server) throws Exception {
        var level = server.overworld();
        var first = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var second = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        var center = level.getSharedSpawnPos().above(40);
        var cache = center.offset(1, 0, 0);
        var oldBlock = level.getBlockState(cache);
        boolean enabled = ChangedSynergyConfig.COMMON.creatureOutposts.get();
        var gateField = CreatureSettlementService.class.getDeclaredField("NEXT_OUTPOST_SEARCH");
        gateField.setAccessible(true);
        var gates = (Map<?, ?>)gateField.get(null);
        Object oldGate = gates.remove(level);
        try {
            for (var creature : new ChangedEntity[]{first, second}) {
                creature.setPos(center.getX(), center.getY(), center.getZ()); creature.setNoAi(true);
                level.addFreshEntity(creature);
            }
            ChangedSynergyConfig.COMMON.creatureOutposts.set(false);
            check(CreatureSettlementService.ensureCachePosition(first).isEmpty()
                            && !first.getPersistentData().contains("ChangedSynergyNextOutpostSearch"),
                    "Off prevents new wild construction before searches or world changes");
            level.setBlock(cache, Blocks.BARREL.defaultBlockState(), 2);
            CreatureCommunityData.markCache(first, cache);
            check(CreatureSettlementService.ensureCachePosition(first).orElseThrow().equals(cache),
                    "Off preserves an existing wild cache and its delivery destination");
            CreatureCommunityData.clearCache(first);
            // Old decoration may be preserved; this test only inspects new construction admission.
            ChangedSynergyConfig.COMMON.creatureOutposts.set(true);
            CreatureSettlementService.ensureCachePosition(first);
            CreatureSettlementService.ensureCachePosition(second);
            check(first.getPersistentData().contains("ChangedSynergyNextOutpostSearch")
                            && !second.getPersistentData().contains("ChangedSynergyNextOutpostSearch"),
                    "Only one unbound creature receives a construction search in the same level window");
            long next = first.getPersistentData().getLong("ChangedSynergyNextOutpostSearch");
            CreatureSettlementService.ensureCachePosition(first);
            check(first.getPersistentData().getLong("ChangedSynergyNextOutpostSearch") == next,
                    "A failed placement is not immediately searched again");

            Class<?> budgetType = Class.forName(CreatureSettlementService.class.getName() + "$PlacementSearch");
            var constructor = budgetType.getDeclaredConstructor(ChangedEntity.class); constructor.setAccessible(true);
            Object budget = constructor.newInstance(second);
            Method slice = budgetType.getDeclaredMethod("slice", String.class, int.class); slice.setAccessible(true);
            var remaining = budgetType.getDeclaredField("remaining"); remaining.setAccessible(true);
            for (int i = 0; i < 4; i++) slice.invoke(budget, "stress", 10000);
            check(remaining.getInt(budget) == 0 && second.getPersistentData().getCompound("ChangedSynergyOutpostSearch").getInt("stress") == 256,
                    "A search has at most 256 candidate slots and preserves its next cursor");
            Object nextBudget = constructor.newInstance(second);
            slice.invoke(nextBudget, "stress", 10000);
            check(second.getPersistentData().getCompound("ChangedSynergyOutpostSearch").getInt("stress") == 320,
                    "The next search continues rather than repeatedly scanning the same candidates");

            var column = CreatureSettlementService.class.getDeclaredMethod("placementColumn", BlockPos.class, int.class);
            column.setAccessible(true);
            var positions = new HashSet<BlockPos>();
            for (int index = 0; index < 1089; index++) {
                var pos = (BlockPos)column.invoke(null, BlockPos.ZERO, index);
                if (Math.abs(pos.getX()) > 16 || Math.abs(pos.getZ()) > 16 || !positions.add(pos))
                    throw new AssertionError("Placement ring has a duplicate or leaves its radius: " + index);
            }
            check(positions.size() == 1089, "Centre-first enumeration covers each candidate exactly once");
            var anchor = CreatureSettlementService.class.getDeclaredMethod("findLoadedAquaticAnchor",
                    net.minecraft.server.level.ServerLevel.class, BlockPos.class);
            anchor.setAccessible(true);
            int loaded = level.getChunkSource().getLoadedChunksCount();
            check(anchor.invoke(null, level, new BlockPos(250000, 64, 250000)) == null
                            && level.getChunkSource().getLoadedChunksCount() == loaded,
                    "Aquatic-anchor search in distant terrain does not load or generate chunks");
            var blueprint = CreatureSettlementBlueprints.INSTANCE.forCreature(second).orElseThrow();
            var indoor = CreatureSettlementService.class.getDeclaredMethod("findInsideStructurePlacement",
                    net.minecraft.server.level.ServerLevel.class, ChangedEntity.class, BoundingBox.class,
                    CreatureSettlementBlueprints.Blueprint.class, budgetType);
            indoor.setAccessible(true);
            Object indoorBudget = constructor.newInstance(second);
            long started = System.nanoTime();
            indoor.invoke(null, level, second, new BoundingBox(center.getX() - 50, 160, center.getZ() - 50,
                    center.getX() + 50, 250, center.getZ() + 50), blueprint, indoorBudget);
            check(remaining.getInt(indoorBudget) == 192, "A real large-ruin placement checks only its 64-cell slice");
            System.out.printf(java.util.Locale.ROOT, "WILD_OUTPOST_SEARCH_SAMPLE: bounded large ruin %.3f ms%n", (System.nanoTime() - started) / 1_000_000.0D);
            checkLargeClaimSnapshot(server, second, budgetType, constructor);

            var navigation = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(net.minecraft.world.entity.Mob.class, "f_21344_");
            navigation.setAccessible(true);
            Object originalNavigation = navigation.get(second);
            var failed = new FailedNavigation(second);
            try {
                navigation.set(second, failed);
                var routine = new CreatureRoutineGoal(second);
                for (var entry : Map.<String, Object>of("state", CreatureLifeMemory.RoutineState.DELIVERING,
                        "destination", second.position().add(15, 0, 0), "remainingTicks", 100).entrySet()) {
                    var field = CreatureRoutineGoal.class.getDeclaredField(entry.getKey());
                    field.setAccessible(true); field.set(routine, entry.getValue());
                }
                for (int tick = 0; tick < 40; tick++) routine.tick();
                check(failed.attempts == 3, "A failed delivery route retries three times in 40 ticks instead of every tick");
            } finally { navigation.set(second, originalNavigation); }
            System.out.println("WILD_OUTPOST_REGRESSION_PASS: " + checks + " checks");
        } finally {
            ChangedSynergyConfig.COMMON.creatureOutposts.set(enabled);
            ((Map<Object, Object>)gates).remove(level);
            if (oldGate != null) ((Map<Object, Object>)gates).put(level, oldGate);
            first.discard(); second.discard(); level.setBlock(cache, oldBlock, 2);
        }
    }

    @SuppressWarnings("unchecked")
    private static void checkLargeClaimSnapshot(MinecraftServer server, ChangedEntity creature,
            Class<?> budgetType, java.lang.reflect.Constructor<?> constructor) throws Exception {
        var level = server.overworld();
        var data = CreatureCommunityData.get(server);
        var field = CreatureCommunityData.class.getDeclaredField("communities"); field.setAccessible(true);
        var original = (Map<Object, Object>)field.get(data);
        var sample = data.save(new CompoundTag()).getList("Records", net.minecraft.nbt.Tag.TAG_COMPOUND).getCompound(0);
        var communityType = Class.forName(CreatureCommunityData.class.getName() + "$Community");
        var load = communityType.getDeclaredMethod("load", CompoundTag.class); load.setAccessible(true);
        var records = new CountedCommunities(); records.putAll(original);
        BlockPos unloaded = new BlockPos(300000, 90, 300000);
        for (int index = 0; index < 4096; index++) {
            var id = java.util.UUID.randomUUID();
            var tag = sample.copy(); tag.putUUID("Id", id);
            tag.putLong("Cache", unloaded.offset(index * 16, 0, 0).asLong());
            records.put(id, load.invoke(null, tag));
        }
        var occupied = CreatureSettlementService.class.getDeclaredMethod("isSettlementAreaOccupied",
                net.minecraft.server.level.ServerLevel.class, BlockPos.class, budgetType);
        occupied.setAccessible(true);
        int chunks = level.getChunkSource().getLoadedChunksCount();
        try {
            field.set(data, records);
            Object search = constructor.newInstance(creature);
            long start = System.nanoTime();
            for (int cell = 0; cell < 256; cell++) {
                if ((boolean)occupied.invoke(null, level, new BlockPos(200000 + cell, 90, 200000), search))
                    throw new AssertionError("Distant communities occupy candidate " + cell);
            }
            check(true, "256 candidates reject distant community claims");
            check(records.scans == 1, "4096 community claims are read once per search, not once per candidate");
            check((boolean)occupied.invoke(null, level, unloaded, search), "An unloaded recorded cache still blocks overlapping construction");
            check(level.getChunkSource().getLoadedChunksCount() == chunks, "Claim indexing does not load distant chunks");
            System.out.printf(java.util.Locale.ROOT, "WILD_OUTPOST_MANY_CLAIMS_SAMPLE: 4096 records, 256 cells, %.3f ms%n",
                    (System.nanoTime() - start) / 1_000_000.0D);
        } finally { field.set(data, original); }
    }

    private static final class CountedCommunities extends java.util.LinkedHashMap<Object, Object> {
        int scans;
        @Override public java.util.Collection<Object> values() { scans++; return super.values(); }
    }

    private static final class FailedNavigation extends net.minecraft.world.entity.ai.navigation.GroundPathNavigation {
        int attempts;
        FailedNavigation(ChangedEntity creature) { super(creature, creature.level()); }
        @Override public boolean moveTo(double x, double y, double z, double speed) { attempts++; return false; }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++; System.out.println("WILD_OUTPOST_PASS: " + message);
    }
}
