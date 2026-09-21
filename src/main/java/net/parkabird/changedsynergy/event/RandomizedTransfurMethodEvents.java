package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.ai.VoluntaryBondTransfurService;

/** Makes a supported hostile encounter keep one random transfur method until it ends. */
public final class RandomizedTransfurMethodEvents {
    private static final String TARGET = "ChangedSynergyRandomTransfurTarget";
    private static final String METHOD = "ChangedSynergyRandomTransfurMethod";
    private static final String EXPIRES = "ChangedSynergyRandomTransfurExpires";
    private static final long ENCOUNTER_TICKS = 200L;

    private RandomizedTransfurMethodEvents() {
    }

    public static void randomize(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (!ChangedSynergyConfig.COMMON.randomizeCreatureTransfurMethod.get()
                || event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSourceEntity() instanceof ChangedEntity source)
                || event.getDecision() == null
                || source.level().isClientSide
                || LatexSocialMemory.isOrganic(source)
                || !CreatureSocialProfile.allowsSynergySystems(source)
                || TakeoverService.active(player)
                || TakeoverService.carrying(source)
                || VoluntaryBondTransfurService.isCompleting(source, player)
                || !isCreatureAttack(event.getTransfurCause())) {
            return;
        }

        LatexAssimilationDecision.Method chosen = choice(source, player);
        LatexAssimilationDecision<?> current = event.getDecision();
        if (chosen == current.method()) {
            return;
        }
        TransfurCause cause = chosen == LatexAssimilationDecision.Method.ABSORPTION
                ? TransfurCause.GRAB_ABSORB : TransfurCause.GRAB_REPLICATE;
        LatexAssimilationDecision<?> alternate =
                source.makeLatexAssimilationDecision(cause, player);
        if (alternate != null && alternate.method() == chosen
                && alternate.transfurVariant() != null) {
            event.setDecision(alternate.withTransfurProgress(
                    current.transfurProgress()));
        }
    }

    private static LatexAssimilationDecision.Method choice(
            ChangedEntity source,
            ServerPlayer player) {
        long now = source.level().getGameTime();
        var data = source.getPersistentData();
        if (data.hasUUID(TARGET)
                && player.getUUID().equals(data.getUUID(TARGET))
                && data.getLong(EXPIRES) > now) {
            data.putLong(EXPIRES, now + ENCOUNTER_TICKS);
            try {
                return LatexAssimilationDecision.Method.valueOf(
                        data.getString(METHOD));
            } catch (IllegalArgumentException ignored) {
            }
        }
        LatexAssimilationDecision.Method chosen = source.getRandom().nextBoolean()
                ? LatexAssimilationDecision.Method.REPLICATION
                : LatexAssimilationDecision.Method.ABSORPTION;
        data.putUUID(TARGET, player.getUUID());
        data.putString(METHOD, chosen.name());
        data.putLong(EXPIRES, now + ENCOUNTER_TICKS);
        return chosen;
    }

    private static boolean isCreatureAttack(TransfurCause cause) {
        return cause == TransfurCause.ATTACK_REPLICATE_LEFT
                || cause == TransfurCause.ATTACK_REPLICATE_RIGHT
                || cause == TransfurCause.GRAB_REPLICATE
                || cause == TransfurCause.GRAB_ABSORB;
    }
}
