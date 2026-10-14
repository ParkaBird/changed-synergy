package net.parkabird.changedsynergy.regression;

import java.util.HashMap;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.*;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Reproduce real terrain, canopy obstruction and solitary prey through the actual selector. */
final class CampCrewFieldChecks {
    static void run(ServerPlayer owner, Outpost camp) throws Exception {
        var level=owner.serverLevel(); var data=PlayerOutpostData.get(owner.server);
        var terrain=new HashMap<BlockPos,BlockState>(); var oldOwner=owner.position();
        var mob=(ChangedEntity)ForgeRegistries.ENTITY_TYPES.getValue(new ResourceLocation("changed:latex_leaf")).create(level);
        Animal cow=null;
        var herd = new java.util.ArrayList<Animal>();
        BlockPos oldFarmStart=camp.farmStart, oldFarmEnd=camp.farmEnd;
        try {
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(16,89,14),new BlockPos(36,99,28))) {
                terrain.put(pos.immutable(),level.getBlockState(pos));
                level.setBlock(pos,(pos.getY()==89?Blocks.STONE:Blocks.AIR).defaultBlockState(),3);
            }
            level.addFreshEntity(mob);
            CreaturePersonality.establishRelationship(mob,owner); CreaturePersonality.setFamiliarity(mob,owner,60);
            check(data.add(camp,mob.getUUID(),"FieldGatherer",camp.dimension,mob.blockPosition(),PlayerOutpostSupplyType.of(mob)),"Field worker joins");
            data.crewJob(camp,mob.getUUID(),CrewJob.FORAGE); PlayerOutpostService.applyRole(mob,owner,Role.CREW);
            reset(owner,mob);
            var oranges=ChangedBlocks.ORANGE_TREE_LEAVES.get();
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(26,93,18),new BlockPos(30,95,22)))
                level.setBlock(pos,oranges.defaultBlockState(),3);
            for(int y=90;y<=93;y++)level.setBlock(new BlockPos(28,y,20),Blocks.OAK_LOG.defaultBlockState(),3);
            CampNavigationChecks.tick(level,owner,List.of(mob),450);
            int fruit=PlayerOutpostCargoInventory.inventory(mob).workItems().stream().filter(s->s.is(ChangedItems.ORANGE.get())).mapToInt(s->s.getCount()).sum();
            System.out.println("CAMP_FIELD_CANOPY fruit="+fruit+" pos="+mob.position()+" status="+camp.members.get(mob.getUUID()).workStatus);
            check(fruit>=3,"Dense crown and trunk yield multiple real oranges via a reachable canopy edge");
            check(mob.getY()<93,"Ground gatherer does not need to climb onto the tree");

            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(16,90,14),new BlockPos(36,99,28)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            PlayerOutpostCargoInventory.inventory(mob).setWorkItems(List.of()); reset(owner,mob);
            BlockPos enclosed=new BlockPos(22,91,20);
            for(BlockPos pos:BlockPos.betweenClosed(enclosed.offset(-1,-1,-1),enclosed.offset(1,1,1)))
                level.setBlock(pos,Blocks.STONE.defaultBlockState(),3);
            level.setBlock(enclosed,oranges.defaultBlockState(),3);
            BlockPos exposed=new BlockPos(29,93,24); level.setBlock(exposed,oranges.defaultBlockState(),3);
            CampNavigationChecks.tick(level,owner,List.of(mob),400);
            check(level.getBlockState(enclosed).is(oranges)&&!level.getBlockState(exposed).is(oranges),
                    "Blocked closest fruit is left alone without hiding farther accessible fruit or harvesting through a wall");

            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(16,90,14),new BlockPos(36,99,28)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            PlayerOutpostCargoInventory.inventory(mob).setWorkItems(List.of());
            // A three-step bank: a single cow is within sight but has no flat plant-style approach.
            for(int x=25;x<=30;x++)for(int z=17;z<=24;z++)for(int y=90;y<90+Math.min(3,x-24);y++)
                level.setBlock(new BlockPos(x,y,z),Blocks.STONE.defaultBlockState(),3);
            cow=EntityType.COW.create(level); cow.setPos(28.5,93,20.5); cow.setNoAi(true); level.addFreshEntity(cow);
            data.crewJob(camp,mob.getUUID(),CrewJob.HUNT); PlayerOutpostService.applyRole(mob,owner,Role.CREW);
            reset(owner,mob);
            cow.setCustomName(net.minecraft.network.chat.Component.literal("Protected cow"));
            CampNavigationChecks.tick(level,owner,List.of(mob),120);
            check(cow.getHealth()==cow.getMaxHealth(),"Named solitary cow remains protected");
            cow.setCustomName(null); reset(owner,mob);
            for(int tick=0;tick<600;tick++) {
                if(cow.isAlive()) { cow.tickCount++; cow.tick(); }
                CampNavigationChecks.tick(level,owner,List.of(mob),1);
            }
            int meat=PlayerOutpostCargoInventory.inventory(mob).workItems().stream().filter(s->s.is(Items.BEEF)).mapToInt(s->s.getCount()).sum();
            System.out.println("CAMP_FIELD_HUNT alive="+cow.isAlive()+" health="+cow.getHealth()+" meat="+meat+" pos="+mob.position()+" status="+camp.members.get(mob.getUUID()).workStatus);
            check(!cow.isAlive()&&meat>0,"A solitary adult on a bank is approached, hunted and its actual drops collected");
            check(mob.getPersistentData().getLong("ChangedSynergyNextHunt")>level.getGameTime(),"Completed hunt retains bounded cooldown");
            check(mob.distanceToSqr(owner)<64,"Hunter resumes following after collecting loot");

            // Live panic/navigation matters: a stationary target cannot detect a slow chase.
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            for(BlockPos pos:BlockPos.betweenClosed(new BlockPos(16,90,14),new BlockPos(36,99,28)))level.setBlock(pos,Blocks.AIR.defaultBlockState(),3);
            reset(owner,mob); mob.getPersistentData().remove("ChangedSynergyNextHunt");
            PlayerOutpostCargoInventory.inventory(mob).setWorkItems(List.of());
            // Keep genuine panic navigation inside the fixture's elevated floor, not an artificial cliff into the void.
            for(int x=16;x<=36;x++)for(int z=14;z<=28;z++)
                if(x==16||x==36||z==14||z==28)for(int y=90;y<93;y++)level.setBlock(new BlockPos(x,y,z),Blocks.GLASS.defaultBlockState(),3);
            cow.discard(); cow=EntityType.COW.create(level); cow.setPos(27.5,90,20.5); cow.getRandom().setSeed(17L); level.addFreshEntity(cow);
            int huntTicks=0;
            for(;huntTicks<600 && cow.isAlive();huntTicks++) {
                cow.tickCount++; cow.tick(); CampNavigationChecks.tick(level,owner,List.of(mob),1);
            }
            CampNavigationChecks.tick(level,owner,List.of(mob),100);
            meat=PlayerOutpostCargoInventory.inventory(mob).workItems().stream().filter(s->s.is(Items.BEEF)).mapToInt(s->s.getCount()).sum();
            System.out.println("CAMP_FIELD_LIVE_HUNT ticks="+huntTicks+" health="+cow.getHealth()+" meat="+meat+" cow="+cow.position()+" hunter="+mob.position());
            check(!cow.isAlive()&&meat>0,"Hunter catches an animal with actual roaming/panic AI and retrieves its drops");
            check(mob.getPersistentData().getLong("ChangedSynergyNextHunt")-level.getGameTime()<=1800L,
                    "Travelling cooldown is shorter without becoming an immediate repeat hunt");

            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            reset(owner,mob); mob.getPersistentData().remove("ChangedSynergyNextHunt");
            camp.farmStart=new BlockPos(22,90,19); camp.farmEnd=new BlockPos(23,90,21);
            for(int i=0;i<20;i++) {
                var protectedCow=EntityType.COW.create(level); protectedCow.setPos(22.2+(i%2)*.6,90,19.2+(i%3)*.6);
                protectedCow.setNoAi(true); level.addFreshEntity(protectedCow); herd.add(protectedCow);
            }
            cow.discard(); cow=EntityType.COW.create(level); cow.setPos(28.5,90,20.5); cow.setNoAi(true); level.addFreshEntity(cow);
            var goal=new PlayerOutpostCrewGatherGoal(mob);
            check(goal.canUse(),"A protected herd larger than the candidate budget does not hide eligible wild prey");
            var prey=goal.getClass().getDeclaredField("prey"); prey.setAccessible(true);
            check(prey.get(goal)==cow,"Only the eligible animal is reserved; livestock remains untouched");
            goal.stop();
            System.out.println("CAMP_CREW_FIELD_PASS checks=12");
        } finally {
            mob.goalSelector.getRunningGoals().toList().forEach(net.minecraft.world.entity.ai.goal.WrappedGoal::stop);
            data.remove(camp,mob.getUUID()); PlayerOutpostService.clearAssignment(mob); mob.discard();
            if(cow!=null)cow.discard();
            herd.forEach(Animal::discard); camp.farmStart=oldFarmStart; camp.farmEnd=oldFarmEnd;
            terrain.forEach((pos,state)->level.setBlock(pos,state,3)); owner.setPos(oldOwner);
        }
    }
    private static void reset(ServerPlayer owner,ChangedEntity mob) throws Exception {
        owner.setPos(20.5,90,22.5); mob.setPos(20.5,90,20.5); mob.setOnGround(true); mob.setDeltaMovement(Vec3.ZERO);
        var goal=mob.goalSelector.getAvailableGoals().stream().filter(g->g.getGoal() instanceof PlayerOutpostCrewGatherGoal).findFirst().orElseThrow().getGoal();
        var field=goal.getClass().getDeclaredField("nextScan"); field.setAccessible(true); field.set(goal,0L);
    }
    private static void check(boolean condition,String message) { if(!condition)throw new AssertionError(message); }
}
