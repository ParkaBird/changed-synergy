package net.parkabird.changedsynergy.ai;

import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.BondedTeleportSafety.ShoreApproach;

/** Shared land/water transition and no-progress tracking for companion goals. */
public final class CompanionFollowNavigation {
    private static final int SHORE_SEARCH_RADIUS = 8;
    private static final int SHORE_VERTICAL_RADIUS = 5;
    private static final int SHORE_CACHE_REQUESTS = 8;
    private static final double SHORE_EXIT_REACH_SQR = 3.0D * 3.0D;
    private static final double TARGET_MOVED_SQR = 3.0D * 3.0D;
    private static final int PROGRESS_SAMPLE_TICKS = 10;
    private static final int STALLED_TICKS = 100;
    private static final double MIN_PROGRESS_SQR = 0.20D * 0.20D;
    private static final double MINIMUM_SPEED_MODIFIER = 0.10D;

    private final ChangedEntity mob;
    @Nullable
    private ShoreApproach shoreApproach;
    @Nullable
    private Vec3 cachedTargetPosition;
    @Nullable
    private Vec3 lastProgressPosition;
    private int shoreCacheRequests;
    private int progressSampleTicks;
    private int stalledTicks;

    public CompanionFollowNavigation(ChangedEntity mob) {
        this.mob = mob;
    }

    /**
     * Requests movement toward a target without asking a water-only navigator
     * to path to a dry block. Returns whether a path or direct shore exit was
     * successfully requested.
     */
    public boolean moveToward(LivingEntity target, double speedModifier) {
        return moveToward(target, null, speedModifier);
    }

    /** Uses an exact dry destination once ordinary ground navigation is active. */
    public boolean moveTowardPosition(
            LivingEntity target,
            Vec3 destination,
            double speedModifier) {
        return moveToward(target, destination, speedModifier);
    }

    private boolean moveToward(
            LivingEntity target,
            @Nullable Vec3 destination,
            double speedModifier) {
        if (!(mob.level() instanceof ServerLevel level)
                || !(mob.getNavigation() instanceof WaterBoundPathNavigation)
                || target.isInWaterOrBubble()) {
            clearShoreCache();
            return directMove(target, destination, speedModifier);
        }

        if (shoreCacheRequests > 0) {
            shoreCacheRequests--;
        }
        if (shoreApproach == null
                || shoreCacheRequests <= 0
                || cachedTargetPosition == null
                || cachedTargetPosition.distanceToSqr(target.position())
                        > TARGET_MOVED_SQR) {
            if (!selectReachableShore(level, target, speedModifier)) {
                clearShoreCache();
                // This normally fails for a dry target, but it remains useful
                // for amphibious implementations with a permissive navigator.
                return directMove(target, destination, speedModifier);
            }
        }

        ShoreApproach approach = shoreApproach;
        if (approach == null) {
            return false;
        }
        if (mob.distanceToSqr(approach.water()) <= SHORE_EXIT_REACH_SQR) {
            mob.getNavigation().stop();
            mob.getMoveControl().setWantedPosition(
                    approach.land().x,
                    approach.land().y,
                    approach.land().z,
                    normalizedSpeed(speedModifier));
            if (approach.land().y > mob.getY() + 0.2D) {
                mob.getJumpControl().jump();
            }
            return true;
        }
        if (mob.getNavigation().moveTo(
                approach.water().x,
                approach.water().y,
                approach.water().z,
                normalizedSpeed(speedModifier))) {
            return true;
        }

        // Terrain may have changed or another entity may now occupy the cached
        // exit. Rebuild the cache immediately instead of waiting for expiry.
        shoreCacheRequests = 0;
        shoreApproach = null;
        return selectReachableShore(level, target, speedModifier);
    }

    private boolean directMove(
            LivingEntity target,
            @Nullable Vec3 destination,
            double speedModifier) {
        return destination == null
                ? mob.getNavigation().moveTo(target, speedModifier)
                : mob.getNavigation().moveTo(
                        destination.x,
                        destination.y,
                        destination.z,
                        speedModifier);
    }

    /** Samples real displacement; animation-only swimming does not count. */
    public void tickProgress(boolean movementNeeded) {
        if (!movementNeeded) {
            resetProgress();
            return;
        }
        if (++progressSampleTicks < PROGRESS_SAMPLE_TICKS) {
            return;
        }
        progressSampleTicks = 0;
        Vec3 current = mob.position();
        if (lastProgressPosition != null
                && current.distanceToSqr(lastProgressPosition)
                        < MIN_PROGRESS_SQR) {
            stalledTicks += PROGRESS_SAMPLE_TICKS;
        } else {
            stalledTicks = 0;
        }
        lastProgressPosition = current;
    }

    public boolean isStalled() {
        return stalledTicks >= STALLED_TICKS;
    }

    public void reset() {
        clearShoreCache();
        resetProgress();
    }

    public void resetProgress() {
        progressSampleTicks = 0;
        stalledTicks = 0;
        lastProgressPosition = null;
    }

    private boolean selectReachableShore(
            ServerLevel level,
            LivingEntity target,
            double speedModifier) {
        List<ShoreApproach> candidates = BondedTeleportSafety.findShoreApproaches(
                level,
                mob,
                target,
                SHORE_SEARCH_RADIUS,
                SHORE_VERTICAL_RADIUS);
        for (ShoreApproach candidate : candidates) {
            if (mob.distanceToSqr(candidate.water()) <= SHORE_EXIT_REACH_SQR
                    || mob.getNavigation().moveTo(
                            candidate.water().x,
                            candidate.water().y,
                            candidate.water().z,
                            normalizedSpeed(speedModifier))) {
                shoreApproach = candidate;
                cachedTargetPosition = target.position();
                shoreCacheRequests = SHORE_CACHE_REQUESTS;
                return true;
            }
        }
        return false;
    }

    private void clearShoreCache() {
        shoreApproach = null;
        cachedTargetPosition = null;
        shoreCacheRequests = 0;
    }

    private static double normalizedSpeed(double speedModifier) {
        return Math.max(MINIMUM_SPEED_MODIFIER, speedModifier);
    }
}
