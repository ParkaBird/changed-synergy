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
    private final ChangedEntity mob;
    private BlockPos bed, access;
    private long nextCheck;
    private int ticks;
    public NaturalCampSleepGoal(ChangedEntity mob){this.mob=mob;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse() {
        if(!(mob.level() instanceof ServerLevel level)||level.getGameTime()<nextCheck||!PlayerOutpostRestGoal.sleepTime(mob)
                ||!NaturalCampService.available(mob)||mob.isSleeping()||mob.isPassenger()&&(!(mob.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity)||CompanionSeatingService.commandedSeat(mob))||mob.isUsingItem()||mob.getTarget()!=null
                ||!PlayerOutpostGoal.canNavigate(mob)||!SynergyPerformanceTracker.allowBackground(mob,SynergyPerformanceTracker.Feature.COMMUNITY,SynergyPerformanceTracker.configuredBackgroundInterval()))return false;
        nextCheck=level.getGameTime()+200;
        for(var pos:NaturalCampService.sites(mob).beds().stream().filter(p->PlayerOutpostRestGoal.free(mob,level,p))
                .sorted(Comparator.<BlockPos>comparingInt(p->level.getBlockState(p).getBlock() instanceof net.parkabird.changedsynergy.block.PerchBlock?0:1)
                        .thenComparingDouble(p->mob.distanceToSqr(Vec3.atCenterOf(p)))).limit(4).toList()) {
            if(mob.distanceToSqr(Vec3.atCenterOf(pos))<=6){bed=pos;access=pos;return true;}
            var path=NaturalCampService.path(mob,pos,1);access=pos;
            if(path==null||!path.canReach()){
                access=approach(level,pos);
                if(!access.equals(pos))path=NaturalCampService.path(mob,access,1);
            }
            if(path!=null&&path.canReach()){bed=pos;return true;}
        }
        return false;
    }
    @Override public void start(){ticks=0;CreatureComfortGoal.endSeatedActivity(mob);if(!PlayerOutpostRestGoal.reserve(mob,(ServerLevel)mob.level(),bed))bed=null;mob.getNavigation().stop();}
    @Override public boolean canContinueToUse() {
        if(bed==null||!(mob.level() instanceof ServerLevel level)||!PlayerOutpostRestGoal.sleepTime(mob)||!NaturalCampService.available(mob)
                ||mob.getTarget()!=null||mob.isPassenger()||!level.hasChunkAt(bed)||!PlayerOutpostRestGoal.reserved(mob,bed))return false;
        if(mob.isSleeping())return mob.getSleepingPos().filter(bed::equals).isPresent()&&bed.equals(PlayerOutpostRestGoal.restingPlace(level,bed));
        return ticks<400&&PlayerOutpostRestGoal.free(mob,level,bed);
    }
    @Override public void tick() {
        if(!canContinueToUse()||mob.isSleeping())return;
        ticks++;
        if(mob.distanceToSqr(Vec3.atCenterOf(bed))>6) {
            if(ticks%40==1&&!PlayerOutpostGoal.walkTo(mob,access,access.getY(),.27))ticks=400;
            return;
        }
        mob.getNavigation().stop();mob.startSleeping(bed);mob.getPersistentData().putLong(SLEEP,bed.asLong());
    }
    private static BlockPos approach(ServerLevel level,BlockPos bed){
        var state=level.getBlockState(bed);
        // A low roof over the pillow must not prevent approaching an otherwise usable bed from its foot.
        return state.getBlock() instanceof net.minecraft.world.level.block.BedBlock
                ?bed.relative(state.getValue(net.minecraft.world.level.block.BedBlock.FACING).getOpposite()):bed;
    }
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void stop(){PlayerOutpostRestGoal.cleanup(mob);mob.getPersistentData().remove(SLEEP);mob.getNavigation().stop();CreatureTaskNavigation.clearFailure(mob);bed=null;access=null;nextCheck=mob.level().getGameTime()+100;}
    private static void reset(ChangedEntity mob) {
        if(!mob.getPersistentData().contains(SLEEP))return;
        var bed=BlockPos.of(mob.getPersistentData().getLong(SLEEP));
        PlayerOutpostRestGoal.cleanup(mob);
        if(mob.getSleepingPos().filter(bed::equals).isPresent())mob.stopSleeping();
        mob.getPersistentData().remove(SLEEP);
    }
    @SubscribeEvent public static void joined(EntityJoinLevelEvent event){if(!event.getLevel().isClientSide()&&event.getEntity() instanceof ChangedEntity mob)reset(mob);}
    // Tracking loss iterates live entity sections; waking here can move the sleeper into another section.
}
