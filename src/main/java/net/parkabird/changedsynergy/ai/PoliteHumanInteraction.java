package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/**
 * Persistent first-contact state for individuals whose human-facing intent is
 * a greeting. A returned pat turns the contact into a remembered relationship.
 */
public final class PoliteHumanInteraction {
    private static final String ROOT = "ChangedSynergyPoliteInteraction";
    private static final String CONTACTS = "Contacts";
    private static final String ATTEMPTS = "Attempts";
    private static final String ACCEPTED = "Accepted";
    private static final String LAST_CONTACT = "LastContact";
    private static final String WARY_UNTIL = "WaryUntil";
    private static final String ACTIVE_TARGET = "ActiveTarget";
    private static final String ACTIVE_STAGE = "ActiveStage";
    private static final String ACTIVE_UNTIL = "ActiveUntil";
    private static final String NEXT_ATTEMPT = "NextAttempt";
    private static final String PLAYER_GREETER = "ChangedSynergyPoliteGreeter";
    private static final String PLAYER_GREETER_UNTIL = "ChangedSynergyPoliteGreeterUntil";
    private static final String PLAYER_NEXT_GREETING = "ChangedSynergyNextPoliteGreeting";

    private PoliteHumanInteraction() {
    }

    public enum Stage {
        NONE(0),
        OBSERVING(1),
        APPROACHING(2),
        WAITING(3);

        private final int id;

        Stage(int id) {
            this.id = id;
        }

        private static Stage byId(int id) {
            for (Stage stage : values()) {
                if (stage.id == id) {
                    return stage;
                }
            }
            return NONE;
        }
    }

    /** Transient movement state is not resumed after an entity reloads. */
    public static void resetTransientState(ChangedEntity mob) {
        clearActive(data(mob));
    }

    /**
     * Shared safety gate used by targeting, grabs and sound investigation.
     * Accepted acquaintances stay neutral while their remembered trust remains
     * positive. Unknown humans receive a finite number of greeting attempts.
     */
    public static boolean shouldWithholdHostility(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!isBaseCandidate(mob, player)) {
            return false;
        }
        if (isAcquainted(mob, player)) {
            return true;
        }
        if (isActiveWith(mob, player)) {
            return true;
        }
        if (hasCourtesyHistory(mob, player)
                && CreaturePersonality.familiarity(mob, player) <= -6) {
            return false;
        }
        return attempts(mob, player)
                < ChangedSynergyConfig.COMMON.politeUnansweredLimit.get();
    }

    public static boolean canBeginApproach(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!shouldWithholdHostility(mob, player)
                || isAcquainted(mob, player)
                || mob.getTarget() != null) {
            return false;
        }
        CompoundTag root = data(mob);
        long now = mob.level().getGameTime();
        CompoundTag contact = contact(mob, player, false);
        return !root.hasUUID(ACTIVE_TARGET)
                && root.getLong(NEXT_ATTEMPT) <= now
                && (contact == null || contact.getLong(WARY_UNTIL) <= now)
                && mayReservePlayer(mob, player, now);
    }

    public static boolean beginApproach(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!canBeginApproach(mob, player)) {
            return false;
        }
        CompoundTag root = data(mob);
        root.putUUID(ACTIVE_TARGET, player.getUUID());
        root.putInt(ACTIVE_STAGE, Stage.OBSERVING.id);
        root.putLong(ACTIVE_UNTIL, mob.level().getGameTime() + 600L);
        reservePlayer(mob, player, mob.level().getGameTime() + 600L);
        return true;
    }

    public static void markApproaching(ChangedEntity mob, ServerPlayer player) {
        if (isActiveWith(mob, player)) {
            data(mob).putInt(ACTIVE_STAGE, Stage.APPROACHING.id);
        }
    }

    public static void markProbe(ChangedEntity mob, ServerPlayer player) {
        if (!isActiveWith(mob, player)) {
            return;
        }
        long now = mob.level().getGameTime();
        long responseTicks =
                ChangedSynergyConfig.COMMON.politeResponseSeconds.get() * 20L;
        CompoundTag contact = contact(mob, player, true);
        contact.putInt(ATTEMPTS, Math.min(32, contact.getInt(ATTEMPTS) + 1));
        contact.putLong(LAST_CONTACT, now);
        putContact(mob, player, contact);

        CompoundTag root = data(mob);
        root.putInt(ACTIVE_STAGE, Stage.WAITING.id);
        root.putLong(ACTIVE_UNTIL, now + responseTicks);
        LatexSocialMemory.beginTruce(mob, player, responseTicks + 40L);
        CreaturePersonality.rememberPatGiven(mob, player);
    }

    /**
     * A player may answer the creature's probe or politely initiate contact
     * first. The caller has already recorded the received pat.
     */
    public static boolean acceptPlayerPat(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!isBaseCandidate(mob, player)
                || !(isActiveWith(mob, player)
                        || hasCourtesyHistory(mob, player)
                        || attempts(mob, player) == 0)) {
            return false;
        }

        CompoundTag contact = contact(mob, player, true);
        contact.putBoolean(ACCEPTED, true);
        contact.putLong(LAST_CONTACT, mob.level().getGameTime());
        contact.remove(WARY_UNTIL);
        putContact(mob, player, contact);

        CompoundTag root = data(mob);
        if (isActiveWith(mob, player)) {
            clearActive(root);
            releasePlayerReservation(mob, player, 160L);
        }
        root.putLong(NEXT_ATTEMPT, mob.level().getGameTime() + 1200L);
        LatexSocialMemory.beginTruce(mob, player, 600L);
        return true;
    }

    public static void finishUnanswered(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!isActiveWith(mob, player)) {
            return;
        }
        CompoundTag root = data(mob);
        clearActive(root);
        releasePlayerReservation(mob, player, 200L);
        long now = mob.level().getGameTime();
        root.putLong(NEXT_ATTEMPT, now + 500L + mob.getRandom().nextInt(401));
        LatexSocialMemory.beginTruce(mob, player,
                attempts(mob, player)
                                >= ChangedSynergyConfig.COMMON.politeUnansweredLimit.get()
                        ? 80L : 160L);
    }

    public static void cancelApproach(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!isActiveWith(mob, player)) {
            return;
        }
        CompoundTag root = data(mob);
        clearActive(root);
        releasePlayerReservation(mob, player, 100L);
        root.putLong(NEXT_ATTEMPT, mob.level().getGameTime() + 200L);
    }

    /**
     * Ends the current greeting without turning one accidental hit into an
     * instant attack. The ordinary warning accumulator decides when hostility
     * is justified.
     */
    public static boolean notePlayerAttack(
            ChangedEntity mob,
            ServerPlayer player) {
        if (!isPoliteHumanPair(mob, player)) {
            return false;
        }

        CompoundTag contact = contact(mob, player, true);
        long now = mob.level().getGameTime();
        contact.putLong(WARY_UNTIL, now + 500L);
        contact.putLong(LAST_CONTACT, now);
        putContact(mob, player, contact);
        CompoundTag root = data(mob);
        if (isActiveWith(mob, player)) {
            clearActive(root);
            releasePlayerReservation(mob, player, 300L);
        }
        root.putLong(NEXT_ATTEMPT, now + 500L);
        return true;
    }

    public static boolean hasCourtesyHistory(
            ChangedEntity mob,
            ServerPlayer player) {
        CompoundTag contact = contact(mob, player, false);
        return contact != null
                && (contact.getInt(ATTEMPTS) > 0
                        || contact.getBoolean(ACCEPTED)
                        || contact.contains(LAST_CONTACT, Tag.TAG_LONG));
    }

    public static boolean isAcquainted(
            ChangedEntity mob,
            ServerPlayer player) {
        CompoundTag contact = contact(mob, player, false);
        return (contact != null && contact.getBoolean(ACCEPTED)
                        || CreaturePersonality.hasEstablishedRelationship(mob, player))
                && CreaturePersonality.hasTrustedRelationship(mob, player);
    }

    public static boolean isActiveWith(
            ChangedEntity mob,
            ServerPlayer player) {
        CompoundTag root = data(mob);
        return root.hasUUID(ACTIVE_TARGET)
                && player.getUUID().equals(root.getUUID(ACTIVE_TARGET));
    }

    public static Stage stage(ChangedEntity mob, ServerPlayer player) {
        return isActiveWith(mob, player)
                ? Stage.byId(data(mob).getInt(ACTIVE_STAGE))
                : Stage.NONE;
    }

    public static long activeUntil(ChangedEntity mob, ServerPlayer player) {
        return isActiveWith(mob, player) ? data(mob).getLong(ACTIVE_UNTIL) : 0L;
    }

    public static boolean hasNearbyAcquaintance(
            ServerPlayer player,
            double range) {
        if (!(player.level() instanceof ServerLevel level)) {
            return false;
        }
        double rangeSqr = range * range;
        return !level.getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(range),
                        mob -> mob.isAlive()
                                && mob.distanceToSqr(player) <= rangeSqr
                                && isAcquainted(mob, player))
                .isEmpty();
    }

    private static int attempts(ChangedEntity mob, ServerPlayer player) {
        CompoundTag contact = contact(mob, player, false);
        return contact == null ? 0 : contact.getInt(ATTEMPTS);
    }

    private static boolean isBaseCandidate(
            ChangedEntity mob,
            ServerPlayer player) {
        return isPoliteHumanPair(mob, player)
                && ChangedSynergyConfig.COMMON.politeHumanInteraction.get()
                && player.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                && player.isAlive()
                && !player.isCreative()
                && !player.isSpectator()
                && !LatexSocialMemory.isProvoked(mob, player)
                && !LatexSocialMemory.isPetDefenseAuthorized(mob, player)
                && LatexSocialMemory.petOwnerUuid(mob).isEmpty()
                && !LatexSocialMemory.hasActiveBond(mob)
                && !(mob instanceof TamableLatexEntity tamable && tamable.isTame());
    }

    private static boolean isPoliteHumanPair(
            ChangedEntity mob,
            ServerPlayer player) {
        return LatexSocialMemory.isSocialLatex(mob)
                && CreatureSocialProfile.allowsPoliteContact(mob)
                && HumanIntent.of(mob) == HumanIntent.GREET
                && !ProcessTransfur.isPlayerTransfurred(player)
                && LatexSocialRelation.between(mob, player) == LatexSocialRelation.HUMAN;
    }

    private static CompoundTag contact(
            ChangedEntity mob,
            ServerPlayer player,
            boolean create) {
        CompoundTag contacts = contacts(mob);
        String key = player.getStringUUID();
        if (!contacts.contains(key, Tag.TAG_COMPOUND)) {
            if (!create) {
                return null;
            }
            contacts.put(key, new CompoundTag());
        }
        return contacts.getCompound(key);
    }

    private static void putContact(
            ChangedEntity mob,
            ServerPlayer player,
            CompoundTag contact) {
        contacts(mob).put(player.getStringUUID(), contact);
    }

    private static CompoundTag contacts(ChangedEntity mob) {
        CompoundTag root = data(mob);
        if (!root.contains(CONTACTS, Tag.TAG_COMPOUND)) {
            root.put(CONTACTS, new CompoundTag());
        }
        return root.getCompound(CONTACTS);
    }

    private static CompoundTag data(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static void clearActive(CompoundTag root) {
        root.remove(ACTIVE_TARGET);
        root.remove(ACTIVE_STAGE);
        root.remove(ACTIVE_UNTIL);
    }

    private static boolean mayReservePlayer(
            ChangedEntity mob,
            ServerPlayer player,
            long now) {
        CompoundTag data = player.getPersistentData();
        if (data.hasUUID(PLAYER_GREETER)
                && data.getLong(PLAYER_GREETER_UNTIL) <= now) {
            data.remove(PLAYER_GREETER);
            data.remove(PLAYER_GREETER_UNTIL);
        }
        return data.getLong(PLAYER_NEXT_GREETING) <= now
                && (!data.hasUUID(PLAYER_GREETER)
                        || mob.getUUID().equals(data.getUUID(PLAYER_GREETER)));
    }

    private static void reservePlayer(
            ChangedEntity mob,
            ServerPlayer player,
            long until) {
        CompoundTag data = player.getPersistentData();
        data.putUUID(PLAYER_GREETER, mob.getUUID());
        data.putLong(PLAYER_GREETER_UNTIL, until);
    }

    private static void releasePlayerReservation(
            ChangedEntity mob,
            ServerPlayer player,
            long cooldownTicks) {
        CompoundTag data = player.getPersistentData();
        if (data.hasUUID(PLAYER_GREETER)
                && mob.getUUID().equals(data.getUUID(PLAYER_GREETER))) {
            data.remove(PLAYER_GREETER);
            data.remove(PLAYER_GREETER_UNTIL);
            data.putLong(
                    PLAYER_NEXT_GREETING,
                    mob.level().getGameTime() + Math.max(1L, cooldownTicks));
        }
    }
}
