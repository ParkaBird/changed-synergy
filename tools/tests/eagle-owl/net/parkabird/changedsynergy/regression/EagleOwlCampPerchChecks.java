package net.parkabird.changedsynergy.regression;

import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.phys.*;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.*;
import net.parkabird.changedsynergy.block.PerchBlock;

final class EagleOwlCampPerchChecks {
    static void run(net.minecraft.server.MinecraftServer server){
        var level=server.overworld();var pos=new BlockPos(8,150,8);var rail=ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get();var state=rail.defaultBlockState();var other=rail.other(pos,state);
        for(var p:BlockPos.betweenClosed(pos.offset(-2,-1,-3),pos.offset(4,-1,3)))level.setBlock(p,Blocks.STONE.defaultBlockState(),3);
        level.setBlock(pos,state,3);rail.setPlacedBy(level,pos,state,null,ItemStack.EMPTY);
        var bird=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);bird.setPos(8.5,150,6.5);
        var nonBird=ChangedSynergyEntities.LATEX_MUNTJAC.get().create(level);
        check(PlayerOutpostRestGoal.restingPlace(level,other).equals(other),"rail halves retain distinct bed identities");
        check(PlayerOutpostRestGoal.free(bird,level,pos)&&!PlayerOutpostRestGoal.free(nonBird,level,pos),"outpost beds honor bird anatomy");
        var marker=pos.offset(3,0,0);level.setBlock(marker,Blocks.CAMPFIRE.defaultBlockState(),3);var data=PlayerOutpostData.get(server);var owner=UUID.randomUUID();
        check(data.claim(level,owner,marker).equals("claimed"),"test outpost claimed");var camp=data.byOwner(owner).orElseThrow();data.setBed(camp,pos);data.refreshRestPlaces(level,camp);
        check(camp.beds.containsAll(List.of(pos,other))&&camp.beds.size()==2,"binding a rail registers both available sleeping places");
        var bed=pos.offset(0,0,-2);var bedState=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);level.setBlock(bed,bedState,3);level.setBlock(bed.east(),bedState.setValue(BedBlock.PART,BedPart.HEAD),3);
        var bounds=new AABB(0,148,0,16,158,16);var resident=new CompoundTag();resident.putBoolean("Camp",true);resident.putBoolean("Resolved",true);
        resident.putDouble("MinX",bounds.minX);resident.putDouble("MinY",bounds.minY);resident.putDouble("MinZ",bounds.minZ);resident.putDouble("MaxX",bounds.maxX);resident.putDouble("MaxY",bounds.maxY);resident.putDouble("MaxZ",bounds.maxZ);bird.getPersistentData().put(StructureResidentGoal.DATA,resident);
        var places=NaturalCampService.sites(bird).beds();check(places.containsAll(List.of(pos,other,bed.east())),"structure residents discover both perch ends alongside regular beds");
        level.addFreshEntity(bird);bird.setNoAi(false);bird.setOnGround(true);long time=level.getDayTime();level.setDayTime(6000);level.updateSkyBrightness();
        int interval=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.backgroundScanInterval.get();boolean distant=net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.distantAiThrottling.get();
        net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.backgroundScanInterval.set(1);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.distantAiThrottling.set(false);
        var goal=new NaturalCampSleepGoal(bird);
        try{check(PlayerOutpostRestGoal.sleepTime(bird)&&!PlayerOutpostRestGoal.sleepTime(nonBird),"owl sleeps in daytime; other residents retain night sleep");check(goal.canUse(),"structure resident selects a free sleep place");goal.start();goal.tick();check(bird.getSleepingPos().filter(p->p.equals(pos)||p.equals(other)).isPresent(),"available perch wins even when a regular bed is closer");level.setDayTime(18000);level.updateSkyBrightness();check(!goal.canContinueToUse(),"owl wakes for nocturnal activity");var wild=ChangedSynergyEntities.LATEX_EAGLE_OWL.get().create(level);check(!new NaturalCampSleepGoal(wild).canUse(),"unassigned wild owl does not search for beds");var leftover=bird.getSleepingPos().filter(pos::equals).isPresent()?other:pos;wild.startSleeping(leftover);check(wild.isSleeping(),"legacy nearby-perch sleeper prepared");PerchSleepService.joined(new net.minecraftforge.event.entity.EntityJoinLevelEvent(wild,level));check(!wild.isSleeping(),"reload clears unmanaged legacy perch sleep");wild.discard();check(bird.goalSelector.getAvailableGoals().stream().noneMatch(g->g.getGoal().getClass().getSimpleName().equals("PerchSleepGoal")),"no independent nearby-perch sleeping goal");
            goal.stop();level.setDayTime(6000);level.updateSkyBrightness();bird.setPos(8.5,150,6.5);
            check(data.add(camp,bird.getUUID(),"Owl",level.dimension().location().toString(),bird.blockPosition(),PlayerOutpostSupplyType.of(bird)),"owl joins test player camp");
            bird.getPersistentData().putUUID("ChangedSynergyPlayerOutpostOwner",owner);
            var rest=new PlayerOutpostRestGoal(bird);
            check(rest.canUse(),"player-camp owl selects its linked perch during daytime");rest.start();rest.tick();
            check(bird.getSleepingPos().filter(p->p.equals(pos)||p.equals(other)).isPresent(),"player-camp binding reaches native perch sleep");
            level.setDayTime(18000);level.updateSkyBrightness();check(!rest.canContinueToUse(),"player-camp owl wakes at night");rest.stop();
            bird.getPersistentData().remove("ChangedSynergyPlayerOutpostOwner");
        }
        finally{net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.backgroundScanInterval.set(interval);net.parkabird.changedsynergy.ChangedSynergyConfig.COMMON.distantAiThrottling.set(distant);goal.stop();level.setDayTime(time);level.updateSkyBrightness();bird.discard();nonBird.discard();data.disband(owner);for(var p:List.of(pos,other,bed,bed.east(),marker))level.removeBlock(p,false);}
        System.out.println("OWL_CAMP_PERCH_SERVER_PASS: player binding, dual capacity, natural structure discovery, anatomy and perch preference");
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);}
}
