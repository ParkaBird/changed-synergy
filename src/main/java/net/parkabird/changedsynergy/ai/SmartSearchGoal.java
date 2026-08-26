package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

public final class SmartSearchGoal extends Goal {
    private final ChangedEntity mob;
    private Vec3 waypoint;
    private int waypointTicks;
    private boolean expired;

    public SmartSearchGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(mob.level() instanceof ServerLevel level)
                || !HuntAIEvents.isHuntAIEnabled(mob)
                || !HuntAIEvents.isEligibleHunter(mob)
                || mob.getTarget() != null) {
            return false;
        }
        HuntState state = HuntMemory.getState(mob);
        return (state == HuntState.SEARCHING || state == HuntState.INVESTIGATING)
                && HuntMemory.getPosition(mob).isPresent()
                && HuntMemory.getUntil(mob) > level.getGameTime();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        waypoint = null;
        waypointTicks = 0;
        expired = false;
    }

    @Override
    public void tick() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }

        long now = level.getGameTime();
        if (now >= HuntMemory.getUntil(mob)) {
            if (HuntMemory.getState(mob) == HuntState.INVESTIGATING) {
                finishInvestigation();
            } else {
                expire(level);
            }
            return;
        }

        ServerPlayer remembered = HuntMemory.getTargetPlayer(mob, level).orElse(null);
        if (remembered != null && HuntAIEvents.canReacquire(mob, remembered, HuntMemory.wasConfirmed(mob))
                && mob.hasLineOfSight(remembered)) {
            HuntMemory.seeTarget(mob, remembered);
            mob.setTarget(remembered);
            NpcDialogue.trigger(mob, remembered, Cue.REACQUIRED);
            return;
        }

        if (HuntMemory.getState(mob) == HuntState.INVESTIGATING) {
            tickInvestigation();
            return;
        }

        if (waypoint == null || waypointTicks-- <= 0
                || mob.getNavigation().isDone() || mob.distanceToSqr(waypoint) < 3.0) {
            selectNextSearchWaypoint();
        }

        if (waypoint != null) {
            mob.getLookControl().setLookAt(waypoint.x, waypoint.y + 1.0, waypoint.z, 30.0F, 30.0F);
        }
    }

    @Override
    public void stop() {
        HuntState state = HuntMemory.getState(mob);
        if ((state == HuntState.SEARCHING || state == HuntState.INVESTIGATING)
                && mob.level() instanceof ServerLevel level
                && HuntMemory.getUntil(mob) <= level.getGameTime()) {
            if (state == HuntState.INVESTIGATING) {
                finishInvestigation();
            } else {
                expire(level);
            }
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private void tickInvestigation() {
        Vec3 center = HuntMemory.getPosition(mob).orElse(null);
        if (center == null || mob.distanceToSqr(center) < 3.0D) {
            finishInvestigation();
            return;
        }

        if (waypoint == null || waypointTicks-- <= 0 || mob.getNavigation().isDone()) {
            waypoint = center;
            double speed = ChangedSynergyConfig.COMMON.huntSearchSpeed.get()
                    * CreaturePersonality.searchSpeedMultiplier(mob);
            mob.getNavigation().moveTo(center.x, center.y, center.z, speed);
            waypointTicks = 20;
        }
        mob.getLookControl().setLookAt(center.x, center.y + 1.0, center.z, 30.0F, 30.0F);
    }

    private void finishInvestigation() {
        HuntMemory.clear(mob);
        mob.getNavigation().stop();
        waypoint = null;
    }

    private void selectNextSearchWaypoint() {
        Vec3 center = HuntMemory.getPosition(mob).orElse(mob.position());
        if (waypoint == null) {
            waypoint = center;
        } else {
            double angle = mob.getRandom().nextDouble() * Math.PI * 2.0;
            double radiusMultiplier = CreaturePersonality.searchRadiusMultiplier(mob);
            double radius = (2.0 + mob.getRandom().nextDouble() * 6.0) * radiusMultiplier;
            double yOffset = mob.getRandom().nextInt(3) - 1;
            waypoint = center.add(Math.cos(angle) * radius, yOffset, Math.sin(angle) * radius);
        }

        double speed = ChangedSynergyConfig.COMMON.huntSearchSpeed.get()
                * CreaturePersonality.searchSpeedMultiplier(mob);
        if (!mob.getNavigation().moveTo(waypoint.x, waypoint.y, waypoint.z, speed)) {
            waypoint = center;
            mob.getNavigation().moveTo(center.x, center.y, center.z, speed);
        }
        waypointTicks = Math.max(12, (int)Math.round(
                (35 + mob.getRandom().nextInt(35))
                        * CreaturePersonality.searchWaypointTimeMultiplier(mob)));
    }

    private void expire(ServerLevel level) {
        if (expired) {
            return;
        }
        expired = true;
        HuntMemory.getTargetPlayer(mob, level)
                .ifPresent(player -> NpcDialogue.trigger(mob, player, Cue.GIVE_UP));
        HuntMemory.clear(mob);
        mob.getNavigation().stop();
    }
}
