package net.parkabird.changedsynergy.ai;

import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.mixin.FenceGateSoundAccessor;

/**
 * Lets slow walkers finish crossing doors/passages and ducks Changed walkers
 * through any 2-block clearance that blocks standing while fitting crouching.
 */
public final class CompanionOpenDoorGoal extends OpenDoorGoal {
    private static final ThreadLocal<Integer> CROUCH_PLAN_DEPTH = ThreadLocal.withInitial(() -> 0);
    private static final int AUTO_CROUCH_HOLD_TICKS = 8;
    private static final WeakHashMap<ChangedEntity, AutoCrouchState> AUTO_CROUCH_STATES = new WeakHashMap<>();

    private static final class AutoCrouchState {
        boolean active;
        int holdTicks;
    }

    public static void beginCrouchPlan() {
        CROUCH_PLAN_DEPTH.set(CROUCH_PLAN_DEPTH.get() + 1);
    }

    public static void endCrouchPlan() {
        int next = CROUCH_PLAN_DEPTH.get() - 1;
        if (next <= 0) CROUCH_PLAN_DEPTH.remove();
        else CROUCH_PLAN_DEPTH.set(next);
    }

    public static boolean isCrouchPlanning() {
        return CROUCH_PLAN_DEPTH.get() > 0;
    }

    private int elapsed;
    private Block passage;
    private boolean openedPassage;
    private double approachX, approachZ;

    public CompanionOpenDoorGoal(Mob mob) { super(mob, true); }

    public static boolean canAutoCrouch(ChangedEntity creature) {
        if (creature == null || !creature.isAlive() || creature.isRemoved()
                || creature.getUnderlyingPlayer() != null || creature.isVehicle()
                || CreatureSocialProfile.isPermanentlyExcluded(creature)) {
            return false;
        }
        EntityDimensions standing = creature.getDimensions(Pose.STANDING);
        EntityDimensions crouched = creature.getDimensions(Pose.CROUCHING);
        return !standing.fixed && !crouched.fixed && crouched.height < standing.height - 0.05F;
    }

    public static boolean isAutoCrouching(LivingEntity entity) {
        if (!(entity instanceof ChangedEntity creature)) return false;
        AutoCrouchState state = AUTO_CROUCH_STATES.get(creature);
        return state != null && state.active && creature.getPose() == Pose.CROUCHING;
    }

    /** Returns true only on the tick a creature transitions into Pose.CROUCHING. */
    public static boolean tickAutoCrouch(ChangedEntity creature) {
        if (creature == null || creature.level().isClientSide) return false;
        AutoCrouchState state = AUTO_CROUCH_STATES.get(creature);
        if (!canAutoCrouch(creature)
                || creature.isSleeping() || creature.isPassenger() || creature.isNoAi()
                || creature.isInWaterOrBubble() || creature.isInLava() || creature.isSwimming()
                || creature.onClimbable()) {
            if (state != null && state.active) {
                if (creature.getPose() == Pose.CROUCHING
                        && !creature.isSleeping() && !creature.isPassenger() && !creature.isInWaterOrBubble()
                        && !standingBlocked(creature, creature.getDimensions(Pose.STANDING),
                                creature.getX(), creature.getY(), creature.getZ())) {
                    creature.setPose(Pose.STANDING);
                }
                state.active = false;
                state.holdTicks = 0;
            }
            return false;
        }

        boolean needCrouch = needsCrouchPass(creature);
        Pose pose = creature.getPose();
        if (needCrouch) {
            if (state == null) {
                state = new AutoCrouchState();
                AUTO_CROUCH_STATES.put(creature, state);
            }
            boolean transitioned = false;
            if (pose == Pose.STANDING
                    && fitsClearance(creature, creature.getDimensions(Pose.CROUCHING),
                            creature.getX(), creature.getY(), creature.getZ(), -1.0E-4D)) {
                creature.setPose(Pose.CROUCHING);
                transitioned = true;
            }
            if (creature.getPose() == Pose.CROUCHING) {
                state.active = true;
                state.holdTicks = AUTO_CROUCH_HOLD_TICKS;
            }
            return transitioned;
        }

        if (state != null && state.active) {
            if (pose != Pose.CROUCHING) {
                state.active = false;
                state.holdTicks = 0;
                return false;
            }
            if (state.holdTicks > 0) {
                state.holdTicks--;
                return false;
            }
            if (!standingBlocked(creature, creature.getDimensions(Pose.STANDING),
                    creature.getX(), creature.getY(), creature.getZ())) {
                creature.setPose(Pose.STANDING);
                state.active = false;
            }
        }
        return false;
    }

    public static double surfaceYAt(BlockGetter level, BlockPos pos) {
        double floorY = WalkNodeEvaluator.getFloorLevel(level, pos);
        var inside = level.getBlockState(pos).getCollisionShape(level, pos);
        if (!inside.isEmpty()) {
            double topInside = pos.getY() + inside.max(Direction.Axis.Y);
            if (topInside > floorY && topInside <= pos.getY() + 0.6D) {
                floorY = topInside;
            }
        }
        return floorY;
    }

    private static AABB headroomBox(EntityDimensions dims, double x, double footY, double z, double topPad) {
        double hw = Math.max(0.05D, dims.width * 0.5D - 1.0E-4D);
        return new AABB(x - hw, footY + 0.005D, z - hw, x + hw, footY + dims.height + topPad, z + hw);
    }

    private static boolean fitsClearance(
            ChangedEntity creature, EntityDimensions dims, double x, double footY, double z, double topPad) {
        return creature.level().noCollision(creature, headroomBox(dims, x, footY, z, topPad));
    }

    private static boolean standingBlocked(
            ChangedEntity creature, EntityDimensions standing, double x, double footY, double z) {
        // 2.0F bodies graze exact 2.0 ceilings on integer floors; apply a tiny margin >= 1.995F.
        double topPad = standing.height >= 1.995F ? 0.01D : -1.0E-4D;
        return !fitsClearance(creature, standing, x, footY, z, topPad);
    }

    static boolean isCrouchClearanceNeeded(ChangedEntity creature, double x, double footY, double z) {
        EntityDimensions standing = creature.getDimensions(Pose.STANDING);
        EntityDimensions crouched = creature.getDimensions(Pose.CROUCHING);
        if (standing.fixed || crouched.fixed || crouched.height >= standing.height - 0.05F) return false;
        return standingBlocked(creature, standing, x, footY, z)
                && fitsClearance(creature, crouched, x, footY, z, -1.0E-4D);
    }

    private static boolean probePositionNeedsCrouch(ChangedEntity creature, double px, double baseY, double pz) {
        if (isCrouchClearanceNeeded(creature, px, baseY, pz)) return true;
        BlockPos aheadPos = BlockPos.containing(px, baseY + 0.55D, pz);
        if (creature.level().hasChunkAt(aheadPos)) {
            double stepY = surfaceYAt(creature.level(), aheadPos);
            if (stepY > baseY + 0.01D && stepY <= baseY + 0.65D
                    && isCrouchClearanceNeeded(creature, px, stepY, pz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean needsCrouchPass(ChangedEntity creature) {
        EntityDimensions standing = creature.getDimensions(Pose.STANDING);
        if (creature.getPose() == Pose.CROUCHING
                && standingBlocked(creature, standing, creature.getX(), creature.getY(), creature.getZ())) {
            return true;
        }
        if (isCrouchClearanceNeeded(creature, creature.getX(), creature.getY(), creature.getZ())) {
            return true;
        }

        var path = creature.getNavigation().getPath();
        double dx = 0.0D, dz = 0.0D;
        if (path != null && !path.isDone()) {
            Vec3 next = path.getNextEntityPos(creature);
            dx = next.x - creature.getX();
            dz = next.z - creature.getZ();
        } else if (creature.getMoveControl().hasWanted()) {
            dx = creature.getMoveControl().getWantedX() - creature.getX();
            dz = creature.getMoveControl().getWantedZ() - creature.getZ();
        } else if (creature.horizontalCollision) {
            Vec3 vel = creature.getDeltaMovement();
            if (Math.hypot(vel.x, vel.z) > 0.01D) {
                dx = vel.x;
                dz = vel.z;
            } else {
                Vec3 look = creature.getLookAngle();
                dx = look.x;
                dz = look.z;
            }
        }

        double dist = Math.hypot(dx, dz);
        if (dist > 0.02D) {
            double dirX = dx / dist;
            double dirZ = dz / dist;
            double[] steps = creature.horizontalCollision
                    ? new double[]{0.12D, 0.25D, 0.55D, 0.90D}
                    : new double[]{0.25D, 0.55D, 0.90D};
            for (double step : steps) {
                double px = creature.getX() + dirX * step;
                double pz = creature.getZ() + dirZ * step;
                if (probePositionNeedsCrouch(creature, px, creature.getY(), pz)) return true;
            }
            if (creature.horizontalCollision) {
                double latX = -dirZ * 0.16D;
                double latZ = dirX * 0.16D;
                double bx = creature.getX() + dirX * 0.25D;
                double bz = creature.getZ() + dirZ * 0.25D;
                if (probePositionNeedsCrouch(creature, bx + latX, creature.getY(), bz + latZ)
                        || probePositionNeedsCrouch(creature, bx - latX, creature.getY(), bz - latZ)) {
                    return true;
                }
            }
        }

        if (path == null || path.isDone()) return false;
        EntityDimensions crouched = creature.getDimensions(Pose.CROUCHING);
        int start = Math.max(0, path.getNextNodeIndex() - 1);
        int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3);
        for (int i = start; i < end; i++) {
            BlockPos np = path.getNode(i).asBlockPos();
            if (!creature.level().hasChunkAt(np)) continue;
            double cx = np.getX() + 0.5D;
            double cy = surfaceYAt(creature.level(), np);
            double cz = np.getZ() + 0.5D;
            if (Mth.square(creature.getX() - cx) + Mth.square(creature.getY() - cy)
                    + Mth.square(creature.getZ() - cz) > 9.0D) {
                continue;
            }
            if (isCrouchClearanceNeeded(creature, cx, cy, cz)) return true;
            // Open 0.1875F door leaves in 1-wide doorways push wider walkers ~0.10D off block center.
            if (creature.getBbWidth() >= 0.65F && !fitsClearance(creature, crouched, cx, cy, cz, -1.0E-4D)) {
                if (isCrouchClearanceNeeded(creature, cx + 0.10D, cy, cz)
                        || isCrouchClearanceNeeded(creature, cx - 0.10D, cy, cz)
                        || isCrouchClearanceNeeded(creature, cx, cy, cz + 0.10D)
                        || isCrouchClearanceNeeded(creature, cx, cy, cz - 0.10D)) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override public boolean canUse() {
        elapsed = 0;
        passage = null;
        openedPassage = false;
        hasDoor = false;
        return tryAcquireDoor();
    }

    private boolean tryAcquireDoor() {
        if (super.canUse()) return true;
        return findPassage();
    }

    /** Only walkers already using our native door goal may plan to open these blocks. */
    public static boolean opensPassages(Mob mob) {
        return mob.getNavigation() instanceof GroundPathNavigation navigation && navigation.canOpenDoors()
                && mob.goalSelector.getAvailableGoals().stream().anyMatch(g -> g.getGoal() instanceof CompanionOpenDoorGoal);
    }

    public static boolean openablePassage(BlockGetter level, BlockPos pos, BlockState state, Mob mob) {
        if (state.getBlock() instanceof FenceGateBlock) return true;
        if (!(state.getBlock() instanceof TrapDoorBlock trapdoor)
                || !trapdoor.type.canOpenByHand()) return false;
        // A hatch used as a bridge or floor must not turn into an unsupported route.
        for (int depth = 1; depth <= Math.ceil(mob.getBbHeight()) + 1; depth++) {
            var below = pos.below(depth);
            var support = level.getBlockState(below);
            if (!support.getFluidState().isEmpty()) return false;
            if (!support.getCollisionShape(level, below).isEmpty()) return support.isFaceSturdy(level, below, Direction.UP);
        }
        return false;
    }

    private boolean findPassage() {
        if (!opensPassages(mob) || mob.isSleeping() || mob.isPassenger() || mob.isNoAi()) return false;
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
            var node = path.getNode(i);
            for (int dy = 0; dy < Math.ceil(mob.getBbHeight()); dy++) {
                var pos = new BlockPos(node.x, node.y + dy, node.z);
                if (pos.getY() < mob.getBoundingBox().minY - .01 || !mob.level().hasChunkAt(pos)
                        || mob.distanceToSqr(pos.getX() + .5, mob.getY(), pos.getZ() + .5) > 2.25) continue;
                var state = mob.level().getBlockState(pos);
                if (!openablePassage(mob.level(), pos, state, mob) || state.getValue(BlockStateProperties.OPEN)) continue;
                passage = state.getBlock();
                doorPos = pos;
                hasDoor = true;
                return true;
            }
        }
        return false;
    }

    @Override protected boolean isOpen() {
        if (passage == null) return super.isOpen();
        var state = mob.level().getBlockState(doorPos);
        return state.is(passage) && state.getValue(BlockStateProperties.OPEN);
    }

    @Override protected void setOpen(boolean open) {
        if (passage == null) { super.setOpen(open); return; }
        var level = mob.level();
        var state = level.getBlockState(doorPos);
        if (!state.is(passage) || state.getValue(BlockStateProperties.OPEN) == open
                || !open && (!openedPassage || state.getValue(BlockStateProperties.POWERED))) return;
        if (open && !openablePassage(level, doorPos, state, mob)) return;
        if (passage instanceof FenceGateBlock && open && state.getValue(FenceGateBlock.FACING) == mob.getDirection().getOpposite()) {
            state = state.setValue(FenceGateBlock.FACING, mob.getDirection());
        }
        level.setBlock(doorPos, state.setValue(BlockStateProperties.OPEN, open), passage instanceof FenceGateBlock ? 10 : 2);
        var sound = passage instanceof FenceGateBlock gate
                ? open ? ((FenceGateSoundAccessor) gate).changedSynergy$openSound() : ((FenceGateSoundAccessor) gate).changedSynergy$closeSound()
                : open ? ((TrapDoorBlock) passage).type.trapdoorOpen() : ((TrapDoorBlock) passage).type.trapdoorClose();
        level.playSound(null, doorPos, sound, SoundSource.BLOCKS, 1, level.random.nextFloat() * .1F + .9F);
        level.gameEvent(mob, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, doorPos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            level.scheduleTick(doorPos, net.minecraft.world.level.material.Fluids.WATER, net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        }
        if (open) openedPassage = true;
    }

    @Override public void start() {
        elapsed = 0;
        if (hasDoor) startDoor();
    }

    private void startDoor() {
        approachX = mob.getX() - doorPos.getX() - .5;
        approachZ = mob.getZ() - doorPos.getZ() - .5;
        super.start();
    }

    @Override public void tick() {
        if (hasDoor) {
            elapsed++;
            super.tick();
            if (isOpen() && mob.horizontalCollision) slidePastSlab(mob, doorPos);
            if (!doorStillActive()) clearDoor();
        } else if (tryAcquireDoor()) {
            elapsed = 0;
            startDoor();
        }
    }

    private void clearDoor() {
        if (hasDoor && doorPos != null
                && mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(doorPos).inflate(.1),
                        entity -> entity.isAlive() && !entity.isRemoved()).isEmpty()) {
            super.stop();
        }
        hasDoor = false;
        passage = null;
        openedPassage = false;
    }

    private boolean doorStillActive() {
        if (!hasDoor || doorPos == null) return false;
        boolean approaching = approachX * (mob.getX() - doorPos.getX() - .5)
                + approachZ * (mob.getZ() - doorPos.getZ() - .5) >= 0;
        return elapsed < 200 && isOpen() && mob.distanceToSqr(doorPos.getCenter()) < 16
                && (approaching || elapsed < 20 || mob.getBoundingBox().intersects(new AABB(doorPos).inflate(.45)));
    }

    /** Called after native navigation chooses its steering point, before movement control. */
    public static void slidePastOpenTrapdoor(Mob mob) {
        if (!mob.horizontalCollision || mob.isNoAi() || mob.isSleeping() || mob.isPassenger()
                || mob.noPhysics || mob.level().isClientSide) return;
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return;
        var next = path.getNextEntityPos(mob);
        var box = mob.getBoundingBox();
        // The path may already have advanced beyond the panel; include the whole body.
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX - .1, box.minY, box.minZ - .1),
                BlockPos.containing(box.maxX + .1, box.maxY, box.maxZ + .1))) {
            if (!mob.level().hasChunkAt(pos)) continue;
            var state = mob.level().getBlockState(pos);
            if (!(state.getBlock() instanceof TrapDoorBlock) || !state.getValue(TrapDoorBlock.OPEN)) continue;
            var shape = state.getCollisionShape(mob.level(), pos);
            if (shape.isEmpty()) continue;
            AABB slab = shape.bounds().move(pos);
            if (!box.inflate(.15).intersects(slab)) continue;
            boolean alongX = slab.getZsize() < .25 && Math.abs(next.x - mob.getX()) > .05;
            boolean alongZ = slab.getXsize() < .25 && Math.abs(next.z - mob.getZ()) > .05;
            if (!alongX && !alongZ) continue;
            double safe = alongX ? slab.getCenter().z < pos.getZ() + .5
                    ? slab.maxZ + mob.getBbWidth() / 2 + .02 : slab.minZ - mob.getBbWidth() / 2 - .02
                    : slab.getCenter().x < pos.getX() + .5
                    ? slab.maxX + mob.getBbWidth() / 2 + .02 : slab.minX - mob.getBbWidth() / 2 - .02;
            double offset = safe - (alongX ? mob.getZ() : mob.getX());
            if (Math.abs(offset) > .5 || !mob.level().noCollision(mob,
                    box.move(alongX ? 0 : offset, 0, alongX ? offset : 0).deflate(1E-7))) continue;
            mob.getMoveControl().setWantedPosition(alongX ? next.x : safe, next.y, alongX ? safe : next.z,
                    mob.getMoveControl().getSpeedModifier());
            return;
        }
    }

    private static void slidePastSlab(Mob mob, BlockPos pos) {
        var shape = mob.level().getBlockState(pos).getCollisionShape(mob.level(), pos);
        if (shape.isEmpty()) return;
        AABB slab = shape.bounds().move(pos);
        var movement = mob.getDeltaMovement();
        // Wider Changed bodies cannot follow Minecraft's exact block-centre
        // line past an open door slab. Slide towards the free side, without
        // teleporting or increasing forward walking speed.
        if (slab.getZsize() < .25 && mob.getBbWidth() < 1 - slab.getZsize()) {
            double safe = slab.getCenter().z < pos.getZ() + .5
                    ? slab.maxZ + mob.getBbWidth() / 2 + .02 : slab.minZ - mob.getBbWidth() / 2 - .02;
            if (Math.abs(safe - mob.getZ()) > .01)
                mob.setDeltaMovement(movement.x, movement.y,
                        Math.max(-.04, Math.min(.04, movement.z + Math.signum(safe - mob.getZ()) * .025)));
        } else if (slab.getXsize() < .25 && mob.getBbWidth() < 1 - slab.getXsize()) {
            double safe = slab.getCenter().x < pos.getX() + .5
                    ? slab.maxX + mob.getBbWidth() / 2 + .02 : slab.minX - mob.getBbWidth() / 2 - .02;
            if (Math.abs(safe - mob.getX()) > .01)
                mob.setDeltaMovement(Math.max(-.04, Math.min(.04, movement.x + Math.signum(safe - mob.getX()) * .025)),
                        movement.y, movement.z);
        }
    }

    @Override public boolean canContinueToUse() {
        return doorStillActive();
    }

    @Override public void stop() {
        clearDoor();
    }
}
