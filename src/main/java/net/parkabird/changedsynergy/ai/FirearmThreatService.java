package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.compat.FirearmCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;

/** Turns valid optional-mod shot events into social reactions and hunt memory. */
public final class FirearmThreatService {
    private static final String NEXT_REACTION = "ChangedSynergyNextFirearmReaction";
    private static final String LAST_SHOT = "ChangedSynergyLastFirearmShot";
    private static final String NEXT_HELD_NOTICE =
            "ChangedSynergyNextHeldFirearmNotice";
    private static final String NEXT_HELD_COMMENT =
            "ChangedSynergyNextHeldFirearmComment";

    private FirearmThreatService() {
    }

    public static void onGunshot(ServerPlayer shooter) {
        onGunshot(shooter, Double.NaN, false);
    }

    /**
     * @param reportedSoundRadius sound distance reported by the firearm mod;
     *                            NaN falls back to Synergy's configured radius
     */
    public static void onGunshot(
            ServerPlayer shooter,
            double reportedSoundRadius,
            boolean suppressed) {
        if (!(shooter.level() instanceof ServerLevel level)
                || shooter.isCreative() || shooter.isSpectator()
                || !SynergyPerformanceTracker.featureEnabled(Feature.HUNT)
                || !level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)) {
            return;
        }
        HuntAIEvents.markFirearmNoiseHandled(shooter);
        long now = level.getGameTime();
        if (shooter.getPersistentData().getLong(LAST_SHOT) + 4L > now) {
            return;
        }
        shooter.getPersistentData().putLong(LAST_SHOT, now);

        double radius = Double.isFinite(reportedSoundRadius)
                ? Math.max(0.0D, reportedSoundRadius)
                : ChangedSynergyConfig.COMMON.firearmGunshotRadius.get();
        if (radius <= 0.0D) {
            return;
        }
        AABB area = shooter.getBoundingBox().inflate(radius);
        double radiusSqr = radius * radius;
        List<ChangedEntity> nearby = level.getEntitiesOfClass(
                        ChangedEntity.class, area, HuntAIEvents::isEligibleHunter)
                .stream()
                .filter(mob -> mob.distanceToSqr(shooter) <= radiusSqr)
                .sorted(Comparator.comparingDouble(mob -> mob.distanceToSqr(shooter)))
                .toList();
        if (nearby.isEmpty()) {
            return;
        }

        long until = now + ChangedSynergyConfig.COMMON.huntSearchSeconds.get() * 20L;
        ChangedEntity hostileSpeaker = null;
        ChangedEntity friendlySpeaker = null;
        int alerted = 0;
        int alertLimit = suppressed ? 5 : 10;
        for (ChangedEntity mob : nearby) {
            boolean hostile = HuntAIEvents.isHostilePlayerRelation(mob, shooter);
            if (hostile && (mob.getTarget() == null || mob.getTarget() == shooter)
                    && alerted < alertLimit) {
                HuntMemory.investigate(mob, shooter, shooter.position(), until);
                if (mob.hasLineOfSight(shooter)
                        && HuntAIEvents.canReacquire(mob, shooter, true)) {
                    HuntMemory.seeTarget(mob, shooter);
                    mob.setTarget(shooter);
                }
                alerted++;
                if (hostileSpeaker == null && mayReact(mob, now)) {
                    hostileSpeaker = mob;
                }
            } else if (!hostile && friendlySpeaker == null && mayReact(mob, now)) {
                friendlySpeaker = mob;
            }
        }

        if (hostileSpeaker != null) {
            markReacted(hostileSpeaker, now);
            NpcDialogue.trigger(hostileSpeaker, shooter, Cue.GUNSHOT_HOSTILE);
        }
        if (friendlySpeaker != null) {
            markReacted(friendlySpeaker, now);
            NpcDialogue.trigger(friendlySpeaker, shooter, Cue.GUNSHOT_FRIENDLY);
        }
    }

    /** Lets an idle creature inspect an unfamiliar firearm before it is fired. */
    public static void observeHeldFirearm(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)
                || player.isCreative() || player.isSpectator()
                || !SynergyPerformanceTracker.featureEnabled(Feature.HUNT)
                || !level.getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                || !FirearmCompat.isHoldingFirearm(player)) {
            return;
        }

        long now = level.getGameTime();
        var playerData = player.getPersistentData();
        if (playerData.getLong(NEXT_HELD_NOTICE) > now
                || playerData.getLong(LAST_SHOT) + 80L > now) {
            return;
        }
        playerData.putLong(NEXT_HELD_NOTICE, now + 240L);

        double radius = Math.min(
                10.0D,
                ChangedSynergyConfig.COMMON.npcDialogueRange.get());
        AABB area = player.getBoundingBox().inflate(radius);
        level.getEntitiesOfClass(
                        ChangedEntity.class,
                        area,
                        HuntAIEvents::isEligibleHunter)
                .stream()
                .filter(mob -> mob.distanceToSqr(player) <= radius * radius)
                .filter(mob -> mob.getTarget() == null
                        && !LatexSocialMemory.isProvoked(mob, player)
                        && mob.hasLineOfSight(player)
                        && mob.getPersistentData()
                                .getLong(NEXT_HELD_COMMENT) <= now)
                .min(Comparator.comparingDouble(mob -> mob.distanceToSqr(player)))
                .ifPresent(mob -> {
                    mob.getPersistentData().putLong(
                            NEXT_HELD_COMMENT, now + 2_400L);
                    NpcDialogue.trigger(mob, player, Cue.FIREARM_NOTICED);
                });
    }

    private static boolean mayReact(ChangedEntity mob, long now) {
        return mob.getPersistentData().getLong(NEXT_REACTION) <= now;
    }

    private static void markReacted(ChangedEntity mob, long now) {
        mob.getPersistentData().putLong(NEXT_REACTION, now + 100L);
    }
}
