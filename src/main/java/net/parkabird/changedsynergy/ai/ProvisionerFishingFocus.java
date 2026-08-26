package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.GroupRole;
import net.parkabird.changedsynergy.ai.CreatureLifeMemory.RoutineState;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;

/** Keeps an active provisioner focused on fishing unless a real threat appears. */
public final class ProvisionerFishingFocus {
    private static final String ANNOUNCED_SESSION =
            "ChangedSynergyFishingNoticeSession";
    private static final int CHECK_INTERVAL_TICKS = 10;
    private static final double NOTICE_RANGE_SQR = 5.5D * 5.5D;

    private ProvisionerFishingFocus() {
    }

    public static boolean isFocusedFishing(ChangedEntity creature) {
        return CreatureLifeMemory.enabled(creature)
                && CreatureLifeMemory.role(creature) == GroupRole.PROVISIONER
                && CreatureLifeMemory.routine(creature) == RoutineState.FISHING;
    }

    /**
     * A nearby player is background movement while the line is out. Direct
     * attacks, active cache defence and a truly hostile faction standing still
     * interrupt the job normally.
     */
    public static boolean ignoresPassingPlayer(
            ChangedEntity creature,
            ServerPlayer player) {
        return isFocusedFishing(creature)
                && !isImmediateThreat(creature, player);
    }

    public static void tick(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || creature.tickCount % CHECK_INTERVAL_TICKS
                        != Math.floorMod(creature.getId(), CHECK_INTERVAL_TICKS)
                || !isFocusedFishing(creature)
                || creature.isAggressive()) {
            return;
        }

        long session = CreatureLifeMemory.snapshot(creature).routineSince();
        if (creature.getPersistentData().getLong(ANNOUNCED_SESSION) == session) {
            return;
        }
        ServerPlayer passer = level.players().stream()
                .filter(player -> player.isAlive()
                        && !player.isSpectator()
                        && creature.distanceToSqr(player) <= NOTICE_RANGE_SQR
                        && creature.hasLineOfSight(player)
                        && ignoresPassingPlayer(creature, player))
                .min(Comparator.comparingDouble(creature::distanceToSqr))
                .orElse(null);
        if (passer == null) {
            return;
        }

        creature.getPersistentData().putLong(ANNOUNCED_SESSION, session);
        NpcDialogue.trigger(creature, passer, cueFor(creature, passer));
    }

    private static boolean isImmediateThreat(
            ChangedEntity creature,
            ServerPlayer player) {
        return CreatureCacheGuardService.isDefendingAgainst(creature, player)
                || LatexSocialMemory.isProvoked(creature, player)
                || FactionReputation.isHostile(creature, player)
                || creature.getLastHurtByMob() == player
                        && creature.tickCount
                                - creature.getLastHurtByMobTimestamp() <= 200;
    }

    private static Cue cueFor(
            ChangedEntity creature,
            ServerPlayer player) {
        if (LatexSocialMemory.isBonded(creature, player)
                || LatexSocialMemory.isPetOwner(creature, player)
                || CreaturePersonality.relationshipTier(creature, player)
                        == CreaturePersonality.RelationshipTier.CLOSE) {
            return Cue.ROLE_FISHING_FOCUS_CLOSE;
        }
        if (CreaturePersonality.hasTrustedRelationship(creature, player)) {
            return Cue.ROLE_FISHING_FOCUS_FRIEND;
        }
        return switch (FactionReputation.standing(creature, player)) {
            case ALLIED -> Cue.ROLE_FISHING_FOCUS_ALLIED;
            case RESPECTED -> Cue.ROLE_FISHING_FOCUS_RESPECTED;
            case RECOGNIZED -> Cue.ROLE_FISHING_FOCUS_RECOGNIZED;
            case DISTRUSTED -> Cue.ROLE_FISHING_FOCUS_DISTRUSTED;
            default -> Cue.ROLE_FISHING_FOCUS_NEUTRAL;
        };
    }
}
