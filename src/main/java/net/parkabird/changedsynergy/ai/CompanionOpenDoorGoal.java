package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.block.AbstractLabDoor;
import net.ltxprogrammer.changed.block.entity.OpenableDoor;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.mixin.FenceGateSoundAccessor;

/** Lets slow walkers finish crossing doors and ducks tall Changed walkers under 2-block clearances. */
public final class CompanionOpenDoorGoal extends OpenDoorGoal {
    private static final ThreadLocal<Integer> CROUCH_PLAN_DEPTH = ThreadLocal.withInitial(() -> 0);

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
    private int crouchElapsed;
    private boolean crouchActive;
    private Block passage;
    private boolean openedPassage;
    private double approachX, approachZ;

    public CompanionOpenDoorGoal(Mob mob) { super(mob, true); }

    public static boolean canAutoCrouch(ChangedEntity creature) {
        return creature.getUnderlyingPlayer() == null
                && !creature.isVehicle()
                && creature.isAlive()
                && creature.getDimensions(Pose.STANDING).height >= 1.98F
                && creature.getDimensions(Pose.CROUCHING).height < 1.95F;
    }

    @Override public boolean canUse() {
        elapsed = 0;
        crouchElapsed = 0;
        passage = null;
        openedPassage = false;
        hasDoor = false;
        boolean doorReady = tryAcquireDoor();
        crouchActive = shouldCrouchNow();
        return doorReady || crouchActive;
    }

    private boolean tryAcquireDoor() {
        if (super.canUse()) return true;
        return findPassage();
    }

    private boolean shouldCrouchNow() {
        if (mob.isSleeping() || mob.isPassenger() || mob.isVehicle() || mob.isNoAi() || !fitsPose(Pose.CROUCHING)) {
            return false;
        }
        if (mob.getPose() == Pose.CROUCHING && !fitsPose(Pose.STANDING)) return true;
        if (!fitsPose(Pose.STANDING)) return true;
        if (hasOpenLabDoorCollision()) return true;
        return mob instanceof ChangedEntity creature && canAutoCrouch(creature) && needsPathCrouch(creature);
    }

    private boolean hasOpenLabDoorCollision() {
        if (!mob.horizontalCollision) return false;
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        for (int i = Math.max(0, path.getNextNodeIndex() - 1);
                i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
            var node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (!mob.level().hasChunkAt(pos) || mob.distanceToSqr(pos.getCenter()) > 4) continue;
            var state = mob.level().getBlockState(pos);
            if (state.getBlock() instanceof AbstractLabDoor
                    && ((OpenableDoor) state.getBlock()).isOpen(state, mob.level(), pos)) {
                return true;
            }
        }
        return false;
    }

    private boolean needsPathCrouch(ChangedEntity creature) {
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        EntityDimensions standing = creature.getDimensions(Pose.STANDING);
        EntityDimensions crouched = creature.getDimensions(Pose.CROUCHING);
        int start = Math.max(0, path.getNextNodeIndex() - 1);
        int end = Math.min(path.getNodeCount(), path.getNextNodeIndex() + 3);
        for (int i = start; i < end; i++) {
            var node = path.getNode(i);
            Vec3 center = Vec3.atBottomCenterOf(node.asBlockPos());
            if (mob.distanceToSqr(center) > 9.0D) continue;
            AABB standBox = standing.makeBoundingBox(center).deflate(1.0E-7D);
            AABB crouchBox = crouched.makeBoundingBox(center).deflate(1.0E-7D);
            if (!mob.level().noCollision(mob, standBox) && mob.level().noCollision(mob, crouchBox)) {
                return true;
            }
        }
        return false;
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
        crouchElapsed = 0;
        if (hasDoor) startDoor();
        if (crouchActive && fitsPose(Pose.CROUCHING)) mob.setPose(Pose.CROUCHING);
    }

    private void startDoor() {
        approachX = mob.getX() - doorPos.getX() - .5;
        approachZ = mob.getZ() - doorPos.getZ() - .5;
        super.start();
    }

    @Override public void tick() {
        if (shouldCrouchNow()) {
            crouchActive = true;
            if (mob.getPose() != Pose.CROUCHING && fitsPose(Pose.CROUCHING)) mob.setPose(Pose.CROUCHING);
        }
        if (crouchActive) crouchElapsed++;
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
        boolean crouchNeeded = crouchActive && !mob.isSleeping() && !mob.isPassenger() && !mob.isVehicle()
                && mob.getPose() == Pose.CROUCHING
                && (crouchElapsed < 20 || shouldCrouchNow() || !fitsPose(Pose.STANDING));
        return doorStillActive() || crouchNeeded;
    }

    @Override public void stop() {
        clearDoor();
        if (crouchActive) {
            if (mob.getPose() == Pose.CROUCHING && (mob.isSleeping() || fitsPose(Pose.STANDING))) {
                mob.setPose(Pose.STANDING);
            }
            crouchActive = false;
        }
    }

    private boolean fitsPose(Pose pose) {
        return mob.level().noCollision(mob, mob.getDimensions(pose)
                .makeBoundingBox(mob.position()).deflate(1.0E-7));
    }
}
