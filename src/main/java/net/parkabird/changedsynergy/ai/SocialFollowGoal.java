package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;

/** A non-bonded acquaintance can walk with one player when invited. */
public final class SocialFollowGoal extends Goal {
    public static final int PRIORITY = -1;
    private static final double FOLLOW_SPEED = 0.35D;
    private static final double TELEPORT_DISTANCE = 28.0D;
    private static final double STUCK_TELEPORT_DISTANCE = 10.0D;

    private final ChangedEntity mob;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer player;
    private int repathTicks;
    private int teleportRetryTicks;
    private float oldWaterCost;

    public SocialFollowGoal(ChangedEntity mob) {
        this.mob = mob;
        this.followNavigation = new CompanionFollowNavigation(mob);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)) {
            return false;
        }
        player = CreaturePersonality.socialPartner(mob);
        return player != null
                && !CreatureSettlementService.hasCargo(mob)
                && player.isAlive()
                && !player.isSpectator()
                && mob.getTarget() == null
                && LatexSocialMemory.petOwnerUuid(mob).isEmpty()
                && !LatexSocialMemory.hasActiveBond(mob)
                && !LatexSocialMemory.isProvoked(mob, player)
                && !SocialAudienceGoal.isActive(mob)
                && movementAvailable()
                && player.level() == mob.level();
    }

    @Override
    public boolean canContinueToUse() {
        return player != null
                && !CreatureSettlementService.hasCargo(mob)
                && player.isAlive()
                && !player.isSpectator()
                && player.level() == mob.level()
                && CreaturePersonality.isSocialFollowing(mob, player)
                && !LatexSocialMemory.isProvoked(mob, player)
                && mob.getTarget() == null
                && !SocialAudienceGoal.isActive(mob)
                && movementAvailable();
    }

    @Override
    public void start() {
        repathTicks = 0;
        teleportRetryTicks = 0;
        oldWaterCost = mob.getPathfindingMalus(BlockPathTypes.WATER);
        mob.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        mob.getNavigation().stop();
        followNavigation.reset();
    }

    @Override
    public void tick() {
        if (player == null) {
            return;
        }
        if (teleportRetryTicks > 0) {
            teleportRetryTicks--;
        }
        double distanceSqr = mob.distanceToSqr(player);
        double stopDistance = PlayerRelationshipSettings
                .friendStopDistance(player);
        double startDistance = PlayerRelationshipSettings
                .friendStartDistance(player);
        followNavigation.tickProgress(
                distanceSqr > stopDistance * stopDistance);
        if (distanceSqr <= stopDistance * stopDistance) {
            mob.getNavigation().stop();
            repathTicks = 0;
            return;
        }
        if (distanceSqr <= startDistance * startDistance
                && mob.getNavigation().isDone()) {
            followNavigation.resetProgress();
            return;
        }
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
            repathTicks = 5;
            Level level = mob.level();
            boolean farAway = distanceSqr
                    >= TELEPORT_DISTANCE * TELEPORT_DISTANCE;
            boolean stuckAway = followNavigation.isStalled()
                    && distanceSqr >= STUCK_TELEPORT_DISTANCE
                            * STUCK_TELEPORT_DISTANCE;
            if ((farAway || stuckAway)
                    && teleportRetryTicks <= 0
                    && !mob.isPassenger()
                    && !mob.isLeashed()
                    && level instanceof ServerLevel serverLevel) {
                if (BondedTeleportSafety.teleportNearOwner(serverLevel, mob, player)) {
                    followNavigation.reset();
                    return;
                }
                teleportRetryTicks = 60;
            }
            followNavigation.moveToward(player, FOLLOW_SPEED);
        }
    }

    @Override
    public void stop() {
        player = null;
        mob.getNavigation().stop();
        mob.setPathfindingMalus(BlockPathTypes.WATER, oldWaterCost);
        followNavigation.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean movementAvailable() {
        return mob.isAlive()
                && !mob.isNoAi()
                && !mob.isPassenger()
                && !mob.isLeashed()
                && !ChangedAddonCompat.isGrabberBusy(mob)
                && HypnosisQteService.getActiveVictim(mob) == null;
    }
}
