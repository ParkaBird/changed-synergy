package net.parkabird.changedsynergy.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.ltxprogrammer.changed.block.AbstractLabDoor;
import net.ltxprogrammer.changed.block.AbstractLargeLabDoor;
import net.ltxprogrammer.changed.block.entity.OpenableDoor;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.phys.AABB;

/** Lets slow walkers finish crossing instead of closing a door on their own body. */
public final class CompanionOpenDoorGoal extends OpenDoorGoal {
    private int elapsed;
    private boolean facility;
    private double approachX, approachZ;
    public CompanionOpenDoorGoal(Mob mob) { super(mob, true); }

    @Override public boolean canUse() {
        facility = false;
        if (super.canUse()) return true;
        if (!mob.horizontalCollision || mob.isSleeping() || mob.getPose() != Pose.STANDING
                || !fitsPose(Pose.CROUCHING)) return false;
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return false;
        for (int i = Math.max(0, path.getNextNodeIndex() - 1);
                i < Math.min(path.getNodeCount(), path.getNextNodeIndex() + 2); i++) {
            var node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (!mob.level().hasChunkAt(pos) || mob.distanceToSqr(pos.getCenter()) > 4) continue;
            var state = mob.level().getBlockState(pos);
            if ((state.getBlock() instanceof AbstractLabDoor || state.getBlock() instanceof AbstractLargeLabDoor)
                    && ((OpenableDoor)state.getBlock()).isOpen(state, mob.level(), pos)) {
                facility = true;
                return true;
            }
        }
        return false;
    }

    @Override public void start() {
        elapsed = 0;
        // Ordinary adult bodies are slightly taller than a small lab door's lintel.
        if (facility) { mob.setPose(Pose.CROUCHING); return; }
        approachX = mob.getX() - doorPos.getX() - .5;
        approachZ = mob.getZ() - doorPos.getZ() - .5;
        super.start();
    }
    @Override public void tick() {
        elapsed++;
        if (facility) return;
        super.tick();
        if (!isOpen() || !mob.horizontalCollision) return;
        slidePastSlab(mob, doorPos);
    }

    /** One active path node only; never opens trapdoors or bypasses a solid barrier. */
    public static void slidePastOpenTrapdoor(Mob mob) {
        if (!mob.horizontalCollision || mob.isNoAi() || mob.isSleeping()) return;
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return;
        BlockPos pos = path.getNextNodePos();
        if (!mob.level().hasChunkAt(pos)
                || !mob.getBoundingBox().inflate(.05).intersects(new AABB(pos))) return;
        var state = mob.level().getBlockState(pos);
        if (!(state.getBlock() instanceof TrapDoorBlock) || !state.getValue(TrapDoorBlock.OPEN)) return;
        var next = path.getNextEntityPos(mob);
        double dx = Math.abs(next.x - mob.getX()), dz = Math.abs(next.z - mob.getZ());
        // Only slide along the open slab, never through a slab across the route.
        if (state.getValue(TrapDoorBlock.FACING).getAxis() == Direction.Axis.Z ? dx <= dz : dz <= dx) return;
        slidePastSlab(mob, pos);
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
        if (facility) return mob.getPose() == Pose.CROUCHING
                && (elapsed < 20 || !fitsPose(Pose.STANDING));
        boolean approaching = approachX * (mob.getX() - doorPos.getX() - .5)
                + approachZ * (mob.getZ() - doorPos.getZ() - .5) >= 0;
        return elapsed < 200 && isOpen() && mob.distanceToSqr(doorPos.getCenter()) < 16
                && (approaching || elapsed < 20 || mob.getBoundingBox().intersects(new AABB(doorPos).inflate(.45)));
    }
    @Override public void stop() {
        if (facility) {
            if (mob.getPose() == Pose.CROUCHING) mob.setPose(Pose.STANDING);
            facility = false;
            return;
        }
        // Timeout or an interrupted route may leave the walker in the opening.
        if (mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(doorPos).inflate(.1),
                entity -> entity.isAlive() && !entity.isRemoved()).isEmpty()) super.stop();
    }

    private boolean fitsPose(Pose pose) {
        return mob.level().noCollision(mob, mob.getDimensions(pose)
                .makeBoundingBox(mob.position()).deflate(1.0E-7));
    }
}
