package net.parkabird.changedsynergy.regression;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.*;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;

@Mod.EventBusSubscriber(modid="changed_synergy",value=Dist.DEDICATED_SERVER)
public final class NaturalCampSleepRegression {
    private static final BlockPos BASE=new BlockPos(5000,120,5000),FIRST=BASE.offset(7,1,7),SECOND=BASE.offset(11,1,7);
    private static MinecraftServer server;private static ServerLevel level;private static ChangedEntity a,b;private static int ticks,checks;private static boolean active;
    @SubscribeEvent public static void start(ServerStartedEvent event) {
        if(!Boolean.getBoolean("changed_synergy.naturalCampSleep"))return;
        server=event.getServer();level=server.overworld();
        try {
            ChangedSynergyConfig.SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
            ChangedSynergyConfig.COMMON.backgroundScanInterval.set(1);ChangedSynergyConfig.COMMON.distantAiThrottling.set(false);
            for(int cx=BASE.getX()>>4;cx<=(BASE.getX()+15)>>4;cx++)for(int cz=BASE.getZ()>>4;cz<=(BASE.getZ()+15)>>4;cz++){level.getChunk(cx,cz);level.setChunkForced(cx,cz,true);}
            for(int x=0;x<16;x++)for(int z=0;z<16;z++)level.setBlock(BASE.offset(x,0,z),Blocks.STONE.defaultBlockState(),2);
            place(FIRST);a=create(5);b=create(10);level.setDayTime(13000);active=true;
        }catch(Throwable error){fail(error);}
    }
    private static ChangedEntity create(int x) {
        var mob=ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE.get().create(level);mob.moveTo(BASE.getX()+x+.5,121,BASE.getZ()+5.5);
        var data=new CompoundTag();data.putBoolean("Camp",true);data.putBoolean("Resolved",true);
        for(String axis:new String[]{"X","Y","Z"}) {int n=axis.equals("X")?BASE.getX():axis.equals("Y")?BASE.getY():BASE.getZ();data.putDouble("Min"+axis,n);data.putDouble("Max"+axis,n+(axis.equals("Y")?10:16));}
        data.putDouble("HomeX",mob.getX());data.putDouble("HomeY",mob.getY());data.putDouble("HomeZ",mob.getZ());mob.getPersistentData().put(StructureResidentGoal.DATA,data);
        level.addFreshEntity(mob);mob.goalSelector.removeAllGoals(g->!(g instanceof NaturalCampSleepGoal));mob.targetSelector.removeAllGoals(g->true);return mob;
    }
    private static void place(BlockPos head) {
        var state=Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING,Direction.EAST);
        level.setBlock(head.west(),state.setValue(BedBlock.PART,BedPart.FOOT),2);level.setBlock(head,state.setValue(BedBlock.PART,BedPart.HEAD),2);
    }
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(!active||event.phase!=TickEvent.Phase.END)return;
        try {
            ticks++;
            if(ticks==180){check(a.isSleeping()!=b.isSleeping(),"one bed cannot be claimed by two residents");place(SECOND);}
            if(ticks==600){check(a.isSleeping()&&b.isSleeping(),"both residents reached beds at night");check(!a.getSleepingPos().equals(b.getSleepingPos()),"residents use different beds");level.setDayTime(1000);}
            if(ticks==650){check(!a.isSleeping()&&!b.isSleeping(),"both wake at dawn");check(!level.getBlockState(FIRST).getValue(BedBlock.OCCUPIED)&&!level.getBlockState(SECOND).getValue(BedBlock.OCCUPIED),"wake-up frees both bed flags");level.setDayTime(13000);}
            if(ticks==900){check(a.isSleeping(),"resident returns to bed next night");var enemy=EntityType.ZOMBIE.create(level);enemy.moveTo(a.getX()+1,a.getY(),a.getZ());a.setTarget(enemy);}
            if(ticks==910){check(!a.isSleeping(),"combat interrupts sleep immediately");a.setTarget(null);}
            if(ticks==1150) {
                check(a.isSleeping(),"resident can return to bed after combat");var save=new CompoundTag();a.save(save);a.discard();
                a=(ChangedEntity)EntityType.loadEntityRecursive(save,level,e->e);a.setUUID(java.util.UUID.randomUUID());level.addFreshEntity(a);
                check(!a.isSleeping()&&!a.getPersistentData().contains("ChangedSynergyNaturalCampBed"),"reload clears stale sleeping ownership");
                ChangedSynergyConfig.COMMON.naturalCampRoutines.set(false);
            }
            if(ticks==1170){check(!b.isSleeping(),"disabling routines releases sleeping residents");System.out.println("NATURAL_CAMP_SLEEP_PASS: "+checks+" night beds, reservation, dawn, combat, reload and configuration checks");active=false;server.halt(false);}
        }catch(Throwable error){fail(error);}
    }
    private static void check(boolean value,String why){if(!value)throw new AssertionError(why);checks++;}
    private static void fail(Throwable error){active=false;System.err.println("NATURAL_CAMP_SLEEP_REGRESSION_FAIL");error.printStackTrace();server.halt(false);}
}
