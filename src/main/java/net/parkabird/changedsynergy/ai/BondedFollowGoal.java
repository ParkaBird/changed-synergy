package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;

/** Lets a latex creature follow players it personally transfurred. */
public final class BondedFollowGoal extends Goal {
    public static final int PRIORITY = -1;
    private static final double FOLLOW_SPEED = 0.35D;
    private static final double TELEPORT_DISTANCE = 24.0D;
    private static final double STUCK_TELEPORT_DISTANCE = 8.0D;
    private final ChangedEntity mob;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer player;
    private int repathTicks;
    private int teleportRetryTicks;
    private float oldWaterCost;

    public BondedFollowGoal(ChangedEntity mob) {
        this.mob = mob;
        this.followNavigation = new CompanionFollowNavigation(mob);
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel)
                || !LatexSocialMemory.isFollowingOwner(mob)) {
            return false;
        }

        player = LatexSocialMemory.getPetOwner(mob);
        if (player == null || !player.isAlive() || player.isSpectator()
                || mob.getTarget() != null
                || SocialAudienceGoal.isActive(mob)
                || !movementAvailable()) {
            return false;
        }
        double startDistance = PlayerRelationshipSettings.bondedStartDistance(player);
        return mob.distanceToSqr(net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player)) > startDistance * startDistance;
    }

    @Override
    public boolean canContinueToUse() {
        return player != null && player.isAlive() && !player.isSpectator()
                && LatexSocialMemory.isPetOwner(mob, player)
                && LatexSocialMemory.isFollowingOwner(mob)
                && mob.getTarget() == null
                && !SocialAudienceGoal.isActive(mob)
                && movementAvailable()
                && player.level() == mob.level()
                && mob.distanceToSqr(net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player))
                        > Math.pow(PlayerRelationshipSettings.bondedStopDistance(player), 2.0D);
    }

    @Override
    public void start() {
        CreatureComfortGoal.endSeatedActivity(mob);
        if (CreatureSettlementService.hasCargo(mob)) CreatureSettlementService.dropCargo(mob);
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
        var body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player);
        double distanceSqr = mob.distanceToSqr(body);
        double stopDistance = PlayerRelationshipSettings
                .bondedStopDistance(player);
        double startDistance = PlayerRelationshipSettings
                .bondedStartDistance(player);
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
        mob.getLookControl().setLookAt(body, 30.0F, 30.0F);
        if (--repathTicks <= 0) {
            repathTicks = 5;
            boolean farAway = distanceSqr
                    >= TELEPORT_DISTANCE * TELEPORT_DISTANCE;
            boolean stuckAway = followNavigation.isStalled()
                    && distanceSqr >= STUCK_TELEPORT_DISTANCE
                            * STUCK_TELEPORT_DISTANCE;
            if ((farAway || stuckAway)
                    && teleportRetryTicks <= 0
                    && !mob.isPassenger() && !mob.isLeashed()
                    && mob.level() instanceof ServerLevel level) {
                if (BondedTeleportSafety.teleportNearOwner(level, mob, player)) {
                    followNavigation.reset();
                    return;
                }
                // A flying owner or a cramped room may currently have no safe
                // landing. Retry later instead of repeatedly scanning or
                // dropping the creature into open air.
                teleportRetryTicks = 60;
            }
            followNavigation.moveToward(body, FOLLOW_SPEED);
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
        if (player == null) return false;
        var body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(player);
        return player != null
                && body.isAlive() && !body.isRemoved() && body.level() == mob.level()
                && !(body == player && net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(player))
                && !PlayerOutpostService.assigned(mob)
                && mob.getTarget() == null
                && !SocialAudienceGoal.isActive(mob)
                && !BondedSuitService.isSuitingOwner(mob, player)
                && !ChangedAddonCompat.isGrabberBusy(mob)
                && HypnosisQteService.getActiveVictim(mob) == null
                && !mob.isPassenger()
                && !mob.isLeashed();
    }
}
