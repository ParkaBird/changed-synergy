package net.parkabird.changedsynergy.ai;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;

/** Natural camps share the existing bed reservations and native sleeping pose. */
@Mod.EventBusSubscriber(modid = "changed_synergy")
public final class NaturalCampSleepGoal extends Goal {
    private static final String SLEEP = "ChangedSynergyNaturalCampBed";
    private static final int MAX_TRAVEL_TICKS = 1600;
    private static final int MAX_STALLED_TICKS = 100;
    /** Matches ChangedEntity's RandomStrollGoal (0.3); Synergy outpost walk is usually 0.35. */
    private static final double WALK_SPEED = 0.3D;
    private static final double LIE_HORIZONTAL_SQR = 1.75D * 1.75D;
    private static final double ACCESS_ARRIVAL_SQR = 1.5D * 1.5D;
    private final ChangedEntity mob;
    private BlockPos bed, access;
    private Vec3 lastPos;
    private long nextCheck;
    private int ticks, stalledTicks, repathCooldown;
    private boolean wasSleepTime;

    public NaturalCampSleepGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)) return false;
        boolean sleepNow = PlayerOutpostRestGoal.sleepTime(mob);
        if (!sleepNow) {
            wasSleepTime = false;
            return false;
        }
        if (!wasSleepTime) {
            wasSleepTime = true;
            nextCheck = level.getGameTime() + (mob.getId() & 7) * 2L;
        }
        if (NaturalCampService.hasActiveMealCall(mob) || level.getGameTime() < nextCheck
                || !SynergyPerformanceTracker.featureEnabled(SynergyPerformanceTracker.Feature.COMMUNITY)
                || !NaturalCampService.available(mob) || mob.isSleeping()
                || (mob.isPassenger() && (!(mob.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity)
                        || CompanionSeatingService.commandedSeat(mob)))
                || mob.isUsingItem() || mob.getTarget() != null || !PlayerOutpostGoal.canNavigate(mob)) {
            return false;
        }
        CreatureComfortGoal.endSeatedActivity(mob);
        List<BlockPos> candidates = NaturalCampService.sites(mob).beds().stream()
                .filter(p -> PlayerOutpostRestGoal.free(mob, level, p))
                .sorted(Comparator.<BlockPos>comparingInt(p -> level.getBlockState(p).getBlock()
                                instanceof net.parkabird.changedsynergy.block.PerchBlock ? 0 : 1)
                        .thenComparingDouble(p -> mob.distanceToSqr(Vec3.atCenterOf(p))))
                .toList();
        for (BlockPos pos : candidates) {
            if (closeEnoughToLieDown(pos)) {
                bed = pos;
                access = approachSpots(level, mob, pos).stream().findFirst().orElse(pos);
                nextCheck = level.getGameTime() + 200;
                return true;
            }
        }
        // Cap A* probes: large camps × every stand spot hitch the server when night falls.
        int probes = 0;
        for (BlockPos pos : candidates) {
            if (probes >= 6) break;
            List<BlockPos> stands = approachSpots(level, mob, pos);
            if (stands.isEmpty()) stands = List.of(pos);
            for (BlockPos stand : stands) {
                if (probes >= 6) break;
                probes++;
                CreatureTaskNavigation.clearFailure(mob);
                if (!CreatureTaskNavigation.moveTo(mob, stand, 1, WALK_SPEED)) continue;
                bed = pos;
                access = stand;
                nextCheck = level.getGameTime() + 200;
                return true;
            }
        }
        nextCheck = level.getGameTime() + 40;
        return false;
    }

    @Override
    public void start() {
        ticks = 0;
        stalledTicks = 0;
        repathCooldown = 0;
        lastPos = mob.position();
        CreatureComfortGoal.endSeatedActivity(mob);
        if (mob.isPassenger() && mob.getVehicle() instanceof net.ltxprogrammer.changed.entity.SeatEntity
                && !CompanionSeatingService.commandedSeat(mob)) {
            mob.stopRiding();
        }
        if (!PlayerOutpostRestGoal.reserve(mob, (ServerLevel) mob.level(), bed)) {
            bed = null;
            access = null;
            mob.getNavigation().stop();
            return;
        }
        if (!closeEnoughToLieDown(bed) && access != null) {
            CreatureTaskNavigation.moveTo(mob, access, 1, WALK_SPEED);
        } else {
            mob.getNavigation().stop();
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (bed == null || !(mob.level() instanceof ServerLevel level) || !PlayerOutpostRestGoal.sleepTime(mob)
                || NaturalCampService.hasActiveMealCall(mob) || !NaturalCampService.available(mob)
                || mob.getTarget() != null || mob.isPassenger() || !level.hasChunkAt(bed)
                || !PlayerOutpostRestGoal.reserved(mob, bed)) {
            return false;
        }
        if (mob.isSleeping()) {
            return mob.getSleepingPos().filter(bed::equals).isPresent()
                    && bed.equals(PlayerOutpostRestGoal.restingPlace(level, bed));
        }
        return ticks < MAX_TRAVEL_TICKS && stalledTicks < MAX_STALLED_TICKS
                && PlayerOutpostRestGoal.free(mob, level, bed);
    }

    @Override
    public void tick() {
        if (!canContinueToUse() || mob.isSleeping()) return;
        ticks++;
        // Approach spots sit beside the foot; that can be >1.75 from the head — still lie down once arrived.
        if (arrivedForSleep()) {
            tryLieDown();
            return;
        }
        if (ticks % 20 == 0) {
            double movedSqr = lastPos == null ? 1.0D : mob.position().distanceToSqr(lastPos);
            lastPos = mob.position();
            // Require near-immobility while the navigator is also idle — slow stairs should not count as stuck.
            if (movedSqr < 0.04D && mob.getNavigation().isDone()) stalledTicks += 20;
            else if (movedSqr >= 0.04D) stalledTicks = 0;
        }
        if (stalledTicks >= MAX_STALLED_TICKS) return;
        if (repathCooldown > 0) {
            repathCooldown--;
            return;
        }
        if ((mob.getNavigation().isDone() || stalledTicks >= 40) && PlayerOutpostGoal.canNavigate(mob)) {
            // Prefer a last step onto the mattress before repathing the stand cell.
            if (access != null && nearBlock(access, ACCESS_ARRIVAL_SQR)
                    && CreatureTaskNavigation.moveTo(mob, bed, 0, WALK_SPEED)) {
                repathCooldown = 15;
                return;
            }
            // Back off instead of accuracy-0 grinding into the same blocked cell.
            if (!CreatureTaskNavigation.moveTo(mob, access, 1, WALK_SPEED)) {
                if (!retargetAlternateApproach()) {
                    stalledTicks = MAX_STALLED_TICKS;
                    return;
                }
            }
            repathCooldown = 15;
        }
    }

    private boolean retargetAlternateApproach() {
        if (!(mob.level() instanceof ServerLevel level) || bed == null) return false;
        CreatureTaskNavigation.clearFailure(mob);
        List<BlockPos> stands = approachSpots(level, mob, bed);
        for (BlockPos stand : stands) {
            if (stand.equals(access)) continue;
            if (CreatureTaskNavigation.moveTo(mob, stand, 1, WALK_SPEED)) {
                access = stand;
                stalledTicks = 0;
                return true;
            }
        }
        // Current bed is a dead end — release and let canUse pick another.
        return false;
    }

    private void tryLieDown() {
        mob.getNavigation().stop();
        mob.setSpeed(0.0F);
        mob.setZza(0.0F);
        mob.setXxa(0.0F);
        if (mob.getPose() == Pose.CROUCHING
                && mob.level().noCollision(mob, mob.getDimensions(Pose.STANDING).makeBoundingBox(mob.position()))) {
            mob.setPose(Pose.STANDING);
        }
        if (!(mob.level() instanceof ServerLevel level)) return;
        if (!PlayerOutpostRestGoal.free(mob, level, bed)) {
            stalledTicks = MAX_STALLED_TICKS;
            return;
        }
        mob.startSleeping(bed);
        mob.getPersistentData().putLong(SLEEP, bed.asLong());
        // Oversized hitboxes get collision-pushed off the mattress; snap back once if still claimed.
        if (mob.isSleeping() && !nearBlock(bed, LIE_HORIZONTAL_SQR)) {
            Vec3 onBed = Vec3.atBottomCenterOf(bed).add(0.0D, 0.6875D, 0.0D);
            mob.setPos(onBed.x, onBed.y, onBed.z);
            if (!nearBlock(bed, LIE_HORIZONTAL_SQR) || mob.getSleepingPos().filter(bed::equals).isEmpty()) {
                mob.stopSleeping();
                mob.getPersistentData().remove(SLEEP);
                stalledTicks = MAX_STALLED_TICKS;
            }
        }
    }

    private boolean arrivedForSleep() {
        if (closeEnoughToLieDown(bed)) return true;
        return access != null && nearBlock(access, ACCESS_ARRIVAL_SQR);
    }

    /** Beside the mattress on the same floor — not a room below within 3D radius 2.5. */
    private boolean closeEnoughToLieDown(BlockPos pos) {
        return nearBlock(pos, LIE_HORIZONTAL_SQR);
    }

    private boolean nearBlock(BlockPos pos, double horizontalSqr) {
        if (pos == null) return false;
        double dx = mob.getX() - (pos.getX() + 0.5D);
        double dy = mob.getY() - pos.getY();
        double dz = mob.getZ() - (pos.getZ() + 0.5D);
        return dy > -0.35D && dy < 1.35D && dx * dx + dz * dz <= horizontalSqr;
    }

    private static List<BlockPos> approachSpots(ServerLevel level, ChangedEntity mob, BlockPos bed) {
        var result = new ArrayList<BlockPos>();
        var state = level.getBlockState(bed);
        if (state.getBlock() instanceof BedBlock) {
            var foot = bed.relative(state.getValue(BedBlock.FACING).getOpposite());
            var anchors = List.of(foot, bed);
            for (var base : anchors) {
                for (var dir : Direction.Plane.HORIZONTAL) {
                    var adj = base.relative(dir);
                    if (adj.equals(bed) || adj.equals(foot) || result.contains(adj)) continue;
                    if (NaturalCampService.walkableSurface(level, mob, adj)) result.add(adj);
                }
            }
        } else {
            if (NaturalCampService.walkableSurface(level, mob, bed)) result.add(bed);
            for (var dir : Direction.Plane.HORIZONTAL) {
                var adj = bed.relative(dir);
                if (NaturalCampService.walkableSurface(level, mob, adj)) result.add(adj);
            }
        }
        return result;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void stop() {
        boolean mealInterrupt = NaturalCampService.hasActiveMealCall(mob);
        boolean stalled = stalledTicks >= MAX_STALLED_TICKS;
        PlayerOutpostRestGoal.cleanup(mob);
        mob.getPersistentData().remove(SLEEP);
        mob.getNavigation().stop();
        CreatureTaskNavigation.clearFailure(mob);
        if (mob.getPose() == Pose.CROUCHING
                && mob.level().noCollision(mob, mob.getDimensions(Pose.STANDING).makeBoundingBox(mob.position()))) {
            mob.setPose(Pose.STANDING);
        }
        bed = null;
        access = null;
        lastPos = null;
        stalledTicks = 0;
        repathCooldown = 0;
        nextCheck = mealInterrupt ? 0L : mob.level().getGameTime() + (stalled ? 200L : 60L);
        if (!mealInterrupt) NaturalCampService.markWantsStation(mob);
    }

    private static void reset(ChangedEntity mob) {
        if (!mob.getPersistentData().contains(SLEEP)) return;
        var bed = BlockPos.of(mob.getPersistentData().getLong(SLEEP));
        PlayerOutpostRestGoal.cleanup(mob);
        if (mob.getSleepingPos().filter(bed::equals).isPresent()) mob.stopSleeping();
        mob.getPersistentData().remove(SLEEP);
    }

    @SubscribeEvent
    public static void joined(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity mob
                && mob.getPersistentData().contains(SLEEP)) {
            SafeEntityMutationQueue.queue(mob, "natural_camp_sleep_reset",
                    () -> { if (mob.isAlive() && !mob.isRemoved()) reset(mob); });
        }
    }
}
