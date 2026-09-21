package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;

/** A slow, non-hostile first-contact approach used by polite individuals. */
public final class PoliteHumanApproachGoal extends Goal {
    public static final int PRIORITY = -1;
    private final ChangedEntity mob;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer player;
    private long nextScanTick;
    private int observeTicks;
    private int closeTicks;
    private int unseenTicks;
    private int repathTicks;
    private boolean completed;

    public PoliteHumanApproachGoal(ChangedEntity mob) {
        this.mob = mob;
        this.followNavigation = new CompanionFollowNavigation(mob);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!ChangedSynergyGameRules.enabled(
                        mob.level(), ChangedSynergyGameRules.FRIENDSHIP_SYSTEM)
                || !SynergyPerformanceTracker.featureEnabled(Feature.SOCIAL)
                || !(mob.level() instanceof ServerLevel level)
                || mob.level().getGameTime() < nextScanTick
                || !movementAvailable()
                || mob.getTarget() != null) {
            return false;
        }
        if (!SynergyPerformanceTracker.allowBackground(
                mob, Feature.SOCIAL,
                SynergyPerformanceTracker.configuredBackgroundInterval())) {
            return false;
        }
        nextScanTick = mob.level().getGameTime() + 20L + mob.getRandom().nextInt(21);

        double range = ChangedSynergyConfig.COMMON.politeApproachRange.get();
        double rangeSqr = range * range;
        player = level.players().stream()
                .filter(candidate -> mob.distanceToSqr(candidate) <= rangeSqr)
                .filter(candidate -> !TakeoverService.active(candidate))
                .filter(candidate -> PoliteHumanInteraction.canBeginApproach(mob, candidate))
                .filter(candidate -> NpcDispositionEvents.canVisuallyAcquire(mob, candidate))
                .min(Comparator.comparingDouble(mob::distanceToSqr))
                .orElse(null);
        return player != null;
    }

    @Override
    public boolean canContinueToUse() {
        if (player == null || !player.isAlive() || player.isSpectator()
                || !SynergyPerformanceTracker.featureEnabled(Feature.SOCIAL)
                || !movementAvailable()
                || !PoliteHumanInteraction.isActiveWith(mob, player)
                || !PoliteHumanInteraction.shouldWithholdHostility(mob, player)) {
            return false;
        }
        double range = ChangedSynergyConfig.COMMON.politeApproachRange.get() * 1.5D;
        return mob.distanceToSqr(player) <= range * range;
    }

    @Override
    public void start() {
        completed = false;
        observeTicks = 20 + mob.getRandom().nextInt(21);
        closeTicks = 0;
        unseenTicks = 0;
        repathTicks = 0;
        mob.getNavigation().stop();
        followNavigation.reset();
        mob.setAggressive(false);
        if (!PoliteHumanInteraction.beginApproach(mob, player)) {
            completed = true;
            return;
        }
        Cue reputationCue = HuntAIEvents.reputationEncounterCue(mob, player);
        NpcDialogue.trigger(
                mob,
                player,
                reputationCue == null ? Cue.POLITE_NOTICE : reputationCue);
    }

    @Override
    public void tick() {
        if (player == null || completed) {
            return;
        }
        mob.setAggressive(false);
        mob.getLookControl().setLookAt(player, 25.0F, 25.0F);

        long now = mob.level().getGameTime();
        PoliteHumanInteraction.Stage stage =
                PoliteHumanInteraction.stage(mob, player);
        if (PoliteHumanInteraction.activeUntil(mob, player) <= now) {
            if (stage == PoliteHumanInteraction.Stage.WAITING) {
                NpcDialogue.trigger(mob, player, Cue.POLITE_NO_RESPONSE);
                PoliteHumanInteraction.finishUnanswered(mob, player);
            } else {
                PoliteHumanInteraction.cancelApproach(mob, player);
            }
            completed = true;
            return;
        }

        if (stage == PoliteHumanInteraction.Stage.WAITING) {
            mob.getNavigation().stop();
            return;
        }

        if (observeTicks-- > 0) {
            mob.getNavigation().stop();
            return;
        }
        PoliteHumanInteraction.markApproaching(mob, player);

        if (!mob.hasLineOfSight(player)) {
            if (++unseenTicks >= 40) {
                PoliteHumanInteraction.cancelApproach(mob, player);
                completed = true;
            }
            return;
        }
        unseenTicks = 0;

        double contactDistance = Mth.clamp(
                1.55D + (mob.getBbWidth() + player.getBbWidth()) * 0.5D,
                2.0D,
                2.8D);
        if (mob.distanceToSqr(player) > contactDistance * contactDistance) {
            closeTicks = 0;
            if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
                repathTicks = 10;
                followNavigation.moveToward(
                        player,
                        ChangedSynergyConfig.COMMON.politeApproachSpeed.get());
            }
            return;
        }

        mob.getNavigation().stop();
        followNavigation.reset();
        if (++closeTicks < 16) {
            return;
        }

        mob.swing(InteractionHand.MAIN_HAND);
        NpcDialogue.emoteTarget(mob, player, Emote.CONFUSED);
        PoliteHumanInteraction.markProbe(mob, player);
        NpcDialogue.trigger(mob, player, Cue.POLITE_PROBE);
        closeTicks = 0;
    }

    @Override
    public void stop() {
        if (!completed && player != null
                && PoliteHumanInteraction.isActiveWith(mob, player)) {
            PoliteHumanInteraction.cancelApproach(mob, player);
        }
        mob.getNavigation().stop();
        followNavigation.reset();
        mob.setAggressive(false);
        player = null;
        completed = false;
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
                && !ProvisionerFishingFocus.isFocusedFishing(mob)
                && !ChangedAddonCompat.isGrabberBusy(mob)
                && HypnosisQteService.getActiveVictim(mob) == null;
    }
}
