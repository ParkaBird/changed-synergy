package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.pathfinder.Path;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import net.parkabird.changedsynergy.entity.LatexSilkMoth;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;

/** An idle preference, below work and social goals. Never treats a bright hazard as a destination. */
public final class MothLightRestGoal extends Goal {
    private final LatexSilkMoth moth;
    private BlockPos destination;
    private Path path;
    private int remaining;
    private long nextSearch;

    public MothLightRestGoal(LatexSilkMoth moth) {
        this.moth=moth;
        setFlags(EnumSet.of(Flag.MOVE));
    }
    private boolean idle() {
        return moth.isAlive() && !moth.isNoAi() && !moth.isPassenger() && !moth.isLeashed()
                && moth.getUnderlyingPlayer()==null && !TrueTransfurCompat.isInstinctBody(moth)
                && !moth.isSleeping() && !moth.isUsingItem()
                && moth.getTarget()==null && !moth.isAggressive() && !moth.isInWaterOrBubble()
                && !PlayerOutpostService.assigned(moth)
                && !(LatexSocialMemory.petOwnerUuid(moth).isPresent() && LatexSocialMemory.isFollowingOwner(moth))
                && CreaturePersonality.socialPartner(moth)==null && !SocialAudienceGoal.isActive(moth)
                && !CreaturePeerInteractionService.active(moth) && !CreatureGrabService.isGrabberBusy(moth)
                && !TakeoverService.carrying(moth);
    }
    @Override public boolean canUse() {
        if (!idle() || moth.level().isClientSide || moth.level().getGameTime()<nextSearch
                || !moth.getNavigation().isDone() || !SynergyPerformanceTracker.allowBackground(
                    moth,SynergyPerformanceTracker.Feature.COMMUNITY,1)) return false;
        // GoalSelector checks do not run every tick; an exact 100-tick phase can be missed.
        // Own the scan cooldown here while retaining the shared AI budget gate.
        nextSearch=moth.level().getGameTime()+100;
        destination=null;path=null;
        int current=moth.level().getBrightness(LightLayer.BLOCK,moth.blockPosition());
        var candidates=new java.util.ArrayList<BlockPos>();
        for (BlockPos pos : BlockPos.betweenClosed(moth.blockPosition().offset(-6,-2,-6),moth.blockPosition().offset(6,2,6))) {
            if (!moth.level().hasChunkAt(pos)) continue;
            int light=moth.level().getBrightness(LightLayer.BLOCK,pos);
            if (light<10 || light<current || !safeStand(pos)) continue;
            candidates.add(pos.immutable());
        }
        candidates.sort(java.util.Comparator.comparingDouble(pos ->
                pos.distSqr(moth.blockPosition())*0.12-moth.level().getBrightness(LightLayer.BLOCK,pos)*4));
        for (BlockPos pos : candidates.stream().limit(8).toList()) {
            var candidate=moth.getNavigation().createPath(pos,0);
            if (candidate==null || !candidate.canReach()) continue;
            boolean safe=true;
            for (int i=0;i<candidate.getNodeCount();i++) {
                var node=candidate.getNode(i);
                if (!safeStand(new BlockPos(node.x,node.y,node.z))) { safe=false;break; }
            }
            if (safe) { destination=pos;path=candidate;return true; }
        }
        return destination!=null;
    }
    private boolean safeStand(BlockPos feet) {
        if (!moth.level().hasChunkAt(feet) || !moth.level().getFluidState(feet).isEmpty()
                || !moth.level().getBlockState(feet.below()).isFaceSturdy(
                    moth.level(),feet.below(),net.minecraft.core.Direction.UP)) return false;
        var body=moth.getBoundingBox().move(feet.getX()+0.5-moth.getX(),feet.getY()-moth.getY(),feet.getZ()+0.5-moth.getZ());
        if (moth.level().getBlockCollisions(moth,body).iterator().hasNext()) return false;
        for (BlockPos pos : BlockPos.betweenClosed(feet.offset(-1,-1,-1),feet.offset(1,2,1))) {
            if (!moth.level().hasChunkAt(pos)) return false;
            var state=moth.level().getBlockState(pos);
            if (state.getFluidState().is(FluidTags.LAVA) || state.is(BlockTags.FIRE)
                    || CampfireBlock.isLitCampfire(state) || state.is(Blocks.MAGMA_BLOCK)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH)
                    || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.POWDER_SNOW)) return false;
        }
        return true;
    }
    @Override public void start() { remaining=240; moth.getNavigation().moveTo(path,0.75); }
    @Override public boolean canContinueToUse() {
        return remaining>0 && idle() && destination!=null && safeStand(destination)
                && moth.level().getBrightness(LightLayer.BLOCK,destination)>=10
                && (!moth.getNavigation().isDone() || moth.position().distanceToSqr(net.minecraft.world.phys.Vec3.atBottomCenterOf(destination))<2.25);
    }
    @Override public void tick() {
        remaining--;
        if (!safeStand(moth.blockPosition())) { remaining=0;moth.getNavigation().stop(); }
    }
    @Override public void stop() { moth.getNavigation().stop();destination=null;path=null; }
}
