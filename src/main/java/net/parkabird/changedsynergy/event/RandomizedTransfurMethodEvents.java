package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyConfig.CreatureTransfurMethod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.ai.VoluntaryBondTransfurService;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;

/** Applies the configured method to eligible hostile creature encounters. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RandomizedTransfurMethodEvents {
    private static final String TARGET = "ChangedSynergyRandomTransfurTarget";
    private static final String METHOD = "ChangedSynergyRandomTransfurMethod";
    private static final String EXPIRES = "ChangedSynergyRandomTransfurExpires";
    private static final long ENCOUNTER_TICKS = 200L;

    private RandomizedTransfurMethodEvents() {
    }

    public static void randomize(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        CreatureTransfurMethod mode = ChangedSynergyConfig.COMMON.creatureTransfurMethod.get();
        if (mode == CreatureTransfurMethod.NATIVE
                || mode == CreatureTransfurMethod.TAKEOVER
                        && !ChangedSynergyConfig.COMMON.takeoverEnabled.get()
                || event.isCanceled()
                || !(event.getEntity() instanceof ServerPlayer player)
                || !(event.getSourceEntity() instanceof ChangedEntity source)
                || event.getDecision() == null
                || source.level().isClientSide
                || ChangedAddonCompat.wearsHazardBodySuit(player)
                || LatexSocialMemory.isOrganic(source)
                || !CreatureSocialProfile.allowsSynergySystems(source)
                || TakeoverService.active(player)
                || TakeoverService.carrying(source)
                || mode == CreatureTransfurMethod.TAKEOVER
                        && !TakeoverService.supportsConfiguredTakeover(source, player)
                || VoluntaryBondTransfurService.isCompleting(source, player)
                || !isCreatureAttack(event.getTransfurCause())) {
            return;
        }

        LatexAssimilationDecision.Method chosen = switch (mode) {
            case RANDOM -> choice(source, player);
            case ASSIMILATION -> LatexAssimilationDecision.Method.REPLICATION;
            case ABSORPTION, TAKEOVER -> LatexAssimilationDecision.Method.ABSORPTION;
            case NATIVE -> event.getDecision().method();
        };
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

    /** Synergy must not turn a protected player into a replica after Addon has checked the suit. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void preserveHazardSuitProtection(
            TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || !ChangedAddonCompat.wearsHazardBodySuit(player)
                || event.getDecision() == null
                || event.getDecision().method() != LatexAssimilationDecision.Method.REPLICATION) {
            return;
        }
        LatexAssimilationDecision<?> original = event.getOriginalDecision();
        if (original != null
                && original.method() == LatexAssimilationDecision.Method.ABSORPTION) {
            event.setDecision(original.withTransfurProgress(
                    event.getDecision().transfurProgress()));
        } else {
            event.setCanceled(true);
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
