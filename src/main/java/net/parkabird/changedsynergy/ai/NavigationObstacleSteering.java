package net.parkabird.changedsynergy.ai;

import java.util.WeakHashMap;
import net.ltxprogrammer.changed.block.entity.OpenableDoor;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.DoorInteractGoal;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Nudges navigation steering around small horizontal snags without leaving safe ground. */
public final class NavigationObstacleSteering {
    private static final double[] LATERAL_PROBES = {0.095D, 0.115D, 0.15D, 0.22D, 0.34D, 0.52D, 0.78D};
    private static final double[] TIGHT_PROBES = {0.20D, 0.36D, 0.56D};
    private static final int BLOCK_STUCK_TICKS = 4;
    private static final int STEP_STUCK_TICKS = 6;
    private static final int ENTITY_STUCK_TICKS = 10;
    private static final int HOLD_TICKS = 14;
    private static final int FLIP_SIDE_TICKS = 4;
    private static final int SIDE_MEMORY_TICKS = 40;
    private static final WeakHashMap<Mob, SteerState> STATES = new WeakHashMap<>();

    private NavigationObstacleSteering() {}

    public static void clearStuck(Mob mob) {
        SteerState state = STATES.get(mob);
        if (state != null) {
            state.blockStuckTicks = 0;
            state.entityStuckTicks = 0;
        }
    }

    public static void tickSteering(Mob mob) {
        if (!canSteer(mob)) {
            STATES.remove(mob);
            return;
        }
        Path path = mob.getNavigation().getPath();
        var move = mob.getMoveControl();
        if (path == null || path.isDone() || !move.hasWanted() || move.getSpeedModifier() <= 0.0D) {
            STATES.remove(mob);
            return;
        }
        Vec3 next = path.getNextEntityPos(mob);
        double dx = next.x - mob.getX();
        double dz = next.z - mob.getZ();
        double dist = Math.hypot(dx, dz);
        if (dist < 0.08D) {
            STATES.remove(mob);
            return;
        }

        double dirX = dx / dist;
        double dirZ = dz / dist;
        long gameTime = mob.level().getGameTime();
        SteerState state = STATES.computeIfAbsent(mob, unused -> new SteerState(mob.getX(), mob.getZ(), gameTime));
        if (gameTime - state.lastTick > 2L) {
            state.reset(mob.getX(), mob.getZ(), gameTime);
        }

        double progress = (mob.getX() - state.lastX) * dirX + (mob.getZ() - state.lastZ) * dirZ;
        state.lastX = mob.getX();
        state.lastZ = mob.getZ();
        state.lastTick = gameTime;

        // Let door-opening goals open closed doors/gates instead of steering away from them.
        if (awaitingDoorOpen(mob, next, dirX, dirZ)) {
            state.blockStuckTicks = 0;
            state.entityStuckTicks = 0;
            state.holdTicks = 0;
            return;
        }

        boolean risingStepJump = next.y > mob.getY() + 0.15D && !mob.onGround() && mob.getDeltaMovement().y > -0.05D;
        if (risingStepJump) {
            if (state.holdTicks > 0) state.holdTicks--;
            return;
        }

        if (state.holdTicks > 0) {
            if (tickActiveHold(mob, next, dirX, dirZ, dist, gameTime, state)) {
                return;
            }
        }

        // Avoid circling when the final destination node itself is occupied by a chest/bed hitbox.
        if (path.getNextNodeIndex() >= path.getNodeCount() - 1 && dist < 1.15D
                && !mob.level().noCollision(mob, offsetBox(mob, next.x, next.z))) {
            return;
        }

        boolean elevationChange = Math.abs(next.y - mob.getY()) > 0.20D;
        int blockThreshold = elevationChange ? STEP_STUCK_TICKS : BLOCK_STUCK_TICKS;
        if (mob.horizontalCollision && progress < 0.025D) {
            state.blockStuckTicks++;
        } else {
            state.blockStuckTicks = Math.max(0, state.blockStuckTicks - 1);
        }

        if (mob.onGround() && progress < 0.015D && hasForwardBlockingEntity(mob, dirX, dirZ)) {
            state.entityStuckTicks++;
        } else {
            state.entityStuckTicks = Math.max(0, state.entityStuckTicks - 1);
        }

        boolean entityTriggered = state.entityStuckTicks >= ENTITY_STUCK_TICKS;
        if (state.blockStuckTicks < blockThreshold && !entityTriggered) {
            return;
        }

        Candidate chosen = findBestCandidate(mob, path, next, dirX, dirZ, gameTime, state, entityTriggered);
        if (chosen == null && entityTriggered && mob.horizontalCollision) {
            chosen = findBestCandidate(mob, path, next, dirX, dirZ, gameTime, state, false);
        }
        state.blockStuckTicks = 0;
        state.entityStuckTicks = 0;
        if (chosen == null) return;

        state.startHold(mob.getX(), mob.getZ(), chosen.clearX, chosen.clearZ, dirX, dirZ,
                chosen.side, chosen.shift, gameTime);
        applySteer(mob, chosen.clearX + dirX * 0.10D, move.getWantedY(), chosen.clearZ + dirZ * 0.10D);
    }

    private static boolean tickActiveHold(
            Mob mob, Vec3 next, double dirX, double dirZ, double dist, long gameTime, SteerState state) {
        if (dirX * state.dirX + dirZ * state.dirZ < 0.70D || Math.abs(next.y - mob.getY()) > 0.25D) {
            state.holdTicks = 0;
            return false;
        }
        double fwdMoved = (mob.getX() - state.startX) * state.dirX + (mob.getZ() - state.startZ) * state.dirZ;
        double lookAhead = Math.min(0.45D, dist);
        if (fwdMoved >= Math.max(0.65D, state.shift + 0.35D)
                && !mob.horizontalCollision
                && isSafeStep(mob, mob.getX() + dirX * lookAhead, mob.getZ() + dirZ * lookAhead, false)) {
            state.holdTicks = 0;
            return false;
        }

        double holdProgress = (mob.getX() - state.holdLastX) * state.dirX
                + (mob.getZ() - state.holdLastZ) * state.dirZ;
        state.holdLastX = mob.getX();
        state.holdLastZ = mob.getZ();
        if (mob.horizontalCollision && holdProgress < 0.025D) {
            if (++state.holdBlockedTicks >= FLIP_SIDE_TICKS) {
                state.preferredSide = -state.activeSide;
                state.lastSideTick = gameTime;
                state.holdTicks = 0;
                return false;
            }
        } else {
            state.holdBlockedTicks = 0;
        }

        state.holdTicks--;
        boolean forwardSafe = isSafeStep(mob, mob.getX() + state.dirX * 0.30D, mob.getZ() + state.dirZ * 0.30D, false);
        double[] target = computeHoldTarget(
                mob.getX(), mob.getZ(), state.clearX, state.clearZ, state.dirX, state.dirZ, forwardSafe);
        if (!isSafeStep(mob, mob.getX() + Math.max(-0.45D, Math.min(0.45D, target[0] - mob.getX())),
                mob.getZ() + Math.max(-0.45D, Math.min(0.45D, target[1] - mob.getZ())), false)) {
            state.holdTicks = 0;
            return false;
        }
        applySteer(mob, target[0], mob.getMoveControl().getWantedY(), target[1]);
        return true;
    }

    private static boolean awaitingDoorOpen(Mob mob, Vec3 next, double dirX, double dirZ) {
        if (mob.goalSelector.getRunningGoals().anyMatch(g -> g.getGoal() instanceof DoorInteractGoal)) {
            return true;
        }
        Level level = mob.level();
        BlockPos mobPos = mob.blockPosition();
        BlockPos nextPos = BlockPos.containing(next.x, mob.getY() + 0.25D, next.z);
        BlockPos aheadPos = BlockPos.containing(mob.getX() + dirX * 0.75D, mob.getY() + 0.25D, mob.getZ() + dirZ * 0.75D);
        return isClosedPassage(mob, level, mobPos)
                || (!nextPos.equals(mobPos) && isClosedPassage(mob, level, nextPos))
                || (!aheadPos.equals(mobPos) && !aheadPos.equals(nextPos) && isClosedPassage(mob, level, aheadPos));
    }

    private static boolean isClosedPassage(Mob mob, Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            return !state.getValue(DoorBlock.OPEN);
        }
        if (state.getBlock() instanceof FenceGateBlock) {
            return !state.getValue(FenceGateBlock.OPEN);
        }
        if (state.getBlock() instanceof OpenableDoor openable) {
            return !openable.isOpen(state, level, pos);
        }
        if (CompanionOpenDoorGoal.opensPassages(mob) && CompanionOpenDoorGoal.openablePassage(level, pos, state, mob)) {
            for (Property<?> prop : state.getProperties()) {
                if ("open".equals(prop.getName()) && prop.getValueClass() == Boolean.class) {
                    return Boolean.FALSE.equals(state.getValue(prop));
                }
            }
            return !state.getCollisionShape(level, pos).isEmpty();
        }
        return false;
    }

    private static void applySteer(Mob mob, double targetX, double targetY, double targetZ) {
        var move = mob.getMoveControl();
        move.setWantedPosition(targetX, targetY, targetZ, move.getSpeedModifier());
    }

    private static Candidate findBestCandidate(
            Mob mob, Path path, Vec3 next, double dirX, double dirZ, long gameTime, SteerState state, boolean avoidEntities) {
        double nextNextDx = 0.0D, nextNextDz = 0.0D;
        int nextIdx = path.getNextNodeIndex();
        if (nextIdx + 1 < path.getNodeCount()) {
            Vec3 ahead = path.getEntityPosAtNode(mob, nextIdx + 1);
            nextNextDx = ahead.x - next.x;
            nextNextDz = ahead.z - next.z;
        }
        int recentSide = gameTime - state.lastSideTick <= SIDE_MEMORY_TICKS ? state.preferredSide : 0;
        int[] order = lateralDirectionOrder(dirX, dirZ, nextNextDx, nextNextDz, recentSide);

        Candidate primary = probeOffsets(mob, dirX, dirZ, order, LATERAL_PROBES, 0.45D, avoidEntities);
        if (primary != null) return primary;
        return probeOffsets(mob, dirX, dirZ, order, TIGHT_PROBES, 0.22D, avoidEntities);
    }

    private static Candidate probeOffsets(
            Mob mob, double dirX, double dirZ, int[] order, double[] shifts, double forwardStep, boolean avoidEntities) {
        for (double shift : shifts) {
            for (int side : order) {
                double px = -dirZ * side;
                double pz = dirX * side;
                double cx = mob.getX() + px * shift;
                double cz = mob.getZ() + pz * shift;
                if (isSafeStep(mob, cx, cz, avoidEntities)
                        && isSafeStep(mob, cx + dirX * forwardStep, cz + dirZ * forwardStep, avoidEntities)) {
                    return new Candidate(cx, cz, side, shift);
                }
            }
        }
        return null;
    }

    static int[] lateralDirectionOrder(
            double dirX, double dirZ, double aheadDx, double aheadDz, int recentPreferredSide) {
        if (recentPreferredSide == 1 || recentPreferredSide == -1) {
            return new int[]{recentPreferredSide, -recentPreferredSide};
        }
        double rightDot = (-dirZ) * aheadDx + dirX * aheadDz;
        if (rightDot < -0.15D) {
            return new int[]{-1, 1};
        }
        return new int[]{1, -1};
    }

    static double[] computeHoldTarget(
            double mobX, double mobZ, double clearX, double clearZ, double dirX, double dirZ, boolean forwardSafe) {
        if (!forwardSafe) {
            return new double[]{clearX + dirX * 0.10D, clearZ + dirZ * 0.10D};
        }
        double px = -dirZ;
        double pz = dirX;
        double perpDelta = (clearX - mobX) * px + (clearZ - mobZ) * pz;
        return new double[]{
            mobX + px * perpDelta + dirX * 0.75D,
            mobZ + pz * perpDelta + dirZ * 0.75D
        };
    }

    private static boolean canSteer(Mob mob) {
        return mob instanceof ChangedEntity
                && !mob.isNoAi()
                && !mob.isSleeping()
                && !mob.isPassenger()
                && !mob.noPhysics
                && !mob.level().isClientSide
                && !mob.isInWaterOrBubble()
                && !mob.isInLava()
                && !mob.onClimbable()
                && !(mob.getNavigation() instanceof WaterBoundPathNavigation);
    }

    private static AABB offsetBox(Mob mob, double targetX, double targetZ) {
        double lift = mob.getBbHeight() < 1.935F ? 0.06D : 0.01D;
        return mob.getBoundingBox().move(targetX - mob.getX(), lift, targetZ - mob.getZ());
    }

    private static boolean isSafeStep(Mob mob, double targetX, double targetZ, boolean avoidEntities) {
        Level level = mob.level();
        BlockPos checkPos = BlockPos.containing(targetX, mob.getY(), targetZ);
        if (!level.isLoaded(checkPos)) return false;

        AABB bodyBox = offsetBox(mob, targetX, targetZ);
        if (!level.noCollision(mob, bodyBox)) return false;

        // Require floor support within half a block (slabs/stairs ok, 1+ block drops rejected).
        AABB supportSlice = new AABB(
                targetX - 0.18D, mob.getY() - 0.58D, targetZ - 0.18D,
                targetX + 0.18D, mob.getY() + 0.02D, targetZ + 0.18D);
        if (level.noCollision(mob, supportSlice)) return false;

        AABB liquidBox = new AABB(
                targetX - 0.25D, mob.getY() - 0.50D, targetZ - 0.25D,
                targetX + 0.25D, mob.getY() + 0.10D, targetZ + 0.25D);
        if (level.containsAnyLiquid(liquidBox)) return false;
        if (PlayerOutpostGoal.onCampfire(level, bodyBox.inflate(0.05D, 0.15D, 0.05D))) return false;

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        if (isHazardPathType(WalkNodeEvaluator.getBlockPathTypeStatic(level, cursor.set(targetX, mob.getY() + 0.05D, targetZ)))
                || isHazardPathType(WalkNodeEvaluator.getBlockPathTypeStatic(level, cursor.set(targetX, mob.getY() - 0.25D, targetZ)))) {
            return false;
        }
        return !avoidEntities || !hasBlockingEntity(mob, bodyBox);
    }

    private static boolean isHazardPathType(BlockPathTypes type) {
        return type == BlockPathTypes.LAVA
                || type == BlockPathTypes.DAMAGE_FIRE
                || type == BlockPathTypes.DANGER_FIRE
                || type == BlockPathTypes.DAMAGE_OTHER
                || type == BlockPathTypes.DANGER_OTHER
                || type == BlockPathTypes.DAMAGE_CAUTIOUS
                || type == BlockPathTypes.STICKY_HONEY;
    }

    private static boolean hasForwardBlockingEntity(Mob mob, double dirX, double dirZ) {
        AABB ahead = mob.getBoundingBox().move(dirX * 0.45D, 0.0D, dirZ * 0.45D);
        return hasBlockingEntity(mob, ahead);
    }

    private static boolean hasBlockingEntity(Mob mob, AABB box) {
        return !mob.level().getEntities(mob, box, e -> e.isAlive()
                && e instanceof LivingEntity
                && e.isPushable()
                && !(e instanceof HangingEntity)).isEmpty();
    }

    private record Candidate(double clearX, double clearZ, int side, double shift) {}

    private static final class SteerState {
        double lastX, lastZ;
        double startX, startZ;
        double holdLastX, holdLastZ;
        double clearX, clearZ;
        double dirX, dirZ;
        double shift;
        long lastTick, lastSideTick = -1000L;
        int blockStuckTicks, entityStuckTicks;
        int holdTicks, holdBlockedTicks;
        int activeSide, preferredSide;

        SteerState(double x, double z, long tick) {
            reset(x, z, tick);
        }

        void reset(double x, double z, long tick) {
            lastX = x;
            lastZ = z;
            lastTick = tick;
            blockStuckTicks = 0;
            entityStuckTicks = 0;
            holdTicks = 0;
            holdBlockedTicks = 0;
        }

        void startHold(double sx, double sz, double cx, double cz, double dx, double dz, int side, double shiftAmt, long tick) {
            startX = sx;
            startZ = sz;
            holdLastX = sx;
            holdLastZ = sz;
            clearX = cx;
            clearZ = cz;
            dirX = dx;
            dirZ = dz;
            activeSide = side;
            preferredSide = side;
            lastSideTick = tick;
            shift = shiftAmt;
            holdTicks = HOLD_TICKS;
            holdBlockedTicks = 0;
        }
    }
}
