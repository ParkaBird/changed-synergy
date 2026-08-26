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
    private static final double FOLLOW_SPEED = 0.35;
    private static final double TELEPORT_DISTANCE = 24.0D;
    private final ChangedEntity mob;
    private ServerPlayer player;
    private int repathTicks;
    private int teleportRetryTicks;
    private float oldWaterCost;

    public BondedFollowGoal(ChangedEntity mob) {
        this.mob = mob;
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
                || !movementAvailable()
                || BondedPetSettings.hasRunningUtilityGoal(mob)) {
            return false;
        }
        double startDistance = PlayerRelationshipSettings.bondedStartDistance(player);
        return mob.distanceToSqr(player) > startDistance * startDistance;
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
                && !BondedPetSettings.hasRunningUtilityGoal(mob)
                && mob.distanceToSqr(player)
                        > Math.pow(PlayerRelationshipSettings.bondedStopDistance(player), 2.0D);
    }

    @Override
    public void start() {
        repathTicks = 0;
        teleportRetryTicks = 0;
        oldWaterCost = mob.getPathfindingMalus(BlockPathTypes.WATER);
        mob.setPathfindingMalus(BlockPathTypes.WATER, 0.0F);
        mob.getNavigation().stop();
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
                .bondedStopDistance(player);
        double startDistance = PlayerRelationshipSettings
                .bondedStartDistance(player);
        if (distanceSqr <= stopDistance * stopDistance) {
            mob.getNavigation().stop();
            repathTicks = 0;
            return;
        }
        if (distanceSqr <= startDistance * startDistance
                && mob.getNavigation().isDone()) {
            return;
        }
        mob.getLookControl().setLookAt(player, 30.0F, 30.0F);
        if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
            repathTicks = 5;
            if (distanceSqr >= TELEPORT_DISTANCE * TELEPORT_DISTANCE
                    && teleportRetryTicks <= 0
                    && !mob.isPassenger() && !mob.isLeashed()
                    && mob.level() instanceof ServerLevel level) {
                if (BondedTeleportSafety.teleportNearOwner(level, mob, player)) {
                    return;
                }
                // A flying owner or a cramped room may currently have no safe
                // landing. Retry later instead of repeatedly scanning or
                // dropping the creature into open air.
                teleportRetryTicks = 60;
            }
            mob.getNavigation().moveTo(player, FOLLOW_SPEED);
        }
    }

    @Override
    public void stop() {
        player = null;
        mob.getNavigation().stop();
        mob.setPathfindingMalus(BlockPathTypes.WATER, oldWaterCost);
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean movementAvailable() {
        return player != null
                && mob.getTarget() == null
                && !SocialAudienceGoal.isActive(mob)
                && !BondedSuitService.isSuitingOwner(mob, player)
                && !ChangedAddonCompat.isGrabberBusy(mob)
                && HypnosisQteService.getActiveVictim(mob) == null
                && !mob.isPassenger()
                && !mob.isLeashed();
    }
}
