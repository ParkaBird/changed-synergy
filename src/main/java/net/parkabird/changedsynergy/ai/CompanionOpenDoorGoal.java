package net.parkabird.changedsynergy.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.ltxprogrammer.changed.block.AbstractLabDoor;
import net.ltxprogrammer.changed.block.entity.OpenableDoor;
import net.minecraft.world.entity.ai.goal.OpenDoorGoal;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.mixin.FenceGateSoundAccessor;

/** Lets slow walkers finish crossing instead of closing a door on their own body. */
public final class CompanionOpenDoorGoal extends OpenDoorGoal {
    private int elapsed;
    private boolean facility;
    private Block passage;
    private boolean openedPassage;
    private double approachX, approachZ;
    public CompanionOpenDoorGoal(Mob mob) { super(mob, true); }

    @Override public boolean canUse() {
        facility = false;
        passage = null;
        openedPassage = false;
        if (super.canUse()) return true;
        if (findPassage()) return true;
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
            if (state.getBlock() instanceof AbstractLabDoor
                    && ((OpenableDoor)state.getBlock()).isOpen(state, mob.level(), pos)) {
                facility = true;
                return true;
            }
        }
        return false;
    }

    /** Only walkers already using our native door goal may plan to open these blocks. */
    public static boolean opensPassages(Mob mob) {
        return mob.getNavigation() instanceof GroundPathNavigation navigation && navigation.canOpenDoors()
                && mob.goalSelector.getAvailableGoals().stream().anyMatch(g->g.getGoal() instanceof CompanionOpenDoorGoal);
    }

    public static boolean openablePassage(BlockGetter level, BlockPos pos, BlockState state, Mob mob) {
        if (state.getBlock() instanceof FenceGateBlock) return true;
        if (!(state.getBlock() instanceof TrapDoorBlock trapdoor)
                || !trapdoor.type.canOpenByHand()) return false;
        // A hatch used as a bridge or floor must not turn into an unsupported route.
        for (int depth=1;depth<=Math.ceil(mob.getBbHeight())+1;depth++) {
            var below=pos.below(depth); var support=level.getBlockState(below);
            if (!support.getFluidState().isEmpty()) return false;
            if (!support.getCollisionShape(level,below).isEmpty()) return support.isFaceSturdy(level,below,Direction.UP);
        }
        return false;
    }

    private boolean findPassage() {
        if (!opensPassages(mob) || mob.isSleeping() || mob.isPassenger() || mob.isNoAi()) return false;
        var path=mob.getNavigation().getPath();
        if (path==null || path.isDone()) return false;
        for (int i=Math.max(0,path.getNextNodeIndex()-1);i<Math.min(path.getNodeCount(),path.getNextNodeIndex()+2);i++) {
            var node=path.getNode(i);
            for (int dy=0;dy<Math.ceil(mob.getBbHeight());dy++) {
                var pos=new BlockPos(node.x,node.y+dy,node.z);
                if (pos.getY()<mob.getBoundingBox().minY-.01 || !mob.level().hasChunkAt(pos)
                        || mob.distanceToSqr(pos.getX()+.5,mob.getY(),pos.getZ()+.5)>2.25) continue;
                var state=mob.level().getBlockState(pos);
                if (!openablePassage(mob.level(),pos,state,mob) || state.getValue(BlockStateProperties.OPEN)) continue;
                passage=state.getBlock();doorPos=pos;hasDoor=true;return true;
            }
        }
        return false;
    }

    @Override protected boolean isOpen() {
        if (passage==null) return super.isOpen();
        var state=mob.level().getBlockState(doorPos);
        return state.is(passage) && state.getValue(BlockStateProperties.OPEN);
    }

    @Override protected void setOpen(boolean open) {
        if (passage==null) {super.setOpen(open);return;}
        var level=mob.level();var state=level.getBlockState(doorPos);
        if (!state.is(passage) || state.getValue(BlockStateProperties.OPEN)==open
                || !open && (!openedPassage || state.getValue(BlockStateProperties.POWERED))) return;
        if (open && !openablePassage(level,doorPos,state,mob)) return;
        if (passage instanceof FenceGateBlock && open && state.getValue(FenceGateBlock.FACING)==mob.getDirection().getOpposite())
            state=state.setValue(FenceGateBlock.FACING,mob.getDirection());
        level.setBlock(doorPos,state.setValue(BlockStateProperties.OPEN,open),passage instanceof FenceGateBlock?10:2);
        var sound=passage instanceof FenceGateBlock gate
                ? open?((FenceGateSoundAccessor)gate).changedSynergy$openSound():((FenceGateSoundAccessor)gate).changedSynergy$closeSound()
                : open?((TrapDoorBlock)passage).type.trapdoorOpen():((TrapDoorBlock)passage).type.trapdoorClose();
        level.playSound(null,doorPos,sound,SoundSource.BLOCKS,1,level.random.nextFloat()*.1F+.9F);
        level.gameEvent(mob,open?GameEvent.BLOCK_OPEN:GameEvent.BLOCK_CLOSE,doorPos);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED))
            level.scheduleTick(doorPos,net.minecraft.world.level.material.Fluids.WATER,net.minecraft.world.level.material.Fluids.WATER.getTickDelay(level));
        if (open) openedPassage=true;
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

    /** Called after native navigation chooses its steering point, before movement control. */
    public static void slidePastOpenTrapdoor(Mob mob) {
        if (!mob.horizontalCollision || mob.isNoAi() || mob.isSleeping() || mob.isPassenger()
                || mob.noPhysics || mob.level().isClientSide) return;
        var path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) return;
        var next = path.getNextEntityPos(mob);
        var box = mob.getBoundingBox();
        // The path may already have advanced beyond the panel; include the whole body.
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(box.minX-.1,box.minY,box.minZ-.1),
                BlockPos.containing(box.maxX+.1,box.maxY,box.maxZ+.1))) {
            if (!mob.level().hasChunkAt(pos)) continue;
            var state = mob.level().getBlockState(pos);
            if (!(state.getBlock() instanceof TrapDoorBlock) || !state.getValue(TrapDoorBlock.OPEN)) continue;
            var shape = state.getCollisionShape(mob.level(), pos);
            if (shape.isEmpty()) continue;
            AABB slab = shape.bounds().move(pos);
            if (!box.inflate(.15).intersects(slab)) continue;
            boolean alongX = slab.getZsize() < .25 && Math.abs(next.x-mob.getX()) > .05;
            boolean alongZ = slab.getXsize() < .25 && Math.abs(next.z-mob.getZ()) > .05;
            if (!alongX && !alongZ) continue;
            double safe = alongX ? slab.getCenter().z < pos.getZ()+.5
                    ? slab.maxZ+mob.getBbWidth()/2+.02 : slab.minZ-mob.getBbWidth()/2-.02
                    : slab.getCenter().x < pos.getX()+.5
                    ? slab.maxX+mob.getBbWidth()/2+.02 : slab.minX-mob.getBbWidth()/2-.02;
            double offset = safe-(alongX ? mob.getZ() : mob.getX());
            if (Math.abs(offset) > .5 || !mob.level().noCollision(mob,
                    box.move(alongX?0:offset,0,alongX?offset:0).deflate(1E-7))) continue;
            mob.getMoveControl().setWantedPosition(alongX?next.x:safe,next.y,alongX?safe:next.z,
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
