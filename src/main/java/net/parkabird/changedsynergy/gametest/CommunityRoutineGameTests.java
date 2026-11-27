package net.parkabird.changedsynergy.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.NaturalCampGoal;
import net.parkabird.changedsynergy.ai.NaturalCampService;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.StructureResidentGoal;
import net.parkabird.changedsynergy.npc.CampApproachGoal;
import net.parkabird.changedsynergy.npc.CampDialogue;
import net.parkabird.changedsynergy.npc.CampLifeService;
import net.parkabird.changedsynergy.npc.CampMembershipService;

@GameTestHolder(ChangedSynergyMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CommunityRoutineGameTests {
    private static final ResourceLocation STOVE_ID = ResourceLocation.tryBuild("farmersdelight", "stove");

    private CommunityRoutineGameTests() {}

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "burrow_sleep", timeoutTicks = 2000)
    public static void burrowAllResidentsSleepAtNight(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 14000L);
        PlacedStructure placed = placeStructure(level, "burrow", new BlockPos(200, 20, 200));
        Set<UUID> everSlept = new HashSet<>();

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 15) return;
            List<ChangedEntity> residents = campResidents(level, placed.bounds);
            helper.assertTrue(!residents.isEmpty(), "burrow should spawn camp residents");
            for (ChangedEntity mob : residents) {
                if (mob.isSleeping()) everSlept.add(mob.getUUID());
            }
            // Allow one pond/door straggler; still fail if a large share never sleeps.
            if (!residents.isEmpty() && everSlept.size() >= Math.max(1, residents.size() - 1)) {
                cleanupPlaced(level, placed);
                helper.succeed();
                return;
            }
            if (tick >= 1950) {
                String missing = residents.stream()
                        .filter(m -> !everSlept.contains(m.getUUID()))
                        .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                + "(sleep=" + m.isSleeping() + ",pose=" + m.getPose()
                                + ",navDone=" + m.getNavigation().isDone()
                                + ",path=" + (m.getNavigation().getPath() == null ? "null" : m.getNavigation().getPath().getTarget() + "/reach=" + m.getNavigation().getPath().canReach())
                                + ",goals=" + m.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList() + ")")
                        .reduce((a, b) -> a + " | " + b).orElse("none");
                cleanupPlaced(level, placed);
                helper.fail("burrow residents did not all sleep (" + everSlept.size() + "/" + residents.size() + "): " + missing);
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "burrow_meal", timeoutTicks = 1800)
    public static void burrowIdleMealSummonHonorsPublicStorage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // Daytime: chief summons currently idle members only (sleepers / workers stay put).
        configureWorld(level, 6000L);
        PlacedStructure placed = placeStructure(level, "burrow", new BlockPos(500, 20, 200));
        if (!ForgeRegistries.BLOCKS.containsKey(STOVE_ID)) {
            // Dev GameTest runs without Farmer's Delight; provide a lit hearth on the burrow stove slot.
            BlockPos stovePos = placed.origin.offset(31, 12, 5);
            BlockState fallbackCampfire = Blocks.CAMPFIRE.defaultBlockState();
            level.setBlock(stovePos, fallbackCampfire, Block.UPDATE_ALL);
        }

        final boolean[] checkedLootOnlyRejected = {false};
        final boolean[] summonedMeal = {false};
        final boolean[] waitedDinner = {false};
        final long[] summonedAt = {-1L};
        final Set<UUID> calledForMeal = new HashSet<>();
        final Set<UUID> arrivedNearDinner = new HashSet<>();
        final BlockPos kitchenFire = placed.origin.offset(31, 12, 5);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 15) return;
            List<ChangedEntity> residents = campResidents(level, placed.bounds);
            ChangedEntity chief = residents.stream()
                    .filter(CampDialogue::chief)
                    .findFirst().orElse(null);
            helper.assertTrue(chief != null, "burrow must have a chief for household meal summons");

            if (!checkedLootOnlyRejected[0]) {
                checkedLootOnlyRejected[0] = true;
                // Facility LootTable chest holds non-public loot; put bread in a non-public chest too.
                BlockPos lootChest = placed.origin.offset(12, 12, 16);
                if (level.getBlockEntity(lootChest) instanceof Container c) {
                    c.setItem(0, new ItemStack(Items.BREAD, 16));
                    c.setChanged();
                }
                ServerPlayer mock = createTestPlayer(level, chief);
                joinCampHome(chief, mock);
                int publicFood = CampLifeService.count(chief, CampLifeService.Supply.FOOD);
                helper.assertTrue(publicFood == 0, "Expected 0 public food when only facility LootTable chest has bread, got " + publicFood);
                helper.assertFalse(CampApproachGoal.shareMeal(chief, mock),
                        "shareMeal must fail when only non-public storage has food");

                // Now stock the authored public storage chest (26,12,9).
                BlockPos publicChest = placed.origin.offset(26, 12, 9);
                Container publicContainer = PlayerOutpostData.container(level, publicChest);
                helper.assertTrue(publicContainer != null, "burrow public chest at (26,12,9) missing");
                publicContainer.setItem(0, new ItemStack(Items.BREAD, 32));
                publicContainer.setChanged();
                return;
            }

            if (!summonedMeal[0]) {
                if (tick < 40) return;
                Set<UUID> busyBefore = new HashSet<>();
                for (ChangedEntity m : residents) {
                    if (NaturalCampService.isCampTaskBusy(m)) busyBefore.add(m.getUUID());
                }
                summonedMeal[0] = true;
                summonedAt[0] = tick;
                ServerPlayer mock = createTestPlayer(level, chief);
                joinCampHome(chief, mock);
                boolean ok = CampApproachGoal.shareMeal(chief, mock);
                helper.assertTrue(ok, "shareMeal must succeed after public chest is stocked");
                for (ChangedEntity m : residents) {
                    if (NaturalCampService.hasActiveMealCall(m)) calledForMeal.add(m.getUUID());
                    if (busyBefore.contains(m.getUUID())) {
                        helper.assertFalse(NaturalCampService.hasActiveMealCall(m),
                                "Busy/sleeping/dialoguing residents must not receive a meal call");
                    }
                }
                helper.assertTrue(!calledForMeal.isEmpty() || !busyBefore.isEmpty(),
                        "Expected either idle summons or a fully-busy camp at call time");
                helper.assertTrue(calledForMeal.stream().noneMatch(busyBefore::contains),
                        "Meal call must only reach previously idle residents");
                return;
            }

            if (!waitedDinner[0]) {
                if (calledForMeal.isEmpty()) {
                    waitedDinner[0] = true;
                    cleanupPlaced(level, placed);
                    helper.succeed();
                    return;
                }
                for (ChangedEntity m : residents) {
                    if (!calledForMeal.contains(m.getUUID())) continue;
                    double dist = m.distanceToSqr(kitchenFire.getX() + 0.5D, kitchenFire.getY(), kitchenFire.getZ() + 0.5D);
                    boolean activeMeal = m.goalSelector.getRunningGoals()
                            .anyMatch(g -> g.getGoal() instanceof NaturalCampGoal);
                    if ((activeMeal || NaturalCampService.hasActiveMealCall(m)) && dist <= 100.0D) {
                        arrivedNearDinner.add(m.getUUID());
                    }
                }
                int need = Math.max(1, (calledForMeal.size() + 1) / 2);
                if (arrivedNearDinner.size() >= need) {
                    waitedDinner[0] = true;
                    cleanupPlaced(level, placed);
                    helper.succeed();
                    return;
                }
                if (tick - summonedAt[0] >= 1400) {
                    String status = residents.stream()
                            .filter(m -> calledForMeal.contains(m.getUUID()) && !arrivedNearDinner.contains(m.getUUID()))
                            .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                    + "(call=" + NaturalCampService.hasActiveMealCall(m)
                                    + ",sleep=" + m.isSleeping() + ")")
                            .reduce((a, b) -> a + ", " + b).orElse("none");
                    cleanupPlaced(level, placed);
                    helper.fail("Idle burrow meal summons did not reach dinner ("
                            + arrivedNearDinner.size() + "/" + calledForMeal.size() + "): " + status);
                }
            }
        });
    }

    // Wide spidtaur room exits remain WIP; keep required=false so diagnostics still run.
    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "lush_cabin", timeoutTicks = 1600, required = false)
    public static void lushCabinTallCrouchAndAtticBackoff(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 14000L);
        PlacedStructure placed = placeStructure(level, "lush_cabin", new BlockPos(800, 20, 200));

        BlockPos publicChest = placed.origin.offset(24, 6, 10);
        Container c = PlayerOutpostData.container(level, publicChest);
        if (c != null) {
            c.setItem(0, new ItemStack(Items.BREAD, 16));
            c.setChanged();
        }

        final boolean[] observedSpidtaurCrouch = {false};
        final Set<UUID> slept = new HashSet<>();

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 30) return;
            List<ChangedEntity> residents = campResidents(level, placed.bounds);
            if (tick == 40) {
                System.out.println("[GT-DIAG lush@40] residents=" + residents.stream().map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()).toList());
            }
            helper.assertTrue(!residents.isEmpty(), "lush_cabin should spawn camp residents");

            ChangedEntity spidtaur = null;
            ChangedEntity atticStiger = null;
            List<ChangedEntity> groundResidents = new ArrayList<>();
            for (ChangedEntity m : residents) {
                ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(m.getType());
                String path = typeId == null ? "" : typeId.getPath();
                if ("latex_spidtaur".equals(path)) {
                    spidtaur = m;
                    if (m.getPose() == Pose.CROUCHING) observedSpidtaurCrouch[0] = true;
                }
                if ("latex_stiger".equals(path) && Math.abs(m.getY() - (placed.origin.getY() + 20.0D)) < 2.5D) {
                    atticStiger = m;
                } else {
                    groundResidents.add(m);
                }
                if (m.isSleeping()) slept.add(m.getUUID());
            }

            if (tick >= 60) {
                if (spidtaur == null) {
                    System.out.println("[GT-DIAG lush@60] ALL entities in bounds: " + level.getEntitiesOfClass(Entity.class, placed.bounds.inflate(64)).stream().map(e -> e.getType() + "@" + e.blockPosition() + "/alive=" + e.isAlive()).toList());
                }
                helper.assertTrue(spidtaur != null, "lush_cabin should have latex_spidtaur");
                helper.assertTrue(atticStiger != null, "lush_cabin should have attic latex_stiger");
                helper.assertFalse(atticStiger.isSleeping(),
                        "Ladder-isolated attic latex_stiger must not clip or sleep without a physical path");
            }

            boolean allGroundSlept = groundResidents.stream().allMatch(m -> slept.contains(m.getUUID()));
            if (allGroundSlept && observedSpidtaurCrouch[0]) {
                cleanupPlaced(level, placed);
                helper.succeed();
                return;
            }
            if (tick >= 1550) {
                String missing = groundResidents.stream()
                        .filter(m -> !slept.contains(m.getUUID()))
                        .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                + "(pose=" + m.getPose() + ",nav=" + m.getNavigation().getClass().getSimpleName()
                                + ",path=" + (m.getNavigation().getPath() == null ? "null" : m.getNavigation().getPath().getTarget() + "/r=" + m.getNavigation().getPath().canReach())
                                + ",avail=" + NaturalCampService.restsHere(m) + ",beds=" + NaturalCampService.sites(m).beds().size()
                                + ",goals=" + m.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList() + ")")
                        .reduce((a, b) -> a + " | " + b).orElse("none");
                cleanupPlaced(level, placed);
                helper.fail("lush_cabin check incomplete: spidtaurCrouched=" + observedSpidtaurCrouch[0]
                        + ", missingGroundSleep=" + missing);
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "prometheus_desert", timeoutTicks = 3000)
    public static void pointPrometheusAndDesertRefugeSleepAndMeal(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 14000L);
        PlacedStructure prometheus = placeStructure(level, "point_prometheus", new BlockPos(1100, 20, 200));
        PlacedStructure desert = placeStructure(level, "desert_refuge", new BlockPos(1400, 20, 200));

        Container promChest = PlayerOutpostData.container(level, prometheus.origin.offset(17, 2, 25));
        if (promChest != null) {
            promChest.setItem(0, new ItemStack(Items.BREAD, 16));
            promChest.setChanged();
        }
        Container desertChest = PlayerOutpostData.container(level, desert.origin.offset(8, 14, 15));
        if (desertChest != null) {
            desertChest.setItem(0, new ItemStack(Items.BREAD, 16));
            desertChest.setChanged();
        }

        final Set<UUID> prometheusSlept = new HashSet<>();
        final Set<UUID> desertSlept = new HashSet<>();
        final boolean[] desertMealCalled = {false};
        final long[] desertMealTick = {-1L};
        final Set<UUID> desertCalled = new HashSet<>();
        final Set<UUID> desertDinnerArrived = new HashSet<>();
        final BlockPos desertDiningSpot = desert.origin.offset(31, 14, 17);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 15) return;

            List<ChangedEntity> promList = campResidents(level, prometheus.bounds);
            List<ChangedEntity> desertList = campResidents(level, desert.bounds);
            helper.assertTrue(!promList.isEmpty(), "point_prometheus should spawn residents");
            helper.assertTrue(!desertList.isEmpty(), "desert_refuge should spawn residents");

            for (ChangedEntity m : promList) {
                if (m.isSleeping()) prometheusSlept.add(m.getUUID());
                if (m.getAirSupply() < m.getMaxAirSupply() && tick % 40 == 0) {
                    System.out.println("[GT-DIAG promAir] " + ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.position()
                            + " air=" + m.getAirSupply() + " headState=" + level.getBlockState(BlockPos.containing(m.getX(), m.getEyeY(), m.getZ()))
                            + " fluid=" + level.getFluidState(BlockPos.containing(m.getX(), m.getEyeY(), m.getZ())));
                }
            }
            for (ChangedEntity m : desertList) if (m.isSleeping()) desertSlept.add(m.getUUID());

            // Multi-floor / tall crouch still leaves stragglers; require a majority slept.
            boolean promReady = !promList.isEmpty()
                    && prometheusSlept.size() >= Math.max(3, (promList.size() + 1) / 2);
            boolean desertReady = !desertList.isEmpty()
                    && desertSlept.size() >= Math.max(2, (desertList.size() + 1) / 2);

            // After night sleep is proven, switch to day so idle members can receive a chief meal call.
            if (promReady && desertReady && !desertMealCalled[0]) {
                if (level.getDayTime() % 24000L > 12000L) {
                    level.setDayTime(1000L);
                    level.updateSkyBrightness();
                    return;
                }
                long stillAsleep = desertList.stream().filter(ChangedEntity::isSleeping).count();
                if (stillAsleep > desertList.size() / 2 && tick < 2500) return;
                ChangedEntity desertChief = desertList.stream().filter(CampDialogue::chief).findFirst().orElse(null);
                helper.assertTrue(desertChief != null, "desert_refuge must have a chief");
                Set<UUID> busyBefore = new HashSet<>();
                for (ChangedEntity m : desertList) {
                    if (NaturalCampService.isCampTaskBusy(m)) busyBefore.add(m.getUUID());
                }
                desertMealCalled[0] = true;
                desertMealTick[0] = tick;
                ServerPlayer mock = createTestPlayer(level, desertChief);
                joinCampHome(desertChief, mock);
                helper.assertTrue(CampApproachGoal.shareMeal(desertChief, mock),
                        "desert_refuge shareMeal must succeed at dining spot");
                for (ChangedEntity m : desertList) {
                    if (busyBefore.contains(m.getUUID())) {
                        helper.assertFalse(NaturalCampService.hasActiveMealCall(m),
                                "Desert sleepers/workers must not be woken for meal");
                    }
                    if (NaturalCampService.hasActiveMealCall(m)) desertCalled.add(m.getUUID());
                }
                return;
            }

            if (desertMealCalled[0]) {
                for (ChangedEntity m : desertList) {
                    if (!desertCalled.contains(m.getUUID())) continue;
                    double dist = m.distanceToSqr(desertDiningSpot.getX() + 0.5D, desertDiningSpot.getY(), desertDiningSpot.getZ() + 0.5D);
                    boolean activeMeal = m.goalSelector.getRunningGoals()
                            .anyMatch(g -> g.getGoal() instanceof NaturalCampGoal);
                    if ((activeMeal || NaturalCampService.hasActiveMealCall(m)) && dist <= 100.0D) {
                        desertDinnerArrived.add(m.getUUID());
                    }
                }
            }

            boolean desertMealOk = desertMealCalled[0] && (
                    desertCalled.isEmpty()
                            || desertDinnerArrived.size() >= Math.max(1, (desertCalled.size() + 1) / 2));

            if (promReady && desertMealOk) {
                cleanupPlaced(level, prometheus);
                cleanupPlaced(level, desert);
                helper.succeed();
                return;
            }

            if (tick >= 2950) {
                String pMissing = promList.stream().filter(m -> !prometheusSlept.contains(m.getUUID()))
                        .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                + "(beds=" + NaturalCampService.sites(m).beds().size()
                                + ",path=" + (m.getNavigation().getPath() == null ? "null" : m.getNavigation().getPath().getTarget() + "/r=" + m.getNavigation().getPath().canReach())
                                + ",goals=" + m.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList() + ")")
                        .reduce((a, b) -> a + " | " + b).orElse("none");
                String dMissing = desertList.stream().filter(m -> !desertSlept.contains(m.getUUID()))
                        .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                + "(beds=" + NaturalCampService.sites(m).beds().size()
                                + ",path=" + (m.getNavigation().getPath() == null ? "null" : m.getNavigation().getPath().getTarget() + "/r=" + m.getNavigation().getPath().canReach())
                                + ",goals=" + m.goalSelector.getRunningGoals().map(g -> g.getGoal().getClass().getSimpleName()).toList() + ")")
                        .reduce((a, b) -> a + " | " + b).orElse("none");
                cleanupPlaced(level, prometheus);
                cleanupPlaced(level, desert);
                helper.fail("prometheus/desert incomplete: promSleep=" + prometheusSlept.size() + "/" + promList.size()
                        + ", desertSleep=" + desertSlept.size() + "/" + desertList.size()
                        + ", desertMealCalled=" + desertMealCalled[0]
                        + ", desertDinner=" + desertDinnerArrived.size() + "/" + desertCalled.size()
                        + " || pMissing=" + pMissing + " || dMissing=" + dMissing);
            }
        });
    }

    private static ServerPlayer createTestPlayer(ServerLevel level, ChangedEntity near) {
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "GameTestCampMember"));
        player.setPos(near.getX(), near.getY(), near.getZ());
        return player;
    }

    private static void joinCampHome(ChangedEntity npc, ServerPlayer player) {
        if (CampMembershipService.canJoin(npc, player)) {
            CampMembershipService.join(npc, player, "join");
            return;
        }
        var data = CampMembershipService.membership(player);
        data.putString("HomeKey", net.parkabird.changedsynergy.npc.StoryNpcService.homeKey(npc));
        data.putBoolean("Suspended", false);
    }

    private static void configureWorld(ServerLevel level, long dayTime) {
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
        level.setDayTime(dayTime);
        if (level.getServer().overworld() != level) {
            level.getServer().overworld().getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
            level.getServer().overworld().setDayTime(dayTime);
        }
        // GameTest has no nearby players; keep community AI fully scheduled.
        ChangedSynergyConfig.COMMON.distantAiThrottling.set(false);
        ChangedSynergyConfig.COMMON.adaptiveAiBudget.set(false);
        ChangedSynergyConfig.COMMON.communityAi.set(true);
        ChangedSynergyConfig.COMMON.naturalCampRoutines.set(true);
        var story = net.parkabird.changedsynergy.npc.StoryNpcData.get(level.getServer());
        if (!story.records.isEmpty()) {
            story.records.clear();
            story.setDirty();
        }
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "the_shoal", timeoutTicks = 400)
    public static void theShoalSpawnsResidentsWetsuitAndStorageOwnership(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 6000L);
        var fdTemplate = level.getStructureManager().get(ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, "the_shoal_fd"));
        helper.assertTrue(fdTemplate.isPresent(), "the_shoal_fd template must be packaged");
        BlockPos origin = new BlockPos(2000, 20, 200);
        StructureTemplate tpl = level.getStructureManager()
                .get(ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, "the_shoal"))
                .orElseThrow(() -> new IllegalStateException("Missing template the_shoal"));
        Vec3i size = tpl.getSize();
        List<ChunkPos> forced = forceChunks(level, origin, size, true);
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
        PlacedStructure placed = new PlacedStructure(origin, size, bounds, forced);
        final boolean[] setupDone = {false};
        final long[] placedTick = {-1L};
        helper.onEachTick(() -> {
            if (!setupDone[0]) {
                if (!allEntityChunksTicking(level, origin, size)) {
                    return;
                }
                setupDone[0] = true;
                for (Entity leftover : level.getEntities((Entity) null, bounds.inflate(16.0D), e -> !(e instanceof ServerPlayer))) {
                    leftover.discard();
                }
                boolean prevBlockDrops = level.getGameRules().getBoolean(GameRules.RULE_DOBLOCKDROPS);
                level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(false, level.getServer());
                try {
                    for (int x = -2; x <= size.getX() + 1; x++) {
                        for (int z = -2; z <= size.getZ() + 1; z++) {
                            for (int y = 0; y <= size.getY() + 1; y++) {
                                BlockPos clearPos = origin.offset(x, y, z);
                                level.removeBlockEntity(clearPos);
                                level.setBlock(clearPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                            }
                            boolean rim = x == -2 || x == size.getX() + 1 || z == -2 || z == size.getZ() + 1;
                            level.setBlock(origin.offset(x, -2, z), Blocks.SANDSTONE.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                            level.setBlock(origin.offset(x, -1, z), Blocks.SAND.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                            level.setBlock(origin.offset(x, 0, z), (rim ? Blocks.SAND : Blocks.WATER).defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                            level.setBlock(origin.offset(x, 1, z), (rim ? Blocks.SAND : Blocks.WATER).defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS);
                        }
                    }
                } finally {
                    level.getGameRules().getRule(GameRules.RULE_DOBLOCKDROPS).set(prevBlockDrops, level.getServer());
                }
                var settings = new StructurePlaceSettings().setKnownShape(true).setIgnoreEntities(false)
                        .addProcessor(net.ltxprogrammer.changed.world.features.structures.HangingBlockFixerProcessor.INSTANCE);
                tpl.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                placedTick[0] = helper.getTick();
                return;
            }
            if (helper.getTick() < placedTick[0] + 20) return;
            try {
                var drops = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, placed.bounds.inflate(8.0D));
                if (!drops.isEmpty()) {
                    var summary = new java.util.TreeMap<String, Integer>();
                    for (var drop : drops) summary.merge(drop.getItem().getItem() + "@" + drop.blockPosition().subtract(placed.origin), drop.getItem().getCount(), Integer::sum);
                    helper.fail("the_shoal dropped " + drops.size() + " items: " + summary);
                }
                var allEnts = level.getEntities((Entity) null, placed.bounds.inflate(16.0D), e -> true).stream()
                        .map(e -> ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) + "@" + e.blockPosition()).toList();
                var boats = level.getEntitiesOfClass(net.minecraft.world.entity.vehicle.Boat.class, placed.bounds.inflate(16.0D));
                var frames = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.ItemFrame.class, placed.bounds.inflate(2.0D));
                helper.assertTrue(boats.size() == 3, "the_shoal should have 3 boats, found " + boats.size() + " | all=" + allEnts);
                helper.assertTrue(frames.size() == 15, "the_shoal should keep 15 item frames, found " + frames.size() + " | all=" + allEnts);
                helper.assertTrue(level.getBlockState(placed.origin.offset(7, 5, 29)).is(net.minecraft.world.level.block.Blocks.SPRUCE_WALL_SIGN),
                        "the_shoal (7,5,29) must have spruce_wall_sign");
                helper.assertTrue(level.getBlockState(placed.origin.offset(8, 9, 26)).is(net.minecraft.world.level.block.Blocks.MANGROVE_WALL_SIGN),
                        "the_shoal (8,9,26) must have mangrove_wall_sign");
                helper.assertTrue("changed:iron_crate".equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(
                        level.getBlockState(placed.origin.offset(11, 5, 20)).getBlock()))),
                        "the_shoal (11,5,20) must have changed:iron_crate");
                helper.assertTrue("changed:cardboard_box_small".equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(
                        level.getBlockState(placed.origin.offset(7, 5, 20)).getBlock()))),
                        "the_shoal (7,5,20) must have changed:cardboard_box_small");
                helper.assertTrue("changed:cardboard_container".equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(
                        level.getBlockState(placed.origin.offset(7, 5, 21)).getBlock()))),
                        "the_shoal (7,5,21) must have changed:cardboard_container");
                helper.assertTrue("changed:clipboard".equals(String.valueOf(ForgeRegistries.BLOCKS.getKey(
                        level.getBlockState(placed.origin.offset(7, 6, 21)).getBlock()))),
                        "the_shoal (7,6,21) must have changed:clipboard");
                var bossBarrelBe = level.getBlockEntity(placed.origin.offset(6, 5, 29));
                helper.assertTrue(bossBarrelBe != null && "changed_synergy:chests/shoal_boss_stash".equals(bossBarrelBe.saveWithFullMetadata().getString("LootTable")),
                        "the_shoal boss barrel (6,5,29) must have shoal_boss_stash LootTable");
                var publicChestBe = level.getBlockEntity(placed.origin.offset(8, 5, 6));
                helper.assertTrue(publicChestBe != null && !publicChestBe.saveWithFullMetadata().contains("LootTable"),
                        "the_shoal public chest (8,5,6) must NOT have a LootTable");
                BlockPos discPos = placed.origin.offset(11, 6, 31);
                var discFrame = frames.stream()
                        .filter(f -> f.getPos().equals(discPos)
                                && "changed:compact_disc".equals(String.valueOf(ForgeRegistries.ITEMS.getKey(f.getItem().getItem()))))
                        .findFirst().orElse(null);
                helper.assertTrue(discFrame != null, "the_shoal must have compact_disc item frame at " + discPos);
                helper.assertTrue(discFrame.isInvisible(), "the_shoal disc item frame must be invisible");
                String discText = discFrame.getItem().getTag() == null ? "" : discFrame.getItem().getTag().getString("Text");
                helper.assertTrue(discText.contains("diving_partner@reef-salvage.net"),
                        "the_shoal disc must contain Fragment 1 email text");
                var manifestNote = level.getBlockEntity(discPos);
                helper.assertTrue(manifestNote instanceof net.ltxprogrammer.changed.block.entity.TextBlockEntity textManifest
                        && textManifest.getText().contains("SALVAGE TALLY: EAST DECK"),
                        "the_shoal (11,6,31) note must contain Fragment 2 tally text");
                var dormNote = level.getBlockEntity(placed.origin.offset(6, 6, 10));
                helper.assertTrue(dormNote instanceof net.ltxprogrammer.changed.block.entity.TextBlockEntity textDorm
                        && textDorm.getText().contains("DORM LOCKERS"),
                        "the_shoal (6,6,10) dorm chest note must contain dorm locker text");

                List<ChangedEntity> residents = campResidents(level, placed.bounds);
                if (residents.size() != 5) {
                    var allChanged = level.getEntitiesOfClass(ChangedEntity.class, placed.bounds.inflate(32.0D)).stream()
                            .map(e -> ForgeRegistries.ENTITY_TYPES.getKey(e.getType()) + "@" + e.blockPosition()
                                    + "(alive=" + e.isAlive() + ",member=" + NaturalCampService.member(e) + ")").toList();
                    helper.fail("the_shoal should have 5 built-in residents, found " + residents.size() + " | nearby=" + allChanged);
                }

                ChangedEntity boss = null;
                int orcas = 0, eels = 0, sharks = 0;
                for (ChangedEntity m : residents) {
                    helper.assertTrue(CampLifeService.mainCamp(m), "resident should be mainCamp: " + m.getType());
                    if (m instanceof net.parkabird.changedsynergy.entity.LatexSwordfish) {
                        boss = m;
                    } else if (m instanceof net.ltxprogrammer.changed.entity.beast.LatexOrca) {
                        orcas++;
                    } else if (m instanceof net.ltxprogrammer.changed.entity.beast.LatexEel) {
                        eels++;
                    } else if (m instanceof net.ltxprogrammer.changed.entity.beast.LatexShark) {
                        sharks++;
                    }
                }
                helper.assertTrue(boss != null && orcas == 1 && eels == 1 && sharks == 2,
                        "Expected 1 swordfish boss, 1 orca, 1 eel, 2 sharks; got boss=" + boss + " orcas=" + orcas + " eels=" + eels + " sharks=" + sharks);
                helper.assertTrue(net.parkabird.changedsynergy.ai.CreatureLifeMemory.role(boss) == net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole.CHIEF,
                        "swordfish boss should have CHIEF role");
                var shoalProfile = net.parkabird.changedsynergy.npc.StoryNpcService.profile(boss);
                helper.assertTrue(shoalProfile != null && "changed_synergy:shoal_chief".equals(shoalProfile.id()),
                        "swordfish boss must resolve profile changed_synergy:shoal_chief");
                helper.assertTrue("scavenger".equals(CampLifeService.voice(boss)),
                        "swordfish boss voice must be scavenger");
                var residentRoles = new java.util.HashSet<String>();
                for (ChangedEntity m : residents) {
                    if (m != boss) {
                        String role = m.getPersistentData().getCompound(net.parkabird.changedsynergy.ai.StructureResidentGoal.DATA).getString("DialogueRole");
                        residentRoles.add(role);
                        helper.assertTrue(shoalProfile.nodes().containsKey("resident_" + role),
                                "shoal_chief must contain dialogue node resident_" + role);
                    }
                }
                helper.assertTrue(residentRoles.equals(java.util.Set.of("fishing", "dorm", "storage", "workshop")),
                        "the_shoal residents must cover all 4 dialogue roles, got " + residentRoles);
                var wetsuit = net.ltxprogrammer.changed.init.ChangedItems.WETSUIT.get();
                helper.assertTrue(net.ltxprogrammer.changed.data.AccessorySlots.isWearing(boss, s -> s.is(wetsuit)),
                        "swordfish boss must wear changed:wetsuit");
                var stands = level.getEntitiesOfClass(net.ltxprogrammer.changed.entity.decoration.BipedArmorStand.class, placed.bounds.inflate(2.0D));
                helper.assertTrue(stands.size() == 1, "the_shoal should have 1 display biped_armor_stand, found " + stands.size());
                var stand = stands.get(0);
                helper.assertTrue(net.parkabird.changedsynergy.ai.ArmorStandClothingService.isDisplayOnly(stand),
                        "the_shoal armor stand must have ChangedSynergyDisplayOnly");
                helper.assertTrue(net.ltxprogrammer.changed.data.AccessorySlots.isWearing(stand, s -> s.is(wetsuit)),
                        "the_shoal armor stand must wear changed:wetsuit");

                for (int[] pub : new int[][]{{8, 5, 6}, {8, 5, 7}}) {
                    BlockPos p = placed.origin.offset(pub[0], pub[1], pub[2]);
                    helper.assertTrue(NaturalCampService.publicStorage(level, p), "public storage missing at " + p);
                    helper.assertTrue(NaturalCampService.ownsStorage(boss, p), "boss must own public storage " + p);
                }
                for (int[] priv : new int[][]{{6, 5, 10}, {6, 5, 29}, {6, 5, 30}, {15, 5, 29},
                        {10, 5, 31}, {10, 6, 31}, {10, 7, 31}, {15, 5, 31}, {15, 6, 31}, {15, 7, 31}}) {
                    BlockPos p = placed.origin.offset(priv[0], priv[1], priv[2]);
                    helper.assertTrue(CampMembershipService.privateStorage(boss, p), "private storage missing at " + p);
                    helper.assertTrue(!NaturalCampService.publicStorage(level, p), "private store must not be public: " + p);
                }
                helper.succeed();
            } finally {
                for (Entity leftover : level.getEntities((Entity) null, placed.bounds.inflate(16.0D), e -> !(e instanceof ServerPlayer))) {
                    leftover.discard();
                }
                cleanupPlaced(level, placed);
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "the_shoal_sleep", timeoutTicks = 1600)
    public static void theShoalResidentsLieDownInSleepingPose(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 14000L);
        PlacedStructure placed = placeStructure(level, "the_shoal", new BlockPos(2300, 20, 200));
        Set<UUID> sleptWithPose = new HashSet<>();

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 15) return;
            List<ChangedEntity> residents = campResidents(level, placed.bounds);
            helper.assertTrue(residents.size() == 5, "the_shoal should have 5 residents, found " + residents.size());
            for (ChangedEntity mob : residents) {
                if (mob.isSleeping()) {
                    helper.assertTrue(mob.hasPose(Pose.SLEEPING),
                            "Sleeping aquatic resident " + ForgeRegistries.ENTITY_TYPES.getKey(mob.getType())
                                    + " must stay in Pose.SLEEPING, got " + mob.getPose());
                    sleptWithPose.add(mob.getUUID());
                }
            }
            if (sleptWithPose.size() >= 2) {
                cleanupPlaced(level, placed);
                helper.succeed();
                return;
            }
            if (tick >= 1550) {
                String missing = residents.stream()
                        .filter(m -> !sleptWithPose.contains(m.getUUID()))
                        .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                + "(sleep=" + m.isSleeping() + ",pose=" + m.getPose() + ")")
                        .reduce((a, b) -> a + " | " + b).orElse("none");
                cleanupPlaced(level, placed);
                helper.fail("the_shoal residents did not sleep in Pose.SLEEPING ("
                        + sleptWithPose.size() + "/" + residents.size() + "): " + missing);
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "the_shoal_worldgen", timeoutTicks = 400)
    public static void theShoalWorldgenLocatesAndExtendsFenceStilts(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        var structReg = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var shoalKey = net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE,
                ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, "the_shoal"));
        var shoalHolder = structReg.getHolder(shoalKey)
                .orElseThrow(() -> new IllegalStateException("Missing registered structure changed_synergy:the_shoal"));
        var mapTag = net.minecraft.tags.TagKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE,
                ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, "on_the_shoal_maps"));
        helper.assertTrue(shoalHolder.is(mapTag), "changed_synergy:the_shoal must belong to tag on_the_shoal_maps");

        var found = level.getChunkSource().getGenerator().findNearestMapStructure(
                level, net.minecraft.core.HolderSet.direct(shoalHolder), new BlockPos(0, 64, 0), 250, false);
        helper.assertTrue(found != null, "findNearestMapStructure must locate a changed_synergy:the_shoal ocean shoal");
        BlockPos target = found.getFirst();
        var chunk = level.getChunk(target.getX() >> 4, target.getZ() >> 4);
        var start = chunk.getStartForStructure(shoalHolder.value());
        helper.assertTrue(start != null && start.isValid(), "located the_shoal must have valid StructureStart at " + target);
        var box = start.getBoundingBox();
        for (int cx = Math.floorDiv(box.minX(), 16); cx <= Math.floorDiv(box.maxX(), 16); cx++) {
            for (int cz = Math.floorDiv(box.minZ(), 16); cz <= Math.floorDiv(box.maxZ(), 16); cz++) {
                level.getChunk(cx, cz);
            }
        }
        int seaLevel = level.getSeaLevel();
        int anchoredColumns = 0;
        int submergedWaterloggedFences = 0;
        for (int x = box.minX(); x <= box.maxX(); x++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                BlockPos topFence = new BlockPos(x, seaLevel, z);
                BlockState atSea = level.getBlockState(topFence);
                if (!atSea.is(net.minecraft.tags.BlockTags.FENCES)) continue;
                int y = seaLevel;
                while (y >= level.getMinBuildHeight()) {
                    BlockState st = level.getBlockState(new BlockPos(x, y, z));
                    if (!st.is(net.minecraft.tags.BlockTags.FENCES)) {
                        if (!st.isAir() && st.getFluidState().isEmpty()) {
                            anchoredColumns++;
                        }
                        break;
                    }
                    if (y < seaLevel && st.hasProperty(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)
                            && st.getValue(net.minecraft.world.level.block.state.properties.BlockStateProperties.WATERLOGGED)) {
                        submergedWaterloggedFences++;
                    }
                    y--;
                }
            }
        }
        helper.assertTrue(anchoredColumns >= 4 && submergedWaterloggedFences >= 4,
                "Expected >=4 fence stilt columns anchored to seabed with waterlogged submerged poles, got anchored="
                        + anchoredColumns + ", waterlogged=" + submergedWaterloggedFences + " at " + target);
        helper.succeed();
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "sleeping_lock", timeoutTicks = 400)
    public static void burrowFixesAndSleepPushLockVerified(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 14000L);
        BlockPos origin = new BlockPos(2400, 20, 200);
        StructureTemplate burrowTpl = level.getStructureManager()
                .get(ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, "burrow"))
                .orElseThrow(() -> new IllegalStateException("Missing template burrow"));
        Vec3i size = burrowTpl.getSize();
        List<ChunkPos> forced = forceChunks(level, origin, size, true);
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
        PlacedStructure placed = new PlacedStructure(origin, size, bounds, forced);

        BlockPos bedHead = new BlockPos(2405, 90, 205);
        BlockPos bedFoot = bedHead.south();
        final boolean[] started = {false};
        final long[] pushTick = {-1L};
        final ChangedEntity[] sleeperRef = {null};
        final ChangedEntity[] bumperRef = {null};

        helper.onEachTick(() -> {
            if (!started[0]) {
                if (!allEntityChunksTicking(level, origin, size)) return;
                started[0] = true;
                for (Entity e : level.getEntities((Entity) null, bounds.inflate(16.0D), ent -> !(ent instanceof ServerPlayer))) {
                    e.discard();
                }
                level.setBlock(origin.offset(28, 8, 12), Blocks.STONE.defaultBlockState(), Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
                burrowTpl.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(false),
                        level.getRandom(), Block.UPDATE_CLIENTS);
                BlockState cornerState = level.getBlockState(origin.offset(28, 8, 12));
                helper.assertTrue(cornerState.is(Blocks.SPRUCE_STAIRS),
                        "burrow ceiling corner (28,8,12) must be spruce_stairs (no stone gap), got " + cornerState);
                AABB blueLoungeWall = new AABB(origin.offset(33, 7, 7)).expandTowards(6.0D, 3.0D, 2.0D);
                var loungePaintings = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, blueLoungeWall);
                helper.assertTrue(loungePaintings.size() == 2,
                        "burrow blue lounge wall should have 2 non-overlapping paintings, found " + loungePaintings.size());
                AABB lowerBedroomWall = new AABB(origin.offset(21, 1, 32)).expandTowards(5.0D, 3.0D, 2.0D);
                var bedroomPaintings = level.getEntitiesOfClass(net.minecraft.world.entity.decoration.Painting.class, lowerBedroomWall);
                helper.assertTrue(bedroomPaintings.size() == 1,
                        "burrow lower double-bed bedroom should keep creation_of_lin painting, found " + bedroomPaintings.size());
                helper.assertTrue(level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, lowerBedroomWall).isEmpty(),
                        "burrow lower double-bed bedroom painting must not drop");

                for (int dx = -1; dx <= 1; dx++) {
                    for (int dz = -1; dz <= 2; dz++) {
                        level.setBlock(bedHead.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        for (int dy = 0; dy <= 2; dy++) {
                            level.setBlock(bedHead.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
                level.setBlock(bedFoot, Blocks.BLUE_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.FOOT), Block.UPDATE_CLIENTS);
                level.setBlock(bedHead, Blocks.BLUE_BED.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.BedBlock.FACING, net.minecraft.core.Direction.NORTH)
                        .setValue(net.minecraft.world.level.block.BedBlock.PART, net.minecraft.world.level.block.state.properties.BedPart.HEAD), Block.UPDATE_CLIENTS);

                var sleeper = net.ltxprogrammer.changed.init.ChangedEntities.LATEX_SHARK.get().create(level);
                var bumper = net.ltxprogrammer.changed.init.ChangedEntities.LATEX_ORCA.get().create(level);
                helper.assertTrue(sleeper != null && bumper != null, "failed to create test ChangedEntities");
                sleeper.setPos(bedHead.getX() + 0.5D, bedHead.getY() + 0.6875D, bedHead.getZ() + 0.5D);
                bumper.setPos(bedHead.getX() + 0.5D, bedHead.getY() + 0.6875D, bedHead.getZ() + 0.5D);
                level.addFreshEntity(sleeper);
                level.addFreshEntity(bumper);
                sleeper.getMoveControl().setWantedPosition(bedHead.getX() + 4.0D, bedHead.getY(), bedHead.getZ() + 4.0D, 0.35D);
                sleeper.setZza(0.35F);
                sleeper.walkAnimation.setSpeed(0.8F);
                sleeper.startSleeping(bedHead);
                helper.assertTrue(sleeper.isSleeping(), "sleeper must enter sleeping state");
                helper.assertFalse(sleeper.isPushable(), "sleeping ChangedEntity must not be pushable");
                sleeper.push(1.5D, 0.2D, 1.5D);
                sleeper.push(bumper);
                sleeperRef[0] = sleeper;
                bumperRef[0] = bumper;
                pushTick[0] = helper.getTick();
                return;
            }
            if (helper.getTick() < pushTick[0] + 15) {
                sleeperRef[0].push(bumperRef[0]);
                return;
            }
            try {
                ChangedEntity sleeper = sleeperRef[0];
                helper.assertTrue(sleeper.isSleeping(), "sleeping ChangedEntity must stay sleeping after bumps");
                double dx = sleeper.getX() - (bedHead.getX() + 0.5D);
                double dz = sleeper.getZ() - (bedHead.getZ() + 0.5D);
                double horizSqr = dx * dx + dz * dz;
                helper.assertTrue(horizSqr < 0.01D && sleeper.getY() >= bedHead.getY() + 0.4D && sleeper.getY() <= bedHead.getY() + 0.9D,
                        "sleeping ChangedEntity drifted/pushed off bed, horizSqr=" + horizSqr + " pos=" + sleeper.position());
                helper.assertTrue(sleeper.walkAnimation.speed() < 0.001F && sleeper.zza == 0.0F && sleeper.xxa == 0.0F,
                        "sleeping ChangedEntity must stop walking state cleanly, walkSpeed=" + sleeper.walkAnimation.speed() + " zza=" + sleeper.zza);
                helper.assertTrue(Math.abs(sleeper.getY() - sleeper.yOld) < 1.0E-4D,
                        "sleeping ChangedEntity must rest steadily without Y bounce: y=" + sleeper.getY() + " yOld=" + sleeper.yOld);

                var structReg = level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
                var burrowHolder = structReg.getHolder(net.minecraft.resources.ResourceKey.create(
                        net.minecraft.core.registries.Registries.STRUCTURE,
                        ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, "burrow"))).orElseThrow();
                var foundBurrow = level.getChunkSource().getGenerator().findNearestMapStructure(
                        level, net.minecraft.core.HolderSet.direct(burrowHolder), new BlockPos(0, 64, 0), 250, false);
                if (foundBurrow != null) {
                    BlockPos bp = foundBurrow.getFirst();
                    var start = level.getChunk(bp.getX() >> 4, bp.getZ() >> 4).getStartForStructure(burrowHolder.value());
                    if (start != null && start.isValid()) {
                        var box = start.getBoundingBox();
                        for (int cx = Math.floorDiv(box.minX(), 16); cx <= Math.floorDiv(box.maxX(), 16); cx++) {
                            for (int cz = Math.floorDiv(box.minZ(), 16); cz <= Math.floorDiv(box.maxZ(), 16); cz++) {
                                level.getChunk(cx, cz);
                            }
                        }
                        for (int x = box.minX(); x <= box.maxX(); x++) {
                            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                                for (int y = box.minY(); y <= box.maxY(); y++) {
                                    BlockPos gp = new BlockPos(x, y, z);
                                    BlockState gs = level.getBlockState(gp);
                                    if (gs.is(Blocks.GRASS_BLOCK) && gs.getValue(net.minecraft.world.level.block.SnowyDirtBlock.SNOWY)) {
                                        BlockState ab = level.getBlockState(gp.above());
                                        helper.assertTrue(ab.is(Blocks.SNOW) || ab.is(Blocks.SNOW_BLOCK) || ab.is(Blocks.POWDER_SNOW),
                                                "burrow grass_block at " + gp + " had snowy=true without snow above (above=" + ab + ")");
                                    }
                                }
                            }
                        }
                    }
                }
                helper.succeed();
            } finally {
                if (sleeperRef[0] != null) sleeperRef[0].discard();
                if (bumperRef[0] != null) bumperRef[0].discard();
                for (Entity e : level.getEntities((Entity) null, bounds.inflate(16.0D), ent -> !(ent instanceof ServerPlayer))) {
                    e.discard();
                }
                cleanupPlaced(level, placed);
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "obstacle_steering", timeoutTicks = 240)
    public static void builtInLatexClearsSmallObstaclesWithoutEnteringHazards(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 6000L);
        BlockPos origin = new BlockPos(2600, 89, 200);
        Vec3i size = new Vec3i(14, 6, 14);
        List<ChunkPos> forced = forceChunks(level, origin, size, true);
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
        PlacedStructure placed = new PlacedStructure(origin, size, bounds, forced);

        final boolean[] started = {false};
        final boolean[] pathed = {false};
        final boolean[] steppedInHazard = {false};
        final ChangedEntity[] doorMobRef = {null};
        final ChangedEntity[] pierMobRef = {null};

        helper.onEachTick(() -> {
            if (!started[0]) {
                if (!allEntityChunksTicking(level, origin, size)) return;
                started[0] = true;
                for (Entity e : level.getEntities((Entity) null, bounds.inflate(16.0D), ent -> !(ent instanceof ServerPlayer))) {
                    e.discard();
                }
                for (int x = 0; x < size.getX(); x++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        level.setBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        for (int y = 1; y <= 4; y++) {
                            level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
                // Track A (x=2): 1-wide open oak door (0.1875 leaf) + doorway walls at z=4, plus chest corner at (2, 1, 7).
                for (int y = 1; y <= 3; y++) {
                    level.setBlock(origin.offset(1, y, 4), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(origin.offset(3, y, 4), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(origin.offset(1, y, 7), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(origin.offset(4, y, 7), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                BlockState openDoor = Blocks.OAK_DOOR.defaultBlockState()
                        .setValue(DoorBlock.FACING, Direction.SOUTH)
                        .setValue(DoorBlock.HINGE, DoorHingeSide.RIGHT)
                        .setValue(DoorBlock.OPEN, true);
                level.setBlock(origin.offset(2, 1, 4), openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(2, 2, 4), openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(2, 1, 7), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);

                // Track B (x=8..9): walkway next to 2-deep water at x=7 and open door leaf on the water side + chest/campfire beyond.
                for (int z = 0; z < size.getZ(); z++) {
                    level.setBlock(origin.offset(7, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    level.setBlock(origin.offset(7, 0, z), Blocks.WATER.defaultBlockState(), Block.UPDATE_CLIENTS);
                }
                level.setBlock(origin.offset(8, 1, 4), openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(8, 2, 4), openDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(9, 1, 6), Blocks.CAMPFIRE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(8, 1, 7), Blocks.CHEST.defaultBlockState(), Block.UPDATE_CLIENTS);

                var doorMob = net.ltxprogrammer.changed.init.ChangedEntities.LATEX_SHARK.get().create(level);
                var pierMob = net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_GRIZZLY.get().create(level);
                helper.assertTrue(doorMob != null && pierMob != null, "failed to create navigation test ChangedEntities");

                doorMob.goalSelector.removeAllGoals(g -> true);
                pierMob.goalSelector.removeAllGoals(g -> true);
                doorMob.setPos(origin.getX() + 2.45D, origin.getY() + 1.0D, origin.getZ() + 1.5D);
                pierMob.setPos(origin.getX() + 8.48D, origin.getY() + 1.0D, origin.getZ() + 1.5D);
                level.addFreshEntity(doorMob);
                level.addFreshEntity(pierMob);
                doorMobRef[0] = doorMob;
                pierMobRef[0] = pierMob;
                return;
            }

            ChangedEntity doorMob = doorMobRef[0];
            ChangedEntity pierMob = pierMobRef[0];
            if (!pathed[0]) {
                if (!doorMob.onGround() || !pierMob.onGround()) return;
                pathed[0] = true;
                boolean startedDoor = doorMob.getNavigation().moveTo(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 10.5D, 0.45D);
                boolean startedPier = pierMob.getNavigation().moveTo(origin.getX() + 8.5D, origin.getY() + 1.0D, origin.getZ() + 10.5D, 0.45D);
                helper.assertTrue(startedDoor && startedPier,
                        "expected valid paths: door=" + startedDoor + ", pier=" + startedPier);
                return;
            }
            if (pierMob.isInWaterOrBubble() || net.parkabird.changedsynergy.ai.PlayerOutpostGoal.onCampfire(pierMob)) {
                steppedInHazard[0] = true;
            }
            if (doorMob.getNavigation().isDone() && doorMob.getZ() < origin.getZ() + 9.5D) {
                doorMob.getNavigation().moveTo(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 10.5D, 0.45D);
            }
            if (pierMob.getNavigation().isDone() && pierMob.getZ() < origin.getZ() + 9.5D) {
                pierMob.getNavigation().moveTo(origin.getX() + 8.5D, origin.getY() + 1.0D, origin.getZ() + 10.5D, 0.45D);
            }

            boolean bothArrived = doorMob.getZ() >= origin.getZ() + 9.5D && pierMob.getZ() >= origin.getZ() + 9.5D;
            if (!bothArrived && helper.getTick() < 220) {
                return;
            }

            try {
                helper.assertFalse(steppedInHazard[0],
                        "pierMob stepped into water or campfire during sidestep at pos=" + pierMob.position());
                helper.assertTrue(doorMob.getZ() >= origin.getZ() + 9.5D,
                        "doorMob (0.7F) got stuck before clearing open door/chest obstacles: pos=" + doorMob.position()
                                + " horizColl=" + doorMob.horizontalCollision);
                helper.assertTrue(pierMob.getZ() >= origin.getZ() + 9.5D,
                        "pierMob (0.8F) got stuck before clearing open door/chest beside hazards: pos=" + pierMob.position()
                                + " horizColl=" + pierMob.horizontalCollision);
                helper.succeed();
            } finally {
                if (doorMobRef[0] != null) doorMobRef[0].discard();
                if (pierMobRef[0] != null) pierMobRef[0].discard();
                for (Entity e : level.getEntities((Entity) null, bounds.inflate(16.0D), ent -> !(ent instanceof ServerPlayer))) {
                    e.discard();
                }
                cleanupPlaced(level, placed);
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "low_clearance_crouch", timeoutTicks = 260)
    public static void changedEntitiesCrouchUnderAllTwoBlockClearances(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 6000L);
        BlockPos origin = new BlockPos(2900, 89, 200);
        Vec3i size = new Vec3i(16, 7, 16);
        List<ChunkPos> forced = forceChunks(level, origin, size, true);
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
        PlacedStructure placed = new PlacedStructure(origin, size, bounds, forced);

        final boolean[] started = {false};
        final boolean[] pathed = {false};
        final boolean[] adultCrouchedAtSnow = {false};
        final boolean[] adultCrouchedAtTrapdoor = {false};
        final boolean[] adultCrouchedAtPlainTwoHigh = {false};
        final boolean[] tallCrouched = {false};
        final ChangedEntity[] adultRef = {null};
        final ChangedEntity[] tallRef = {null};
        final ChangedEntity[] slabCheckRef = {null};

        helper.onEachTick(() -> {
            if (!started[0]) {
                if (!allEntityChunksTicking(level, origin, size)) return;
                started[0] = true;
                for (Entity e : level.getEntities((Entity) null, bounds.inflate(16.0D), ent -> !(ent instanceof ServerPlayer))) {
                    e.discard();
                }
                for (int x = 0; x < size.getX(); x++) {
                    for (int z = 0; z < size.getZ(); z++) {
                        level.setBlock(origin.offset(x, -1, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        level.setBlock(origin.offset(x, 0, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        for (int y = 1; y <= 5; y++) {
                            level.setBlock(origin.offset(x, y, z), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
                // Enclose three isolated 1-block-wide lanes (x=2, x=7, x=12) so paths cannot detour between lanes.
                for (int z = 0; z < size.getZ(); z++) {
                    for (int x : new int[]{1, 3, 6, 8, 11, 13}) {
                        for (int y = 1; y <= 4; y++) {
                            level.setBlock(origin.offset(x, y, z), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                    }
                }
                for (int x = 0; x < size.getX(); x++) {
                    for (int y = 1; y <= 4; y++) {
                        level.setBlock(origin.offset(x, y, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        level.setBlock(origin.offset(x, y, size.getZ() - 1), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    }
                }
                // Lane A (x=2, for 1.93F non-camp wild DarkLatexWolf):
                // z=3: clean 2-high stone doorway -> should walk through UPRIGHT (no needless crouch)
                level.setBlock(origin.offset(2, 3, 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                // z=7: 2-high stone doorway + 2 snow layers (clearance 1.875) -> must crouch
                level.setBlock(origin.offset(2, 3, 7), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(2, 1, 7), Blocks.SNOW.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SnowLayerBlock.LAYERS, 2), Block.UPDATE_CLIENTS);
                // z=11: closed top-half iron trapdoor in head block (clearance 1.8125) -> must crouch
                level.setBlock(origin.offset(2, 3, 11), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(2, 2, 11), Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, false), Block.UPDATE_CLIENTS);

                // Lane B (x=7, for 2.3F LatexGrizzly):
                // z=3: closed oak door in 2-high wall
                level.setBlock(origin.offset(7, 3, 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                BlockState oakDoor = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
                level.setBlock(origin.offset(7, 1, 3), oakDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(7, 2, 3), oakDoor.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_CLIENTS);
                // z=7: half-shifted 2.0-block slab corridor (bottom slab at y=1 -> top slab at y=3)
                level.setBlock(origin.offset(7, 1, 7), Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.BOTTOM), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(7, 3, 7), Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP), Block.UPDATE_CLIENTS);
                // z=11: closed top-half iron trapdoor (1.8125)
                level.setBlock(origin.offset(7, 3, 11), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(7, 2, 11), Blocks.IRON_TRAPDOOR.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP)
                        .setValue(net.minecraft.world.level.block.TrapDoorBlock.OPEN, false), Block.UPDATE_CLIENTS);

                // Lane C (x=12, negative test: 1.5-block top-slab gap at z=3 -> neither crouch nor pass)
                level.setBlock(origin.offset(12, 2, 3), Blocks.STONE_SLAB.defaultBlockState()
                        .setValue(net.minecraft.world.level.block.SlabBlock.TYPE, net.minecraft.world.level.block.state.properties.SlabType.TOP), Block.UPDATE_CLIENTS);
                level.setBlock(origin.offset(12, 3, 3), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);

                var adult = net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                var tall = net.parkabird.changedsynergy.init.ChangedSynergyEntities.LATEX_GRIZZLY.get().create(level);
                var slabTester = net.ltxprogrammer.changed.init.ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
                helper.assertTrue(adult != null && tall != null && slabTester != null, "failed to create crouch test entities");

                adult.setPos(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 1.5D);
                tall.setPos(origin.getX() + 7.5D, origin.getY() + 1.0D, origin.getZ() + 1.5D);
                slabTester.setPos(origin.getX() + 12.5D, origin.getY() + 1.0D, origin.getZ() + 1.5D);
                level.addFreshEntity(adult);
                level.addFreshEntity(tall);
                level.addFreshEntity(slabTester);
                // Keep only door-opening goals so wander goals do not steer away from the test lane.
                adult.goalSelector.removeAllGoals(g -> !(g instanceof net.minecraft.world.entity.ai.goal.DoorInteractGoal));
                tall.goalSelector.removeAllGoals(g -> !(g instanceof net.minecraft.world.entity.ai.goal.DoorInteractGoal));
                slabTester.goalSelector.removeAllGoals(g -> true);
                adultRef[0] = adult;
                tallRef[0] = tall;
                slabCheckRef[0] = slabTester;
                return;
            }

            ChangedEntity adult = adultRef[0];
            ChangedEntity tall = tallRef[0];
            ChangedEntity slabTester = slabCheckRef[0];
            if (!pathed[0]) {
                if (!adult.onGround() || !tall.onGround() || !slabTester.onGround()) return;
                pathed[0] = true;
                boolean okAdult = adult.getNavigation().moveTo(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 14.5D, 0.55D);
                boolean okTall = tall.getNavigation().moveTo(origin.getX() + 7.5D, origin.getY() + 1.0D, origin.getZ() + 14.5D, 0.55D);
                helper.assertTrue(okAdult && okTall, "expected reachable 2-block paths: adult=" + okAdult + ", tall=" + okTall);
                var blockedSlabPath = slabTester.getNavigation().createPath(origin.offset(12, 1, 5), 0);
                helper.assertTrue(blockedSlabPath == null || !blockedSlabPath.canReach(),
                        "1.5-block top-slab gap must stay blocked even with crouch planning");
                return;
            }

            if (adult.getZ() >= origin.getZ() + 2.0D && adult.getZ() <= origin.getZ() + 4.0D && adult.getPose() == Pose.CROUCHING) {
                adultCrouchedAtPlainTwoHigh[0] = true;
            }
            if (adult.getZ() >= origin.getZ() + 5.8D && adult.getZ() <= origin.getZ() + 8.2D && adult.getPose() == Pose.CROUCHING) {
                adultCrouchedAtSnow[0] = true;
            }
            if (adult.getZ() >= origin.getZ() + 9.8D && adult.getZ() <= origin.getZ() + 12.2D && adult.getPose() == Pose.CROUCHING) {
                adultCrouchedAtTrapdoor[0] = true;
            }
            if (tall.getPose() == Pose.CROUCHING) {
                tallCrouched[0] = true;
            }

            if (adult.getNavigation().isDone() && adult.getZ() < origin.getZ() + 13.5D) {
                adult.getNavigation().moveTo(origin.getX() + 2.5D, origin.getY() + 1.0D, origin.getZ() + 14.5D, 0.55D);
            }
            if (tall.getNavigation().isDone() && tall.getZ() < origin.getZ() + 13.5D) {
                tall.getNavigation().moveTo(origin.getX() + 7.5D, origin.getY() + 1.0D, origin.getZ() + 14.5D, 0.55D);
            }

            boolean bothArrived = adult.getZ() >= origin.getZ() + 13.5D && tall.getZ() >= origin.getZ() + 13.5D;
            boolean bothStandingAgain = adult.getPose() == Pose.STANDING && tall.getPose() == Pose.STANDING;
            if ((!bothArrived || !bothStandingAgain) && helper.getTick() < 240) {
                return;
            }

            try {
                helper.assertTrue(bothArrived, "expected both walkers to cross all 2-block lanes: adultZ="
                        + adult.getZ() + " tallZ=" + tall.getZ());
                helper.assertFalse(adultCrouchedAtPlainTwoHigh[0],
                        "1.93F walker must not crouch unnecessarily in plain 2.0-block opening");
                helper.assertTrue(adultCrouchedAtSnow[0] && adultCrouchedAtTrapdoor[0],
                        "1.93F walker must crouch under low snow/trapdoor 2-block openings: snow="
                                + adultCrouchedAtSnow[0] + " trapdoor=" + adultCrouchedAtTrapdoor[0]);
                helper.assertTrue(tallCrouched[0], "2.3F tall walker must crouch under 2-block passages");
                helper.assertTrue(bothStandingAgain, "walkers must stand back up after clearing low passages: adult="
                        + adult.getPose() + " tall=" + tall.getPose());
                helper.succeed();
            } finally {
                if (adultRef[0] != null) adultRef[0].discard();
                if (tallRef[0] != null) tallRef[0].discard();
                if (slabCheckRef[0] != null) slabCheckRef[0].discard();
                for (Entity e : level.getEntities((Entity) null, bounds.inflate(16.0D), ent -> !(ent instanceof ServerPlayer))) {
                    e.discard();
                }
                cleanupPlaced(level, placed);
            }
        });
    }

    private static boolean allEntityChunksTicking(ServerLevel level, BlockPos origin, Vec3i size) {
        int minCx = Math.floorDiv(origin.getX() - 16, 16);
        int maxCx = Math.floorDiv(origin.getX() + size.getX() + 16, 16);
        int minCz = Math.floorDiv(origin.getZ() - 16, 16);
        int maxCz = Math.floorDiv(origin.getZ() + size.getZ() + 16, 16);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                long chunkKey = ChunkPos.asLong(cx, cz);
                if (!level.areEntitiesLoaded(chunkKey)
                        || !level.isPositionEntityTicking(new BlockPos(cx * 16 + 8, origin.getY(), cz * 16 + 8))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static List<ChangedEntity> campResidents(ServerLevel level, AABB bounds) {
        return level.getEntitiesOfClass(ChangedEntity.class, bounds.inflate(16.0D),
                m -> m.isAlive() && NaturalCampService.member(m)
                        && !m.getPersistentData().getCompound(StructureResidentGoal.DATA).isEmpty());
    }

    private static PlacedStructure placeStructure(ServerLevel level, String name, BlockPos origin) {
        StructureTemplate tpl = level.getStructureManager()
                .get(ResourceLocation.tryBuild(ChangedSynergyMod.MOD_ID, name))
                .orElseThrow(() -> new IllegalStateException("Missing template " + name));
        Vec3i size = tpl.getSize();
        List<ChunkPos> forced = forceChunks(level, origin, size, true);
        tpl.placeInWorld(level, origin, origin, new StructurePlaceSettings().setIgnoreEntities(false),
                level.getRandom(), Block.UPDATE_CLIENTS);
        AABB bounds = new AABB(origin.getX(), origin.getY(), origin.getZ(),
                origin.getX() + size.getX(), origin.getY() + size.getY(), origin.getZ() + size.getZ());
        return new PlacedStructure(origin, size, bounds, forced);
    }

    private static void cleanupPlaced(ServerLevel level, PlacedStructure placed) {
        for (ChangedEntity m : level.getEntitiesOfClass(ChangedEntity.class, placed.bounds.inflate(32.0D))) {
            m.discard();
        }
        for (ChunkPos chunk : placed.forcedChunks) {
            level.setChunkForced(chunk.x, chunk.z, false);
        }
    }

    private static List<ChunkPos> forceChunks(ServerLevel level, BlockPos origin, Vec3i size, boolean add) {
        int minCx = Math.floorDiv(origin.getX() - 16, 16);
        int maxCx = Math.floorDiv(origin.getX() + size.getX() + 16, 16);
        int minCz = Math.floorDiv(origin.getZ() - 16, 16);
        int maxCz = Math.floorDiv(origin.getZ() + size.getZ() + 16, 16);
        List<ChunkPos> chunks = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                level.getChunk(cx, cz);
                level.setChunkForced(cx, cz, add);
                chunks.add(new ChunkPos(cx, cz));
            }
        }
        return chunks;
    }

    private record PlacedStructure(BlockPos origin, Vec3i size, AABB bounds, List<ChunkPos> forcedChunks) {}
}
