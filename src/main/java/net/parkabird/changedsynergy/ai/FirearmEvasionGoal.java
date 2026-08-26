package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.compat.FirearmCompat;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;

/** Stable lateral bursts make armed-player pursuit less like a straight line. */
public final class FirearmEvasionGoal extends Goal {
    private static final double MIN_DISTANCE_SQR = 4.0D * 4.0D;
    private static final double MAX_DISTANCE_SQR = 30.0D * 30.0D;
    private static final double DEFAULT_CHASE_SPEED = 0.4D;
    private final ChangedEntity mob;
    private ServerPlayer threat;
    private Vec3 dodgePoint;
    private int dodgeTicks;
    private int nextDodgeTick;
    private boolean dodgeLeft;

    public FirearmEvasionGoal(ChangedEntity mob) {
        this.mob = mob;
        this.dodgeLeft = mob.getRandom().nextBoolean();
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ChangedSynergyConfig.COMMON.firearmEvasion.get()
                || mob.tickCount < nextDodgeTick
                || mob.isInWaterOrBubble()
                || ChangedAddonCompat.isGrabberBusy(mob)
                || !(mob.getTarget() instanceof ServerPlayer player)
                || !HuntAIEvents.isHuntAIEnabled(mob)
                || !HuntAIEvents.isEligibleHunter(mob)
                || !HuntAIEvents.isHostilePlayerRelation(mob, player)
                || !FirearmCompat.isHoldingFirearm(player)
                || !mob.hasLineOfSight(player)) {
            return false;
        }
        double distance = mob.distanceToSqr(player);
        if (distance < MIN_DISTANCE_SQR || distance > MAX_DISTANCE_SQR) {
            return false;
        }
        threat = player;
        dodgePoint = chooseDodgePoint(player);
        return dodgePoint != null;
    }

    @Override
    public boolean canContinueToUse() {
        return dodgeTicks > 0 && threat != null && threat.isAlive()
                && dodgePoint != null
                && mob.distanceToSqr(dodgePoint) > 2.0D
                && !mob.getNavigation().isDone()
                && !mob.isInWaterOrBubble()
                && !ChangedAddonCompat.isGrabberBusy(mob)
                && FirearmCompat.isHoldingFirearm(threat);
    }

    @Override
    public void start() {
        dodgeTicks = 18 + mob.getRandom().nextInt(8);
        double speed = !mob.getNavigation().isDone() && mob.getNavigation().speedModifier > 0.0D
                ? mob.getNavigation().speedModifier : DEFAULT_CHASE_SPEED;
        mob.getNavigation().moveTo(dodgePoint.x, dodgePoint.y, dodgePoint.z, speed);
        dodgeLeft = !dodgeLeft;
    }

    @Override
    public void tick() {
        dodgeTicks--;
        if (threat != null) {
            mob.getLookControl().setLookAt(threat, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        int baseCooldown = 28 + mob.getRandom().nextInt(22);
        nextDodgeTick = mob.tickCount + Math.max(12, (int)Math.round(
                baseCooldown * CreaturePersonality.firearmEvasionCooldownMultiplier(mob)));
        threat = null;
        dodgePoint = null;
        dodgeTicks = 0;
    }

    private Vec3 chooseDodgePoint(ServerPlayer player) {
        Vec3 away = mob.position().subtract(player.position());
        away = new Vec3(away.x, 0.0D, away.z);
        if (away.lengthSqr() < 1.0E-4D) {
            return null;
        }
        away = away.normalize();
        double side = dodgeLeft ? 1.0D : -1.0D;
        Vec3 lateral = new Vec3(-away.z * side, 0.0D, away.x * side);
        Vec3 desired = mob.position().add(lateral.scale(6.0D)).add(away.scale(1.5D));
        Vec3 candidate = DefaultRandomPos.getPosTowards(mob, 8, 4, desired, Math.PI / 3.0D);
        if (candidate == null) {
            desired = mob.position().subtract(lateral.scale(6.0D)).add(away.scale(1.5D));
            candidate = DefaultRandomPos.getPosTowards(mob, 8, 4, desired, Math.PI / 3.0D);
        }
        return candidate;
    }
}
