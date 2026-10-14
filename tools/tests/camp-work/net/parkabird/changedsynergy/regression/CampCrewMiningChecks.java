package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.CrewJob;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Actual selector timing: adjacent mining, new exposure, safe interruptions and follower handoff. */
final class CampCrewMiningChecks {
    private static int checks;
    static void run(ServerPlayer owner, PlayerOutpostData.Outpost camp) throws Exception {
        var level = owner.serverLevel();
        var data = PlayerOutpostData.get(owner.server);
        var mob = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("changed:latex_leaf")).create(level);
        var oldOwner = owner.position();
        int oldInterval = ChangedSynergyConfig.COMMON.playerOutpostWorkScanInterval.get();
        var terrain = new HashMap<BlockPos, BlockState>();
        try {
            ChangedSynergyConfig.COMMON.playerOutpostWorkScanInterval.set(200);
            level.addFreshEntity(mob);
            CreaturePersonality.establishRelationship(mob, owner);
            CreaturePersonality.setFamiliarity(mob, owner, 60);
            check(data.add(camp, mob.getUUID(), "VeinMiner", camp.dimension, mob.blockPosition(), PlayerOutpostSupplyType.of(mob)),
                    "Continuity worker joins the real camp");
            data.crewJob(camp, mob.getUUID(), CrewJob.MINE);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(18,89,18), new BlockPos(28,94,32))) {
                terrain.put(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, (pos.getY() == 89 ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 3);
            }
            var ores = List.of(new BlockPos(21,90,20), new BlockPos(21,90,21),
                    new BlockPos(22,90,20), new BlockPos(22,90,21));
            reset(owner, mob);
            for (BlockPos ore : ores) level.setBlock(ore, Blocks.IRON_ORE.defaultBlockState(), 3);
            var goal = goal(mob);
            set(goal, "nextScan", 0L); set(goal, "cursor", 0);
            int previous = 0, lastBreak = -1;
            for (int tick=0; tick<200 && previous<4; tick++) {
                CampNavigationChecks.tick(level, owner, List.of(mob), 1);
                int collected = iron(mob);
                if (collected > previous) {
                    if (lastBreak >= 0) check(tick-lastBreak <= 20,
                            "Adjacent ore has no configured ten-second discovery pause: gap="+(tick-lastBreak));
                    previous = collected; lastBreak = tick;
                }
            }
            check(previous == 4 && ores.stream().allMatch(pos -> level.getBlockState(pos).isAir()),
                    "The real selector continuously mines the full nearby vein");
            check(BondedInventoryService.inventory(mob, owner).getItem(0).getDamageValue() == 4,
                    "Each chained block consumes exactly one supplied-tool durability");
            long retry = (Long)get(goal, "nextScan");
            check(retry > level.getGameTime() && retry-level.getGameTime() <= 10,
                    "Completed native-style mining gets one short discovery retry");
            CampNavigationChecks.tick(level, owner, List.of(mob), 3);
            check(mob.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof PlayerOutpostGoal),
                    "An exhausted vein promptly yields MOVE back to following");
            check(mob.getPathfindingMalus(BlockPathTypes.WATER) == 0,
                    "Crew following uses the companion water-path policy");
            owner.setPos(20.5,90,32.5);
            double nearestFollow = Double.MAX_VALUE;
            for(int tick=0;tick<300;tick++) {
                CampNavigationChecks.tick(level, owner, List.of(mob), 1);
                nearestFollow = Math.min(nearestFollow,mob.distanceToSqr(owner));
            }
            System.out.println("CAMP_MINING_FOLLOW_END pos="+mob.position()+" owner="+owner.position()+" dist="+mob.distanceToSqr(owner)
                    +" nearest="+nearestFollow+" status="+camp.members.get(mob.getUUID()).workStatus+" action="+get(goal(mob),"action")+" block="+get(goal(mob),"block")
                    +" nav="+mob.getNavigation().getTargetPos()+" flags="+mob.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList());
            check(nearestFollow <= 36 && mob.distanceToSqr(owner) <= 64,
                    "Crew reaches its stop radius after mining and remains inside the follow threshold while idle");
            check(mob.getPathfindingMalus(BlockPathTypes.WATER) == 3,
                    "Stopping following restores the original water-path cost");
            check((Long)get(goal, "nextScan") > level.getGameTime(), "Empty discovery remains throttled");

            for(int trial=0;trial<8;trial++) {
                mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
                mob.setPos(20.5,90,20.5); mob.setDeltaMovement(Vec3.ZERO); mob.setOnGround(true);
                mob.getRandom().setSeed(trial);
                double nearest=Double.MAX_VALUE;
                for(int tick=0;tick<300;tick++) {
                    CampNavigationChecks.tick(level,owner,List.of(mob),1);
                    nearest=Math.min(nearest,mob.distanceToSqr(owner));
                }
                System.out.println("CAMP_FOLLOW_SAMPLE trial="+trial+" nearest="+nearest+" final="+mob.distanceToSqr(owner)
                        +" pos="+mob.position()+" status="+camp.members.get(mob.getUUID()).workStatus+" action="+get(goal(mob),"action")
                        +" flags="+mob.goalSelector.getRunningGoals().map(g->g.getGoal().getClass().getSimpleName()).toList());
                check(nearest<=36 && mob.distanceToSqr(owner)<=64,"Idle crew reaches the six-block stop radius and remains within its eight-block follow threshold");
            }

            reset(owner, mob);
            for (BlockPos pos : BlockPos.betweenClosed(new BlockPos(21,90,19), new BlockPos(23,92,21)))
                level.setBlock(pos, Blocks.STONE.defaultBlockState(), 3);
            BlockPos face = new BlockPos(21,91,20), inner = new BlockPos(22,91,20);
            level.setBlock(face, Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(inner, Blocks.IRON_ORE.defaultBlockState(), 3);
            var exposed = CreatureSettlementService.class.getDeclaredMethod("isExposedOre",
                    net.minecraft.server.level.ServerLevel.class, BlockPos.class, BlockState.class);
            exposed.setAccessible(true);
            check(!(boolean)exposed.invoke(null, level, inner, level.getBlockState(inner)),
                    "The second ore starts fully concealed");
            goal = goal(mob); set(goal, "nextScan", 0L); set(goal, "cursor", 0);
            int first = -1, second = -1;
            for (int tick=0; tick<150 && second<0; tick++) {
                CampNavigationChecks.tick(level, owner, List.of(mob), 1);
                if (first<0 && level.getBlockState(face).isAir()) first=tick;
                if (level.getBlockState(inner).isAir()) second=tick;
            }
            check(first >= 0 && second-first > 0 && second-first <= 20 && iron(mob) == 2,
                    "Breaking the face discovers the newly exposed ore without waiting for a full scan");

            reset(owner, mob);
            level.setBlock(face, Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(inner, Blocks.IRON_ORE.defaultBlockState(), 3);
            goal = goal(mob); set(goal, "nextScan", 0L); set(goal, "cursor", 0);
            for (int tick=0; tick<150 && iron(mob)==0; tick++) CampNavigationChecks.tick(level, owner, List.of(mob), 1);
            owner.setPos(60.5,90,29.5);
            CampNavigationChecks.tick(level, owner, List.of(mob), 3);
            check(iron(mob) == 1 && level.getBlockState(inner).is(Blocks.IRON_ORE),
                    "An owner leaving the work leash interrupts a chained mine before its next drop");

            reset(owner, mob);
            PlayerOutpostCargoInventory.inventory(mob).setWorkItems(List.of(new ItemStack(Items.COAL), new ItemStack(Items.APPLE),
                    new ItemStack(Items.WHEAT), new ItemStack(Items.COBBLESTONE)));
            level.setBlock(face, Blocks.IRON_ORE.defaultBlockState(), 3);
            level.setBlock(inner, Blocks.IRON_ORE.defaultBlockState(), 3);
            goal = goal(mob); set(goal, "nextScan", 0L); set(goal, "cursor", 0);
            boolean fullFollowing=false;
            for (int tick=0;tick<300;tick++) {
                CampNavigationChecks.tick(level, owner, List.of(mob), 1);
                fullFollowing |= mob.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof PlayerOutpostGoal);
                if (fullFollowing && mob.distanceToSqr(owner)<=36) break;
            }
            check(iron(mob) == 2 && level.getBlockState(inner).isAir()
                            && PlayerOutpostCargoInventory.inventory(mob).workItems().size() == 5,
                    "Five occupied slots do not stop a stackable vein or reserve an unusable sixth slot");
            check(fullFollowing && mob.distanceToSqr(owner) <= 36, "Miner hands off to following and reaches its owner");

            for (int occupied : List.of(5, 6)) {
                reset(owner, mob);
                var cargo = new java.util.ArrayList<ItemStack>(List.of(new ItemStack(Items.COAL,64), new ItemStack(Items.APPLE,64),
                        new ItemStack(Items.WHEAT,64), new ItemStack(Items.COBBLESTONE,64), new ItemStack(Items.DIRT,64)));
                if (occupied == 6) cargo.add(new ItemStack(Items.RAW_IRON,62));
                PlayerOutpostCargoInventory.inventory(mob).setWorkItems(cargo);
                level.setBlock(face, Blocks.IRON_ORE.defaultBlockState(),3); level.setBlock(inner, Blocks.IRON_ORE.defaultBlockState(),3);
                goal=goal(mob); set(goal,"nextScan",0L);
                CampNavigationChecks.tick(level, owner, List.of(mob), 100);
                check(level.getBlockState(face).isAir() && level.getBlockState(inner).isAir()
                                && iron(mob)==(occupied==6?64:2),
                        "Actual mining uses sixth empty slot or matching partial stack with all slots occupied: "+occupied);
                check(PlayerOutpostCargoInventory.inventory(mob).workItems().size()==6,"All six work slots are usable");
            }
            level.setBlock(face,Blocks.IRON_ORE.defaultBlockState(),3); set(goal(mob),"nextScan",0L);
            CampNavigationChecks.tick(level, owner, List.of(mob), 50);
            check(level.getBlockState(face).is(Blocks.IRON_ORE) && iron(mob)==64,"Truly full bag stops before destroying ore");
            var cargoInventory=PlayerOutpostCargoInventory.inventory(mob);
            cargoInventory.setItem(5,new ItemStack(Items.APPLE,1));
            set(goal(mob),"nextScan",0L);
            CampNavigationChecks.tick(level, owner, List.of(mob), 100);
            check(level.getBlockState(face).is(Blocks.IRON_ORE),"Partial unrelated stacks cannot consume ore without room for its drop");
            reset(owner,mob);
            level.setBlock(face,Blocks.STONE.defaultBlockState(),3); level.setBlock(inner,Blocks.STONE.defaultBlockState(),3);
            BlockPos distant=new BlockPos(28,89,20);
            level.setBlock(distant,Blocks.IRON_ORE.defaultBlockState(),3);
            goal=goal(mob); set(goal,"nextScan",0L);
            check(goal.canUse()&&distant.equals(get(goal,"block")),"Initial mining scan includes ore eight blocks away");
            goal.start();
            // Return control to the real selector after observing the selected target.
            goal.stop(); set(goal,"nextScan",0L);
            CampNavigationChecks.tick(level,owner,List.of(mob),350);
            check(level.getBlockState(distant).isAir()&&iron(mob)==1,"Wider mining discovery leads to actual travel and collection");
            System.out.println("CAMP_CREW_MINING_CONTINUITY_PASS checks="+checks);
        } finally {
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            data.remove(camp, mob.getUUID()); PlayerOutpostService.clearAssignment(mob); mob.discard();
            terrain.forEach((pos,state)->level.setBlock(pos,state,3));
            owner.setPos(oldOwner);
            ChangedSynergyConfig.COMMON.playerOutpostWorkScanInterval.set(oldInterval);
        }
    }
    private static void reset(ServerPlayer owner, ChangedEntity mob) {
        mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
        owner.setPos(20.5,90,29.5); mob.setPos(20.5,90,20.5); mob.setDeltaMovement(Vec3.ZERO); mob.setOnGround(true);
        mob.setPathfindingMalus(BlockPathTypes.WATER, 3);
        PlayerOutpostCargoInventory.inventory(mob).setWorkItems(List.of());
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE); pick.enchant(Enchantments.BLOCK_EFFICIENCY, 3);
        BondedInventoryService.inventory(mob, owner).setItem(0, pick);
        PlayerOutpostService.applyRole(mob, owner, Role.CREW);
        // Optional AI ownership changes replace goal objects at the real tick-END boundary.
        net.parkabird.changedsynergy.compat.ChangedExtrasCompat.onLivingTick(
                new net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent(mob));
        net.parkabird.changedsynergy.ai.SafeEntityMutationQueue.onServerTick(
                new net.minecraftforge.event.TickEvent.ServerTickEvent(
                        net.minecraftforge.event.TickEvent.Phase.END, () -> true, owner.server));
    }
    private static PlayerOutpostCrewGatherGoal goal(ChangedEntity mob) {
        return (PlayerOutpostCrewGatherGoal)mob.goalSelector.getAvailableGoals().stream()
                .filter(g -> g.getGoal() instanceof PlayerOutpostCrewGatherGoal).findFirst().orElseThrow().getGoal();
    }
    private static int iron(ChangedEntity mob) {
        return PlayerOutpostCargoInventory.inventory(mob).workItems().stream().filter(s -> s.is(Items.RAW_IRON)).mapToInt(ItemStack::getCount).sum();
    }
    private static Object get(Object o, String name) throws Exception { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(o); }
    private static void set(Object o, String name, Object value) throws Exception { Field f=o.getClass().getDeclaredField(name); f.setAccessible(true); f.set(o,value); }
    private static void check(boolean condition, String message) { checks++; if(!condition)throw new AssertionError(message); }
}
