package net.parkabird.changedsynergy.ai;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.NpcDispositionEvents;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker;
import net.parkabird.changedsynergy.performance.SynergyPerformanceTracker.Feature;

/** Lets trusted friends and high-standing faction members defend a player. */
public final class SocialFriendDefenseGoal extends TargetGoal {
    private static final int RECENT_HIT_TICKS = 100;
    private static final int ASSIST_DIALOGUE_COOLDOWN_TICKS = 600;
    private static final int SAME_ENGAGEMENT_COOLDOWN_TICKS = 1200;
    private static final String NEXT_ASSIST_DIALOGUE =
            "ChangedSynergyNextFriendCombatAssistDialogue";
    private static final String NEXT_SAME_ASSIST_DIALOGUE =
            "ChangedSynergyNextSameFriendCombatAssistDialogue";
    private static final String LAST_ASSIST_PLAYER =
            "ChangedSynergyLastFriendCombatAssistPlayer";
    private static final String LAST_ASSIST_TARGET =
            "ChangedSynergyLastFriendCombatAssistTarget";
    private static final Map<ServerLevel, AssistDialogueGate> ASSIST_GATES =
            Collections.synchronizedMap(new WeakHashMap<>());

    private final ChangedEntity friend;
    private ServerPlayer player;
    private LivingEntity threat;

    public SocialFriendDefenseGoal(ChangedEntity friend) {
        super(friend, false);
        this.friend = friend;
        setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (!(friend.level() instanceof ServerLevel)
                || !SynergyPerformanceTracker.featureEnabled(Feature.SOCIAL)
                || friend.getTarget() != null
                || !LatexSocialMemory.petOwnerUuid(friend).isEmpty()
                || LatexSocialMemory.hasActiveBond(friend)
                || SocialAudienceGoal.isActive(friend)
                || ChangedAddonCompat.isGrabberBusy(friend)) {
            return false;
        }
        if (!SynergyPerformanceTracker.allowBackground(
                friend, Feature.SOCIAL,
                SynergyPerformanceTracker.configuredBackgroundInterval())) {
            return false;
        }
        player = findPlayer();
        if (player == null) {
            return false;
        }
        threat = findThreat(player);
        return canDefendAgainst(threat, player);
    }

    @Override
    public void start() {
        LatexSocialMemory.authorizePetDefense(friend, threat, 200L);
        friend.setTarget(threat);
        friend.setAggressive(true);
        announceAssist();
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        return player != null
                && threat != null
                && SynergyPerformanceTracker.featureEnabled(Feature.SOCIAL)
                && isSupported(player)
                && canDefendAgainst(threat, player)
                && remainsThreat(threat, player)
                && super.canContinueToUse();
    }

    @Override
    public void stop() {
        LatexSocialMemory.clearPetDefense(friend, threat);
        if (friend.getTarget() == threat) {
            friend.setTarget(null);
            friend.setAggressive(false);
        }
        player = null;
        threat = null;
        super.stop();
    }

    /** Allows one assist call per player group instead of one per restarted target goal. */
    private void announceAssist() {
        if (player == null
                || threat == null
                || !(friend.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (friend.getPersistentData().getLong(NEXT_ASSIST_DIALOGUE) > now
                || player.getPersistentData().getLong(NEXT_ASSIST_DIALOGUE) > now) {
            return;
        }
        boolean sameEngagement = friend.getPersistentData().hasUUID(LAST_ASSIST_PLAYER)
                && friend.getPersistentData().hasUUID(LAST_ASSIST_TARGET)
                && player.getUUID().equals(friend.getPersistentData().getUUID(
                        LAST_ASSIST_PLAYER))
                && threat.getUUID().equals(friend.getPersistentData().getUUID(
                        LAST_ASSIST_TARGET));
        if (sameEngagement
                && friend.getPersistentData().getLong(
                        NEXT_SAME_ASSIST_DIALOGUE) > now) {
            return;
        }
        synchronized (ASSIST_GATES) {
            AssistDialogueGate gate = ASSIST_GATES.computeIfAbsent(
                    level, ignored -> new AssistDialogueGate());
            if (!gate.tryAcquire(
                    friend.getUUID(),
                    player.getUUID(),
                    threat.getUUID(),
                    now)) {
                return;
            }
        }
        long next = now + ASSIST_DIALOGUE_COOLDOWN_TICKS;
        friend.getPersistentData().putLong(NEXT_ASSIST_DIALOGUE, next);
        player.getPersistentData().putLong(NEXT_ASSIST_DIALOGUE, next);
        friend.getPersistentData().putUUID(
                LAST_ASSIST_PLAYER, player.getUUID());
        friend.getPersistentData().putUUID(
                LAST_ASSIST_TARGET, threat.getUUID());
        friend.getPersistentData().putLong(
                NEXT_SAME_ASSIST_DIALOGUE,
                now + SAME_ENGAGEMENT_COOLDOWN_TICKS);
        if (hasPersonalSupport(player)) {
            NpcDialogue.trigger(
                    friend,
                    player,
                    NpcDialogue.Cue.FRIEND_COMBAT_ASSIST);
        } else {
            NpcDialogue.trigger(
                    friend,
                    player,
                    NpcDialogue.Cue.FACTION_COMBAT_ASSIST,
                    player.getDisplayName());
        }
    }

    private record AssistDialogueKey(
            UUID friend,
            UUID player,
            UUID threat) {
    }

    /** In-memory guard closes same-tick races between several assisting friends. */
    private static final class AssistDialogueGate {
        private final Map<UUID, Long> playerCooldowns = new HashMap<>();
        private final Map<AssistDialogueKey, Long> engagementCooldowns =
                new HashMap<>();

        private boolean tryAcquire(
                UUID friend,
                UUID player,
                UUID threat,
                long now) {
            playerCooldowns.entrySet().removeIf(entry -> entry.getValue() <= now);
            engagementCooldowns.entrySet().removeIf(
                    entry -> entry.getValue() <= now);
            AssistDialogueKey key = new AssistDialogueKey(
                    friend, player, threat);
            if (playerCooldowns.getOrDefault(player, 0L) > now
                    || engagementCooldowns.getOrDefault(key, 0L) > now) {
                return false;
            }
            playerCooldowns.put(
                    player, now + ASSIST_DIALOGUE_COOLDOWN_TICKS);
            engagementCooldowns.put(
                    key, now + SAME_ENGAGEMENT_COOLDOWN_TICKS);
            return true;
        }
    }

    private ServerPlayer findPlayer() {
        ServerPlayer following = CreaturePersonality.socialPartner(friend);
        if (isSupported(following)) {
            return following;
        }
        return ((ServerLevel)friend.level()).players().stream()
                .filter(this::isSupported)
                .min(Comparator.comparingDouble(friend::distanceToSqr))
                .orElse(null);
    }

    private boolean isSupported(ServerPlayer candidate) {
        if (candidate == null
                || !candidate.isAlive()
                || candidate.isSpectator()
                || candidate.level() != friend.level()
                || LatexSocialMemory.isProvoked(friend, candidate)
                || LatexSocialMemory.hasRelationshipBetrayal(
                        friend, candidate)) {
            return false;
        }
        boolean personalSupport = hasPersonalSupport(candidate);
        boolean factionSupport = hasFactionSupport(candidate);
        if (!personalSupport && !factionSupport) {
            return false;
        }
        double supportRange = supportRange(candidate);
        return friend.distanceToSqr(candidate)
                <= supportRange * supportRange;
    }

    private boolean hasPersonalSupport(ServerPlayer candidate) {
        if (!CreaturePersonality.canFriendDefend(friend, candidate)
                || !CreaturePersonality.hasTrustedRelationship(
                        friend, candidate)) {
            return false;
        }
        boolean closeFriend = CreaturePersonality.relationshipTier(
                friend, candidate) == CreaturePersonality.RelationshipTier.CLOSE;
        return closeFriend
                || CreaturePersonality.isSocialFollowing(friend, candidate);
    }

    private boolean hasFactionSupport(ServerPlayer candidate) {
        return FactionReputation.isAllied(friend, candidate);
    }

    private double supportRange(ServerPlayer candidate) {
        double range = hasPersonalSupport(candidate)
                ? CreaturePersonality.friendDefenseRange(friend, candidate)
                : 0.0D;
        if (hasFactionSupport(candidate)) {
            range = Math.max(range, 24.0D);
        }
        return range;
    }

    private LivingEntity findThreat(ServerPlayer supportedPlayer) {
        LivingEntity recent = supportedPlayer.getLastHurtByMob();
        if (supportedPlayer.tickCount - supportedPlayer.getLastHurtByMobTimestamp()
                        <= RECENT_HIT_TICKS
                && canDefendAgainst(recent, supportedPlayer)) {
            return recent;
        }
        return supportedPlayer.level().getEntitiesOfClass(
                        Mob.class,
                        supportedPlayer.getBoundingBox().inflate(
                                supportRange(supportedPlayer)),
                        candidate -> candidate.getTarget() == supportedPlayer
                                && (!(candidate instanceof ChangedEntity changed)
                                        || NpcDispositionEvents.hasHostileDisposition(
                                                changed, supportedPlayer))
                                && canDefendAgainst(candidate, supportedPlayer))
                .stream()
                .min(Comparator.comparingDouble(candidate ->
                        candidate.distanceToSqr(supportedPlayer)))
                .orElse(null);
    }

    /**
     * Revalidates a Changed target for the lifetime of the fight. Some social
     * approach goals temporarily use vanilla target state, and other goals may
     * leave that state behind for a tick; neither is evidence of hostility.
     * A creature that actually hurt the player remains a valid threat for the
     * normal recent-hit window regardless of its usual disposition.
     */
    private boolean remainsThreat(
            LivingEntity candidate,
            ServerPlayer supportedPlayer) {
        if (supportedPlayer.getLastHurtByMob() == candidate
                && supportedPlayer.tickCount
                        - supportedPlayer.getLastHurtByMobTimestamp()
                        <= RECENT_HIT_TICKS) {
            return true;
        }
        return !(candidate instanceof ChangedEntity changed)
                || NpcDispositionEvents.hasHostileDisposition(
                        changed, supportedPlayer);
    }

    private boolean canDefendAgainst(
            LivingEntity candidate,
            ServerPlayer supportedPlayer) {
        if (!(candidate instanceof Mob)
                || candidate == friend
                || !candidate.isAlive()
                || candidate instanceof Creeper
                || candidate instanceof Ghast
                || candidate.isAlliedTo(supportedPlayer)
                || candidate.isAlliedTo(friend)) {
            return false;
        }
        if (candidate instanceof ChangedEntity other
                && (LatexCreatureCombatRules.areCompatriots(friend, other)
                        || LatexSocialMemory.isBonded(other, supportedPlayer)
                        || LatexSocialMemory.isPetOwner(other, supportedPlayer)
                        || CreaturePersonality.hasTrustedRelationship(other, supportedPlayer)
                        || other instanceof TamableLatexEntity nativePet
                                && nativePet.isTame()
                                && supportedPlayer.getUUID().equals(nativePet.getOwnerUUID()))) {
            return false;
        }
        return friend.canAttack(candidate);
    }
}
