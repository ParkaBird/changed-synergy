package net.parkabird.changedsynergy.gametest;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
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

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "burrow_sleep", timeoutTicks = 1200)
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
            if (everSlept.size() == residents.size() && residents.size() >= 8) {
                cleanupPlaced(level, placed);
                helper.succeed();
                return;
            }
            if (tick >= 1150) {
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

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "burrow_meal", timeoutTicks = 1200)
    public static void burrowMidnightMealSummonHonorsPublicStorage(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        configureWorld(level, 18000L);
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
        final Set<UUID> wokeForMeal = new HashSet<>();
        final Set<UUID> arrivedNearDinner = new HashSet<>();
        final BlockPos kitchenFire = placed.origin.offset(31, 12, 5);

        helper.onEachTick(() -> {
            long tick = helper.getTick();
            if (tick < 15) return;
            List<ChangedEntity> residents = campResidents(level, placed.bounds);
            ChangedEntity chief = residents.stream()
                    .filter(e -> CampDialogue.chief(e)
                            || e.getPersistentData().getCompound(StructureResidentGoal.DATA).getBoolean("Character"))
                    .findFirst().orElse(null);
            helper.assertTrue(chief != null, "burrow must have a camp organizer");

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
                long sleepingCount = residents.stream().filter(ChangedEntity::isSleeping).count();
                if (sleepingCount < Math.min(4, residents.size()) && tick < 550) return;
                summonedMeal[0] = true;
                summonedAt[0] = tick;
                ServerPlayer mock = createTestPlayer(level, chief);
                joinCampHome(chief, mock);
                boolean ok = CampApproachGoal.shareMeal(chief, mock);
                helper.assertTrue(ok, "shareMeal must succeed after public chest is stocked");
                for (ChangedEntity m : residents) {
                    if (!m.isSleeping() && NaturalCampService.hasActiveMealCall(m)) {
                        wokeForMeal.add(m.getUUID());
                    }
                }
                helper.assertTrue(wokeForMeal.size() == residents.size(),
                        "All burrow residents must receive unlimited-range meal call and wake: "
                                + wokeForMeal.size() + "/" + residents.size());
                return;
            }

            if (!waitedDinner[0]) {
                for (ChangedEntity m : residents) {
                    boolean activeMeal = m.goalSelector.getRunningGoals()
                            .anyMatch(g -> g.getGoal() instanceof NaturalCampGoal);
                    if (activeMeal && m.distanceToSqr(kitchenFire.getX() + 0.5D, kitchenFire.getY(), kitchenFire.getZ() + 0.5D) <= 64.0D) {
                        arrivedNearDinner.add(m.getUUID());
                    }
                }
                if (arrivedNearDinner.size() >= Math.min(5, residents.size())) {
                    waitedDinner[0] = true;
                    cleanupPlaced(level, placed);
                    helper.succeed();
                    return;
                }
                if (tick - summonedAt[0] >= 550) {
                    String status = residents.stream()
                            .filter(m -> !arrivedNearDinner.contains(m.getUUID()))
                            .map(m -> ForgeRegistries.ENTITY_TYPES.getKey(m.getType()) + "@" + m.blockPosition()
                                    + "(call=" + NaturalCampService.hasActiveMealCall(m)
                                    + ",sleep=" + m.isSleeping() + ")")
                            .reduce((a, b) -> a + ", " + b).orElse("none");
                    cleanupPlaced(level, placed);
                    helper.fail("Summoned burrow residents did not reach dinner area ("
                            + arrivedNearDinner.size() + "/" + residents.size() + "): " + status);
                }
            }
        });
    }

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "lush_cabin", timeoutTicks = 1200)
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
            if (tick >= 1150) {
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

    @GameTest(templateNamespace = "changed_synergy", template = "hyena_camp_open", batch = "prometheus_desert", timeoutTicks = 1500)
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

            boolean allPromSlept = prometheusSlept.size() == promList.size();
            boolean allDesertSlept = desertSlept.size() == desertList.size();

            if (!desertMealCalled[0] && allDesertSlept) {
                ChangedEntity desertChief = desertList.stream().filter(CampDialogue::chief).findFirst().orElse(null);
                helper.assertTrue(desertChief != null, "desert_refuge must have a chief");
                desertMealCalled[0] = true;
                desertMealTick[0] = tick;
                ServerPlayer mock = createTestPlayer(level, desertChief);
                joinCampHome(desertChief, mock);
                helper.assertTrue(CampApproachGoal.shareMeal(desertChief, mock),
                        "desert_refuge shareMeal must succeed at dining spot");
                return;
            }

            if (desertMealCalled[0]) {
                for (ChangedEntity m : desertList) {
                    boolean activeMeal = m.goalSelector.getRunningGoals()
                            .anyMatch(g -> g.getGoal() instanceof NaturalCampGoal);
                    if (activeMeal && m.distanceToSqr(desertDiningSpot.getX() + 0.5D, desertDiningSpot.getY(), desertDiningSpot.getZ() + 0.5D) <= 81.0D) {
                        desertDinnerArrived.add(m.getUUID());
                    }
                }
            }

            if (allPromSlept && desertMealCalled[0] && desertDinnerArrived.size() >= Math.min(4, desertList.size())) {
                cleanupPlaced(level, prometheus);
                cleanupPlaced(level, desert);
                helper.succeed();
                return;
            }

            if (tick >= 1450) {
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
                        + ", desertDinner=" + desertDinnerArrived.size() + "/" + desertList.size()
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
