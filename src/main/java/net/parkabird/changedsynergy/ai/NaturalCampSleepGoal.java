package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;

/** Natural camps share the existing bed reservations and native sleeping pose. */
@Mod.EventBusSubscriber(modid="changed_synergy")
public final class NaturalCampSleepGoal extends Goal {
    private static final String SLEEP="ChangedSynergyNaturalCampBed";
    private static final int MAX_TRAVEL_TICKS=1400;
    private static final int MAX_STALLED_TICKS=140;
    private static final double WALK_SPEED=0.38D;
    private final ChangedEntity mob;
    private BlockPos bed, access;
    private net.minecraft.world.level.pathfinder.Path initialPath;
    private Vec3 lastPos;
    private long nextCheck;
    private int ticks, stalledTicks;
    private boolean wasSleepTime;
    public NaturalCampSleepGoal(ChangedEntity mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse() {
        if(!(mob.level() instanceof ServerLevel level))return false;
        boolean sleepNow=PlayerOutpostRestGoal.sleepTime(mob);
        if(!sleepNow){wasSleepTime=false;return false;}
        if(!wasSleepTime){wasSleepTime=true;nextCheck=level.getGameTime()+(mob.getId()&7)*2L;}
        if(NaturalCampService.hasActiveMealCall(mob)||level.getGameTime()<nextCheck
                ||!SynergyPerformanceTracker.featureEnabled(SynergyPerformanceTracker.Feature.COMMUNITY)
                ||!NaturalCampService.available(mob)||mob.isSleeping()
                ||(mob.isPassenger()&&(!(mob.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity)||CompanionSeatingService.commandedSeat(mob)))
                ||mob.isUsingItem()||mob.getTarget()!=null||!PlayerOutpostGoal.canNavigate(mob))return false;
        nextCheck=level.getGameTime()+200;
        var candidates=NaturalCampService.sites(mob).beds().stream()
                .filter(p->PlayerOutpostRestGoal.free(mob,level,p))
                .sorted(Comparator.<BlockPos>comparingInt(p->level.getBlockState(p).getBlock() instanceof net.parkabird.changedsynergy.block.PerchBlock?0:1)
                        .thenComparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p)))).toList();
        var accessToBed=new java.util.LinkedHashMap<BlockPos,BlockPos>();
        for(var pos:candidates){
            if(mob.distanceToSqr(Vec3.atCenterOf(pos))<=6.25D){bed=pos;access=pos;initialPath=null;return true;}
            for(var stand:approachSpots(level,mob,pos))accessToBed.putIfAbsent(stand,pos);
        }
        if(accessToBed.isEmpty())return false;
        var path=NaturalCampService.pathAny(mob,accessToBed.keySet(),1);
        if(path!=null&&path.canReach()){
            var target=path.getTarget();
            var chosen=accessToBed.get(target);
            if(chosen==null)chosen=candidates.stream().min(Comparator.comparingDouble(b->b.distSqr(target))).orElse(null);
            if(chosen!=null){bed=chosen;access=target;initialPath=path;return true;}
        }
        return false;
    }
    @Override public void start(){
        ticks=0;stalledTicks=0;lastPos=mob.position();
        CreatureComfortGoal.endSeatedActivity(mob);
        if(mob.isPassenger()&&mob.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity&&!CompanionSeatingService.commandedSeat(mob))mob.stopRiding();
        if(!PlayerOutpostRestGoal.reserve(mob,(ServerLevel)mob.level(),bed)){bed=null;access=null;initialPath=null;mob.getNavigation().stop();return;}
        if(initialPath!=null&&mob.distanceToSqr(Vec3.atCenterOf(bed))>6.25D)mob.getNavigation().moveTo(initialPath,WALK_SPEED);
        else mob.getNavigation().stop();
        initialPath=null;
    }
    @Override public boolean canContinueToUse() {
        if(bed==null||!(mob.level() instanceof ServerLevel level)||!PlayerOutpostRestGoal.sleepTime(mob)
                ||NaturalCampService.hasActiveMealCall(mob)||!NaturalCampService.available(mob)
                ||mob.getTarget()!=null||mob.isPassenger()||!level.hasChunkAt(bed)||!PlayerOutpostRestGoal.reserved(mob,bed))return false;
        if(mob.isSleeping())return mob.getSleepingPos().filter(bed::equals).isPresent()&&bed.equals(PlayerOutpostRestGoal.restingPlace(level,bed));
        return ticks<MAX_TRAVEL_TICKS&&stalledTicks<MAX_STALLED_TICKS&&PlayerOutpostRestGoal.free(mob,level,bed);
    }
    @Override public void tick() {
        if(!canContinueToUse()||mob.isSleeping())return;
        ticks++;
        if(mob.distanceToSqr(Vec3.atCenterOf(bed))<=6.25D){
            mob.getNavigation().stop();
            if(mob.getPose()==net.minecraft.world.entity.Pose.CROUCHING)mob.setPose(net.minecraft.world.entity.Pose.STANDING);
            mob.startSleeping(bed);mob.getPersistentData().putLong(SLEEP,bed.asLong());
            return;
        }
        if(ticks%20==0){
            double movedSqr=lastPos==null?1.0D:mob.position().distanceToSqr(lastPos);
            lastPos=mob.position();
            stalledTicks=movedSqr<0.09D?stalledTicks+20:0;
        }
        if(stalledTicks>=MAX_STALLED_TICKS)return;
        if((mob.getNavigation().isDone()||stalledTicks>=40)&&PlayerOutpostGoal.canNavigate(mob)){
            int acc=mob.distanceToSqr(Vec3.atCenterOf(access))<=9.0D?0:1;
            var repath=NaturalCampService.path(mob,access,acc);
            if((repath==null||!repath.canReach())&&acc==0)repath=NaturalCampService.path(mob,access,1);
            if(repath!=null&&repath.canReach())mob.getNavigation().moveTo(repath,WALK_SPEED);
        }
    }
    private static java.util.List<BlockPos> approachSpots(ServerLevel level,ChangedEntity mob,BlockPos bed){
        var result=new java.util.ArrayList<BlockPos>();
        var state=level.getBlockState(bed);
        if(state.getBlock() instanceof net.minecraft.world.level.block.BedBlock){
            var foot=bed.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite());
            var anchors=java.util.List.of(foot,bed);
            for(var base:anchors){
                for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL){
                    var adj=base.relative(dir);
                    if(!adj.equals(bed)&&!adj.equals(foot)&&!result.contains(adj)&&NaturalCampService.walkableSurface(level,mob,adj))result.add(adj);
                }
            }
        }else{
            if(NaturalCampService.walkableSurface(level,mob,bed))result.add(bed);
            for(var dir:net.minecraft.core.Direction.Plane.HORIZONTAL){
                var adj=bed.relative(dir);
                if(NaturalCampService.walkableSurface(level,mob,adj))result.add(adj);
            }
        }
        return result;
    }
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void stop(){
        boolean mealInterrupt=NaturalCampService.hasActiveMealCall(mob);
        boolean stalled=stalledTicks>=MAX_STALLED_TICKS;
        PlayerOutpostRestGoal.cleanup(mob);mob.getPersistentData().remove(SLEEP);mob.getNavigation().stop();CreatureTaskNavigation.clearFailure(mob);
        if(mob.getPose()==net.minecraft.world.entity.Pose.CROUCHING&&mob.level().noCollision(mob,mob.getDimensions(net.minecraft.world.entity.Pose.STANDING).makeBoundingBox(mob.position())))mob.setPose(net.minecraft.world.entity.Pose.STANDING);
        bed=null;access=null;initialPath=null;lastPos=null;stalledTicks=0;
        nextCheck=mealInterrupt?0L:mob.level().getGameTime()+(stalled?200L:60L);
    }
    private static void reset(ChangedEntity mob) {
        if(!mob.getPersistentData().contains(SLEEP))return;
        var bed=BlockPos.of(mob.getPersistentData().getLong(SLEEP));
        PlayerOutpostRestGoal.cleanup(mob);
        if(mob.getSleepingPos().filter(bed::equals).isPresent())mob.stopSleeping();
        mob.getPersistentData().remove(SLEEP);
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){
        if(!event.getLevel().isClientSide()&&event.getEntity() instanceof ChangedEntity mob&&mob.getPersistentData().contains(SLEEP))
            SafeEntityMutationQueue.queue(mob,"natural_camp_sleep_reset",()->{if(mob.isAlive()&&!mob.isRemoved())reset(mob);});
    }
    // Tracking loss iterates live entity sections; waking here can move the sleeper into another section.
}
