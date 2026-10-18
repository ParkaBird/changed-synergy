package net.parkabird.changedsynergy.regression;

import java.util.ArrayList;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.*;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** A busy home camp must not permanently starve travelling jobs on the real selector. */
final class CampCrewSchedulingChecks {
    private static int checks;

    static void run(ServerPlayer owner, Outpost camp) throws Exception {
        var level = owner.serverLevel(); var data = PlayerOutpostData.get(owner.server);
        var created = new ArrayList<ChangedEntity>(); var animals = new ArrayList<Animal>();
        int oldLimit = ChangedSynergyConfig.COMMON.playerOutpostResidents.get();
        int oldWorkers = ChangedSynergyConfig.COMMON.playerOutpostActiveWorkers.get();
        BlockPos oldFarmStart = camp.farmStart, oldFarmEnd = camp.farmEnd;
        Vec3 ownerPosition = owner.position();
        try {
            ChangedSynergyConfig.COMMON.playerOutpostResidents.set(16);
            ChangedSynergyConfig.COMMON.playerOutpostActiveWorkers.set(4);
            var leaf = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("changed:latex_leaf")).create(level);
            created.add(leaf); leaf.setPos(20.5,90,20.5); leaf.setOnGround(true); level.addFreshEntity(leaf);
            CreaturePersonality.establishRelationship(leaf, owner); CreaturePersonality.setFamiliarity(leaf, owner, 60);
            check(data.add(camp, leaf.getUUID(), "CrewLeaf", camp.dimension, leaf.blockPosition(), PlayerOutpostSupplyType.of(leaf)), "Ordinary leaf friend joins camp");
            data.crewJob(camp, leaf.getUUID(), CrewJob.MINE); PlayerOutpostService.applyRole(leaf, owner, Role.CREW);
            // This resource is off to the side of a nearby stationary owner. Following the
            // owner cannot accidentally carry the member into harvesting range.
            owner.setPos(20.5,90,22.5);
            BlockPos resource = new BlockPos(26,89,20);
            level.setBlock(resource, Blocks.IRON_ORE.defaultBlockState(), 3);
            boolean started=false;
            for(int i=0;i<300&&!started;i++) {
                CampNavigationChecks.tick(level, owner, List.of(leaf), 1);
                started=leaf.goalSelector.getRunningGoals().anyMatch(g->g.getGoal() instanceof PlayerOutpostCrewGatherGoal);
            }
            check(started,"Surface-only vein starts mining while ordinary follow stays nearby");
            check(leaf.distanceToSqr(Vec3.atCenterOf(resource))>9,"Selected surface vein still requires actual travel");
            // An owner who moves within the finishing radius must not interrupt the mine route.
            owner.setPos(13.5,90,15.5);
            CampNavigationChecks.tick(level, owner, List.of(leaf), 500);
            diagnose(leaf, resource);
            check(level.getBlockState(resource).isAir() && cargo(leaf, Items.RAW_IRON) == 1,
                    "Real AI finishes a surface vein despite the owner's conflicting follow direction");
            PlayerOutpostService.releaseCargo(leaf);
            level.setBlock(resource,Blocks.STONE.defaultBlockState(),3);
            terrain(owner, leaf, camp);
            // Each following scenario starts from its own settled flat-field position.
            leaf.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            leaf.getNavigation().stop(); leaf.setPos(20.5,90,20.5);
            leaf.setDeltaMovement(Vec3.ZERO); leaf.setOnGround(true);
            owner.setPos(20.5,90,22.5);
            camp.members.get(leaf.getUUID()).workStatus="travelling";
            check(!new PlayerOutpostGoal(leaf).canUse()&&camp.members.get(leaf.getUUID()).workStatus.equals("waiting"),
                    "A settled crew member does not claim to be travelling to a workplace");
            resource=new BlockPos(26,90,20);

            // Keep four real harvesting goals unfinished while the travelling member ticks.
            // This represents other loaded workers using all of the stationary camp budget.
            camp.farmStart = new BlockPos(9,90,9); camp.farmEnd = new BlockPos(10,90,10);
            var potatoes = (net.minecraft.world.level.block.CropBlock)Blocks.POTATOES;
            for (BlockPos crop:BlockPos.betweenClosed(camp.farmStart, camp.farmEnd)) {
                level.setBlock(crop.below(),Blocks.FARMLAND.defaultBlockState(),3);
                level.setBlock(crop,potatoes.getStateForAge(potatoes.getMaxAge()),3);
            }
            for (int i=0; i<4; i++) {
                var worker = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("changed:dark_latex_wolf_male")).create(level);
                created.add(worker); worker.setPos(6.5+i,90,6.5); worker.setOnGround(true); level.addFreshEntity(worker);
                CreaturePersonality.establishRelationship(worker, owner); CreaturePersonality.setFamiliarity(worker, owner, 60);
                check(data.add(camp, worker.getUUID(), "BusyFarmer", camp.dimension, worker.blockPosition(), PlayerOutpostSupplyType.ORANGE), "Stationary worker joins camp");
                data.setWork(camp, worker.getUUID(), Work.FARM, null); PlayerOutpostService.applyRole(worker, owner, Role.SUPPLY);
                var work = worker.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof PlayerOutpostWorkGoal).findFirst().orElseThrow();
                set(work.getGoal(), "scanInitialized", true); set(work.getGoal(), "nextScan", 0L);
                boolean ready = work.canUse();
                if(!ready)System.out.println("CAMP_BUSY_FARMER_DIAGNOSTIC index="+i+" status="+camp.members.get(worker.getUUID()).workStatus);
                check(ready, "Busy farmer has a real pending harvest"); work.start();
            }
            check(created.stream().filter(m -> camp.members.get(m.getUUID()).role == Role.SUPPLY)
                            .allMatch(m -> m.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof PlayerOutpostWorkGoal)),
                    "All four stationary work slots are occupied");
            leaf.setPos(20.5,90,20.5); leaf.setDeltaMovement(Vec3.ZERO); leaf.setOnGround(true);
            level.setBlock(resource, Blocks.IRON_ORE.defaultBlockState(), 3);
            CampNavigationChecks.tick(level, owner, List.of(leaf), 500);
            diagnose(leaf, resource);
            check(level.getBlockState(resource).isAir() && cargo(leaf, Items.RAW_IRON) == 1,
                    "Busy home workers do not starve scheduled travelling mining");
            PlayerOutpostService.releaseCargo(leaf);
            data.crewJob(camp, leaf.getUUID(), CrewJob.FORAGE); PlayerOutpostService.applyRole(leaf, owner, Role.CREW);
            leaf.setPos(20.5,90,20.5); leaf.setDeltaMovement(Vec3.ZERO); leaf.setOnGround(true);
            level.setBlock(resource, Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE, 3), 3);
            CampNavigationChecks.tick(level, owner, List.of(leaf), 500);
            if (level.getBlockState(resource).getValue(SweetBerryBushBlock.AGE) != 1) diagnose(leaf, resource);
            check(level.getBlockState(resource).getValue(SweetBerryBushBlock.AGE) == 1 && cargo(leaf, Items.SWEET_BERRIES) >= 2,
                    "Busy home workers do not starve scheduled travelling foraging");
            PlayerOutpostService.releaseCargo(leaf);
            leaf.setPos(20.5,90,20.5); leaf.setDeltaMovement(Vec3.ZERO); leaf.setOnGround(true);
            BlockPos canopy = new BlockPos(26,94,20);
            var oranges = net.ltxprogrammer.changed.init.ChangedBlocks.ORANGE_TREE_LEAVES.get();
            level.setBlock(canopy, oranges.defaultBlockState(), 3);
            CampNavigationChecks.tick(level, owner, List.of(leaf), 650);
            if (level.getBlockState(canopy).is(oranges)) diagnose(leaf,canopy);
            check(!level.getBlockState(canopy).is(oranges)
                            && PlayerOutpostCargoInventory.inventory(leaf).workItems().stream()
                                    .anyMatch(stack -> new ResourceLocation("changed:orange").equals(ForgeRegistries.ITEMS.getKey(stack.getItem()))),
                    "A travelling gatherer approaches fruit above its head and collects a real orange");
            PlayerOutpostService.releaseCargo(leaf); level.setBlock(canopy, Blocks.AIR.defaultBlockState(), 3);
            leaf.setPos(20.5,90,20.5); leaf.setDeltaMovement(Vec3.ZERO); leaf.setOnGround(true);
            BlockPos vine = new BlockPos(20,94,20);
            level.setBlock(vine.above(),Blocks.STONE.defaultBlockState(),3);
            level.setBlock(vine, Blocks.CAVE_VINES.defaultBlockState().setValue(CaveVines.BERRIES, true), 3);
            CampNavigationChecks.tick(level, owner, List.of(leaf), 200);
            check(!level.getBlockState(vine).getValue(CaveVines.BERRIES) && cargo(leaf, Items.GLOW_BERRIES) > 0,
                    "Nearby hanging fruit is discovered promptly and harvested at eye-based reach");
            PlayerOutpostService.releaseCargo(leaf); level.setBlock(vine, Blocks.AIR.defaultBlockState(), 3);
            level.setBlock(vine.above(),Blocks.AIR.defaultBlockState(),3);
            data.crewJob(camp, leaf.getUUID(), CrewJob.HUNT); PlayerOutpostService.applyRole(leaf, owner, Role.CREW);
            leaf.setPos(20.5,90,20.5); leaf.setDeltaMovement(Vec3.ZERO); leaf.setOnGround(true);
            for (int i=0;i<3;i++) { var cow = EntityType.COW.create(level); cow.setPos(22.5+i,90,25.5); cow.setHealth(1); level.addFreshEntity(cow); animals.add(cow); }
            for (int tick=0; tick<500; tick++) {
                // Real navigation must keep tracking prey; a stationary one-tick kill is insufficient.
                for (Animal animal:animals) if (animal.isAlive() && animal.getZ()<27.5)
                    animal.setPos(animal.getX(),90,animal.getZ()+0.01);
                CampNavigationChecks.tick(level, owner, List.of(leaf), 1);
            }
            check(animals.stream().filter(a -> !a.isAlive()).count() == 1 && cargo(leaf, Items.BEEF) > 0,
                    "Busy home workers do not starve scheduled hunting and collection of actual loot");
            check(camp.members.get(leaf.getUUID()).workStatus.equals("hunt_cooldown"),
                    "A settled hunter explains its cooldown instead of appearing to do nothing");
            check(leaf.distanceToSqr(owner) < 64, "Crew resumes following after completing its finite job");
            // Separating groups must retain the crew's own concurrency limit.
            ChangedSynergyConfig.COMMON.playerOutpostActiveWorkers.set(1);
            var peer = (ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("changed:latex_leaf")).create(level);
            created.add(peer); peer.setPos(23.5,90,25.5); peer.setOnGround(true); level.addFreshEntity(peer);
            CreaturePersonality.establishRelationship(peer, owner); CreaturePersonality.setFamiliarity(peer, owner, 60);
            check(data.add(camp,peer.getUUID(),"CrewPeer",camp.dimension,peer.blockPosition(),PlayerOutpostSupplyType.of(peer)),"Second crew member joins");
            check(data.crewJob(camp,peer.getUUID(),CrewJob.FORAGE),"Second crew job is accepted"); PlayerOutpostService.applyRole(peer,owner,Role.CREW);
            level.setBlock(resource,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,3),3);
            var peerWork=peer.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof PlayerOutpostCrewGatherGoal).findFirst().orElseThrow();
            set(peerWork.getGoal(),"nextScan",0L); set(peerWork.getGoal(),"cursor",0);
            check(peerWork.canUse(),"One crew slot remains usable despite four busy suppliers and limit one"); peerWork.start();
            var budget=PlayerOutpostWorkGoal.class.getDeclaredMethod("workerSlotAvailable",ChangedEntity.class,Outpost.class); budget.setAccessible(true);
            check(!(boolean)budget.invoke(null,leaf,camp),"Another travelling task cannot exceed its own one-worker limit");
            check(!PlayerOutpostWorkGoal.canWork(created.get(1),camp),"Stationary workers retain their own lowered limit");
            peerWork.stop();
            check((boolean)budget.invoke(null,leaf,camp),"Stopping a crew task frees its slot even while the home camp stays busy");
            System.out.println("CAMP_CREW_SCHEDULING_REGRESSION_PASS checks="+checks);
        } finally {
            for (ChangedEntity mob:created) {
                mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
                data.remove(camp,mob.getUUID()); PlayerOutpostService.clearAssignment(mob); mob.discard();
            }
            animals.forEach(Animal::discard); owner.setPos(ownerPosition);
            ChangedSynergyConfig.COMMON.playerOutpostResidents.set(oldLimit); ChangedSynergyConfig.COMMON.playerOutpostActiveWorkers.set(oldWorkers);
            camp.farmStart = oldFarmStart; camp.farmEnd = oldFarmEnd;
        }
    }
    /** Optional local reproduction uses terrain only; it never opens or writes the user's save. */
    private static void terrain(ServerPlayer owner, ChangedEntity mob, Outpost camp) throws Exception {
        String snapshotPath = System.getProperty("changed_synergy.campTerrainSnapshot");
        if (snapshotPath == null || snapshotPath.isBlank()) return;
        var file = java.nio.file.Path.of(snapshotPath);
        if (!java.nio.file.Files.isRegularFile(file))
            throw new IllegalArgumentException("Missing requested camp terrain snapshot: " + file);
        var level=owner.serverLevel();
        var snapshot=com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(file)).getAsJsonObject();
        var changed=new java.util.HashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
        Vec3 oldOwner=owner.position();
        BlockPos target=new BlockPos(23,87,21);
        try {
            // Replacing the entire test terrain is an artificial scene change: discard
            // the old flat-field path and start reproducible, idle discovery here.
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            mob.getNavigation().stop(); mob.getRandom().setSeed(0);
            var terrainGoal=mob.goalSelector.getAvailableGoals().stream()
                    .filter(g->g.getGoal() instanceof PlayerOutpostCrewGatherGoal).findFirst().orElseThrow().getGoal();
            set(terrainGoal,"nextScan",0L); set(terrainGoal,"cursor",0);
            for(var entry:snapshot.getAsJsonArray("blocks")) {
                var b=entry.getAsJsonObject(); var pos=new BlockPos(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt());
                changed.put(pos,level.getBlockState(pos));
                var json=b.getAsJsonObject("state"); var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation(json.get("Name").getAsString()));
                var state=block.defaultBlockState();
                if(json.has("Properties"))for(var prop:json.getAsJsonObject("Properties").entrySet()) {
                    var property=block.getStateDefinition().getProperty(prop.getKey());
                    if(property!=null)state=property(state,property,prop.getValue().getAsString());
                }
                level.setBlock(pos,state,3);
            }
            var p=snapshot.getAsJsonArray("mob"); mob.setPos(p.get(0).getAsDouble(),p.get(1).getAsDouble(),p.get(2).getAsDouble());
            var o=snapshot.getAsJsonArray("owner"); owner.setPos(o.get(0).getAsDouble(),o.get(1).getAsDouble(),o.get(2).getAsDouble());
            mob.setDeltaMovement(Vec3.ZERO); mob.setOnGround(true);
            PlayerOutpostWorkGoal.acceptCargo(mob,new ItemStack(Items.COAL,2));
            PlayerOutpostService.applyRole(mob,owner,Role.CREW);
            check(level.getBlockState(target).is(Blocks.IRON_ORE),"Terrain reproduction retains the exposed iron vein");
            CampNavigationChecks.tick(level,owner,List.of(mob),700);
            diagnose(mob,target);
            System.out.println("CAMP_CREW_TERRAIN_TRACE pos="+mob.position()+" owner="+owner.position()+" target="+level.getBlockState(target)
                    +" cargo="+PlayerOutpostCargoInventory.inventory(mob).workItems()+" status="+camp.members.get(mob.getUUID()).workStatus);
            long mined=snapshot.getAsJsonArray("blocks").asList().stream().map(com.google.gson.JsonElement::getAsJsonObject)
                    .filter(b->b.getAsJsonObject("state").get("Name").getAsString().endsWith("_ore"))
                    .filter(b->level.getBlockState(new BlockPos(b.get("x").getAsInt(),b.get("y").getAsInt(),b.get("z").getAsInt())).isAir()).count();
            check(mined>0&&(cargo(mob,Items.COAL)>2||cargo(mob,Items.RAW_IRON)>0),
                    "Travelling miner reaches real hillside veins and collects their loot while retaining earlier coal");
        } finally {
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            for(var entry:changed.entrySet())level.setBlock(entry.getKey(),entry.getValue(),3);
            owner.setPos(oldOwner); PlayerOutpostService.releaseCargo(mob);
            mob.setPos(20.5,90,20.5); mob.setDeltaMovement(Vec3.ZERO);mob.setOnGround(true);
        }
    }
    private static <T extends Comparable<T>> net.minecraft.world.level.block.state.BlockState property(
            net.minecraft.world.level.block.state.BlockState state,net.minecraft.world.level.block.state.properties.Property<T> property,String value) {
        return property.getValue(value).map(v->state.setValue(property,v)).orElse(state);
    }
    private static void diagnose(ChangedEntity mob, BlockPos resource) throws Exception {
        if (mob.level().getBlockState(resource).isAir()) return;
        var scheduled = mob.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof PlayerOutpostCrewGatherGoal).findFirst().orElseThrow().getGoal();
        System.out.println("CAMP_CREW_SCHEDULING_DIAGNOSTIC pos="+mob.position()+" target="+mob.getTarget()
                +" status="+PlayerOutpostData.get(((net.minecraft.server.level.ServerLevel)mob.level()).getServer()).byMember(mob.getUUID()).orElseThrow().members.get(mob.getUUID()).workStatus
                +" cargo="+PlayerOutpostCargoInventory.inventory(mob).workItems()
                +" cursor="+get(scheduled,"cursor")+" action="+get(scheduled,"action")
                +" block="+get(scheduled,"block")+" stand="+get(scheduled,"stand")
                +" remaining="+get(scheduled,"remaining")+" progress="+get(scheduled,"progress")
                +" path="+mob.getNavigation().getPath()+" navigating="+!mob.getNavigation().isDone()+" goals="
                +mob.goalSelector.getAvailableGoals().stream().map(g->g.getPriority()+":"+g.getGoal().getClass().getSimpleName()+":"+g.isRunning()+":"+g.getFlags()).toList());
    }
    private static int cargo(ChangedEntity mob, net.minecraft.world.item.Item item) {
        return PlayerOutpostCargoInventory.inventory(mob).workItems().stream().filter(s->s.is(item)).mapToInt(ItemStack::getCount).sum();
    }
    private static Object get(Object goal, String name) throws Exception { var f=goal.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(goal); }
    private static void set(Object goal, String name, Object value) throws Exception { var f=goal.getClass().getDeclaredField(name);f.setAccessible(true);f.set(goal,value); }
    private static void check(boolean value, String message) { checks++; if(!value)throw new AssertionError(message); }
}
