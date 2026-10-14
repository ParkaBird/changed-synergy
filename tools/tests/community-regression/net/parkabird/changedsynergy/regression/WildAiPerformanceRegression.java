package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.CreatureComfortGoal;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;

/** Actual server entities/blocks; counting navigation makes pathological searches reproducible. */
final class WildAiPerformanceRegression {
    private static int checks;

    static void run(MinecraftServer server) throws Exception {
        boolean success = true;
        for (Check action : List.<Check>of(() -> guards(server), () -> comfort(server),
                () -> fishing(server), () -> glowBerries(server), () -> blockedFollowing(server), () -> unloaded(server))) {
            try { action.run(); }
            catch (Throwable failure) { success = false; failure.printStackTrace(); }
        }
        if (!success) throw new AssertionError("Wild AI performance regression failed");
        System.out.println("WILD_AI_PERFORMANCE_REGRESSION_PASS: " + checks + " checks");
    }

    @SuppressWarnings("unchecked")
    private static void guards(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos cache = level.getSharedSpawnPos().above(65);
        BlockState old = level.getBlockState(cache);
        var data = CreatureCommunityData.get(server);
        var field = CreatureCommunityData.class.getDeclaredField("communities");
        field.setAccessible(true);
        var original = (Map<UUID, Object>) field.get(data);
        var records = new CountedCommunities(); records.putAll(original);
        List<ChangedEntity> guards = new ArrayList<>();
        try {
            level.setBlock(cache, Blocks.BARREL.defaultBlockState(), 2);
            for (int i = 0; i < 48; i++) {
                ChangedEntity guard = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                guard.setPos(cache.getX() + (i % 8), cache.getY(), cache.getZ() + (i / 8));
                CreatureLifeMemory.setRole(guard, CreatureLifeMemory.GroupRole.GUARD);
                level.addFreshEntity(guard); guards.add(guard);
                CreatureCommunityData.markCache(guard, cache);
            }
            records.clear(); records.putAll(original); field.set(data, records);
            Method stationed = CreatureCacheGuardService.class.getDeclaredMethod("stationedGuards", ServerLevel.class, BlockPos.class);
            stationed.setAccessible(true);
            long started = System.nanoTime();
            var selected = (List<ChangedEntity>) stationed.invoke(null, level, cache);
            System.out.printf(java.util.Locale.ROOT, "WILD_GUARD_SELECTION_SAMPLE: guards=%d communityScans=%d ms=%.3f%n",
                    guards.size(), records.scans, (System.nanoTime() - started) / 1_000_000.0);
            check(selected.equals(guards.stream().sorted(Comparator.comparing(ChangedEntity::getUUID)).limit(3).toList()),
                    "Crowded cache keeps exactly the same three UUID-stable guards");
            check(records.scans <= guards.size() * 2, "Guard selection scans communities linearly, without a nested entity query");
            for (ChangedEntity guard : guards) check(CreatureCacheGuardService.patrolCache(guard).isPresent() == selected.contains(guard),
                    "Individual patrol eligibility matches the shared guard cap");
            var player = new net.minecraft.server.level.ServerPlayer(server, level,
                    new com.mojang.authlib.GameProfile(UUID.randomUUID(), "CacheGuardFixture"));
            ChangedEntity departing = guards.get(0);
            CreatureCacheGuardService.begin(departing, player, cache);
            check(CreatureCacheGuardService.assignment(departing).isPresent(), "Intrusion creates a live defense assignment");
            departing.discard();
            var assignments = CreatureCacheGuardService.class.getDeclaredField("ASSIGNMENTS"); assignments.setAccessible(true);
            check(!((Map<?, ?>) assignments.get(null)).containsKey(departing.getUUID()),
                    "Removing a guard clears its temporary defense order immediately");
        } finally {
            field.set(data, original);
            guards.forEach(ChangedEntity::discard); level.setBlock(cache, old, 2);
        }
    }

    private static void comfort(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        BlockPos origin = level.getSharedSpawnPos().above(70);
        mob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var old = new LinkedHashMap<BlockPos, BlockState>();
        var navigation = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21344_"); navigation.setAccessible(true);
        Object previous = navigation.get(mob);
        var probe = new ProbeNavigation(mob); navigation.set(mob, probe);
        try {
            List<BlockPos> targets = new ArrayList<>();
            for (int x = -6; x <= 6; x++) for (int z = -6; z <= 6 && targets.size() < 40; z++) {
                BlockPos pos = origin.offset(x, 0, z); old.put(pos, level.getBlockState(pos));
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 2); targets.add(pos);
            }
            targets.sort(Comparator.comparingDouble(origin::distSqr));
            probe.reachable = targets.get(30);
            var goal = new CreatureComfortGoal(mob);
            Class<?> mode = Class.forName(CreatureComfortGoal.class.getName() + "$Mode");
            var select = CreatureComfortGoal.class.getDeclaredMethod("selectReachable", mode, Predicate.class);
            select.setAccessible(true);
            Object rest = java.util.Arrays.stream(mode.getEnumConstants()).filter(v -> v.toString().equals("REST")).findFirst().orElseThrow();
            Predicate<BlockState> stone = state -> state.is(Blocks.STONE);
            boolean found = (boolean) select.invoke(goal, rest, stone);
            System.out.println("WILD_COMFORT_SEARCH_SAMPLE: firstPassProbes=" + probe.attempts);
            check(!found && probe.attempts == 16, "Unreachable nearby rest props use at most sixteen paths per decision");
            int first = probe.attempts;
            check((boolean) select.invoke(goal, rest, stone) && probe.attempts - first <= 16,
                    "Later rest candidates remain reachable on the next decision");
            var target = CreatureComfortGoal.class.getDeclaredField("target"); target.setAccessible(true);
            check(probe.reachable.equals(target.get(goal)), "Rest search selects the actual reachable candidate");
        } finally {
            navigation.set(mob, previous); old.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
    }

    private static void fishing(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        BlockPos origin = level.getSharedSpawnPos().above(75);
        mob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var old = new LinkedHashMap<BlockPos, BlockState>();
        var navigation = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21344_"); navigation.setAccessible(true);
        Object previous = navigation.get(mob);
        var probe = new ProbeNavigation(mob); navigation.set(mob, probe);
        try {
            for (int x = -12; x <= 12; x++) for (int z = -12; z <= 12; z++) {
                BlockPos pos = origin.offset(x, -1, z); old.put(pos, level.getBlockState(pos));
                boolean water = Math.abs(x) >= 5 && Math.abs(x) % 2 == 1;
                level.setBlock(pos, (water ? Blocks.WATER : Blocks.STONE).defaultBlockState(), 2);
            }
            check(CreatureSettlementService.findCompanionFishingSite(mob, 12, 0).isEmpty(),
                    "An inaccessible bank is not accepted as a fishing destination");
            System.out.println("WILD_FISHING_SEARCH_SAMPLE: firstPassProbes=" + probe.attempts);
            check(probe.attempts == 16, "Fishing bounds failed path probes per decision");
            int attempts = probe.attempts;
            check(CreatureSettlementService.findCompanionFishingSite(mob, 12, 0).isEmpty()
                            && probe.attempts - attempts == 16 && probe.targets.stream().distinct().count() > 16,
                    "Fishing resumes later banks instead of repeating the same blocked ones");
            probe.reachable = probe.targets.get(probe.targets.size() - 1);
            boolean found = false;
            for (int i = 0; i < 80 && !found; i++) {
                int before = probe.attempts;
                found = CreatureSettlementService.findCompanionFishingSite(mob, 12, 0).isPresent();
                if (probe.attempts - before > 16) throw new AssertionError("Fishing pass exceeded its budget");
            }
            check(found, "Fishing eventually revisits and finds a usable bank after its route recovers");
        } finally {
            navigation.set(mob, previous); old.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
    }

    private static void unloaded(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        int coordinate = 200000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(100000) * 16;
        BlockPos distant = new BlockPos(coordinate, 200, coordinate);
        check(!level.hasChunkAt(distant), "Dry-stand fixture starts in unloaded terrain");
        var dry = CreatureSettlementService.class.getDeclaredMethod("isDryStand", ServerLevel.class, BlockPos.class);
        dry.setAccessible(true); dry.invoke(null, level, distant);
        check(!level.hasChunkAt(distant), "Testing a dry stand does not load or generate distant terrain");
        BlockPos edge = distant.offset(16000, 0, 16000);
        level.getChunk(edge);
        BlockPos neighbor = edge.west();
        check(!level.hasChunkAt(neighbor), "Comfort fixture starts next to an unloaded chunk");
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        mob.setPos(edge.getX() + .5, edge.getY(), edge.getZ() + .5);
        Class<?> mode = Class.forName(CreatureComfortGoal.class.getName() + "$Mode");
        var select = CreatureComfortGoal.class.getDeclaredMethod("selectReachable", mode, Predicate.class);
        select.setAccessible(true);
        select.invoke(new CreatureComfortGoal(mob), mode.getEnumConstants()[1], (Predicate<BlockState>) state -> false);
        check(!level.hasChunkAt(neighbor), "Ambient comfort scanning respects unloaded chunk borders");
        var player = new net.minecraft.server.level.ServerPlayer(server, level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "ShoreBorderFixture"));
        player.setPos(edge.getX() + .5, edge.getY(), edge.getZ() + .5);
        net.parkabird.changedsynergy.ai.BondedTeleportSafety.findShoreApproaches(level, mob, player, 8, 5);
        check(!level.hasChunkAt(neighbor), "Companion shore searches do not generate adjacent terrain");
    }

    private static void glowBerries(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        ChangedEntity mob = (ChangedEntity) net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getValue(
                new net.minecraft.resources.ResourceLocation("changed", "latex_kobold")).create(level);
        BlockPos origin = level.getSharedSpawnPos().above(90);
        mob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        mob.getPersistentData().putString("ChangedSynergyLightReputationGroup", "cave");
        var old = new LinkedHashMap<BlockPos, BlockState>();
        var navigation = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21344_"); navigation.setAccessible(true);
        Object previous = navigation.get(mob);
        var probe = new ProbeNavigation(mob); navigation.set(mob, probe);
        try {
            for (int x = -9; x <= 9; x++) for (int z = -9; z <= 9; z++) {
                BlockPos floor = origin.offset(x, -1, z); old.put(floor, level.getBlockState(floor));
                level.setBlock(floor, (Math.abs(x) >= 4 || Math.abs(z) >= 4 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
                if (x % 3 == 0 && z % 3 == 0) {
                    BlockPos berries = origin.offset(x, 4, z); old.put(berries, level.getBlockState(berries));
                    level.setBlock(berries, Blocks.CAVE_VINES.defaultBlockState().setValue(net.minecraft.world.level.block.CaveVines.BERRIES, true), 2);
                }
            }
            check(CreatureSettlementService.findGlowBerrySite(mob, 12, 6, 2).isEmpty() && probe.attempts == 16,
                    "Cave berry search shares sixteen path probes across all vines");
            int first = probe.attempts;
            check(CreatureSettlementService.findGlowBerrySite(mob, 12, 6, 2).isEmpty() && probe.attempts - first == 16
                            && probe.targets.stream().distinct().count() == 32,
                    "Cave berry search continues to new stands without duplicate paths");
            probe.reachable = probe.targets.get(20);
            boolean found = false;
            for (int i = 0; i < 60 && !found; i++) found = CreatureSettlementService.findGlowBerrySite(mob, 12, 6, 2).isPresent();
            check(found, "Cave berry gathering recovers when a later stand becomes reachable");
        } finally {
            navigation.set(mob, previous); old.forEach((pos, state) -> level.setBlock(pos, state, 2));
        }
    }

    private static final class CountedCommunities extends LinkedHashMap<UUID, Object> {
        int scans;
        @Override public java.util.Collection<Object> values() { scans++; return super.values(); }
    }
    private static final class ProbeNavigation extends GroundPathNavigation {
        int attempts;
        int moves;
        BlockPos reachable;
        final List<BlockPos> targets = new ArrayList<>();
        ProbeNavigation(ChangedEntity mob) { super(mob, mob.level()); }
        @Override public Path createPath(BlockPos pos, int accuracy) {
            attempts++; targets.add(pos.immutable());
            return pos.equals(reachable) ? new Path(List.of(new Node(pos.getX(), pos.getY(), pos.getZ())), pos, true) : null;
        }
        @Override public boolean moveTo(double x, double y, double z, double speed) { moves++; return false; }
        @Override public boolean moveTo(net.minecraft.world.entity.Entity target, double speed) { moves++; return false; }
    }

    private static void blockedFollowing(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        BlockPos origin = level.getSharedSpawnPos().above(130);
        mob.setPos(origin.getX() + .5, origin.getY(), origin.getZ() + .5);
        var player = new net.minecraft.server.level.ServerPlayer(server, level,
                new com.mojang.authlib.GameProfile(UUID.randomUUID(), "BlockedFollowFixture"));
        player.connection = new net.minecraft.server.network.ServerGamePacketListenerImpl(server,
                new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND), player);
        player.setPos(mob.getX() + 18, mob.getY(), mob.getZ());
        var navigation = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findField(Mob.class, "f_21344_"); navigation.setAccessible(true);
        Object previous = navigation.get(mob);
        var probe = new ProbeNavigation(mob); navigation.set(mob, probe);
        try {
            for (net.minecraft.world.entity.ai.goal.Goal goal : List.of(
                    new net.parkabird.changedsynergy.ai.BondedFollowGoal(mob),
                    new net.parkabird.changedsynergy.ai.SocialFollowGoal(mob))) {
                var owner = goal.getClass().getDeclaredField("player"); owner.setAccessible(true); owner.set(goal, player);
                probe.moves = 0;
                for (int tick = 0; tick < 40; tick++) goal.tick();
                check(probe.moves == 8, goal.getClass().getSimpleName() + ": failed routes respect the five-tick follow retry");
            }
            probe.moves = 0;
            net.parkabird.changedsynergy.ai.HuntMemory.investigate(mob, player, mob.position().add(12, 0, 0), level.getGameTime() + 200);
            var search = new net.parkabird.changedsynergy.ai.SmartSearchGoal(mob); search.start();
            for (int tick = 0; tick < 40; tick++) search.tick();
            check(probe.moves == 2, "Blocked hunt investigation retries twice in forty ticks");
            net.parkabird.changedsynergy.ai.HuntMemory.clear(mob);
        } finally { navigation.set(mob, previous); }
    }
    private interface Check { void run() throws Exception; }
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++; System.out.println("WILD_AI_PASS: " + message);
    }
}
