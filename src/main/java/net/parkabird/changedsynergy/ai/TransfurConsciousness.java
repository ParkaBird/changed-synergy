package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.init.ChangedGameRules;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;

/** Keeps forced conversion separate from confirmed origins, bonds and temporary suits. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class TransfurConsciousness {
    private TransfurConsciousness() {}

    public static boolean isKept(Player player) {
        return player.level().getGameRules().getBoolean(ChangedGameRules.RULE_KEEP_BRAIN);
    }

    public static boolean isTransforming(Player player) {
        var form = ProcessTransfur.getPlayerTransfurVariant(player);
        return form != null && !form.isTemporaryFromSuit() && form.isTransfurring();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onConversionStarted(TransfurEvents.PreProcessTransfurVariantInstanceEvent event) {
        if (!(event.player instanceof ServerPlayer player) || event.temporaryFromSuit
                || event.transfurContext == null || event.transfurContext.source() == null) return;
        var source = event.transfurContext.source().map(
                net.ltxprogrammer.changed.ability.IAbstractChangedEntity::getEntity,
                net.ltxprogrammer.changed.ability.ILatexAssimilatedEntity::getEntity);
        if (source instanceof ChangedEntity creature && LatexSocialMemory.isSocialLatex(creature)
                && !TrueTransfurCompat.isInstinctBody(creature)) {
            LatexSocialMemory.settleAfterSecondaryTransfur(creature, player);
            FactionHostilityGrace.beginGlobal(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAnimationAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && isTransforming(player)
                && event.getSource().getEntity() instanceof ChangedEntity creature
                && event.getAmount() != Float.MAX_VALUE) {
            event.setCanceled(true);
            LatexSocialMemory.clearHostilityToward(creature, player);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onAnimationTarget(LivingChangeTargetEvent event) {
        if (event.getNewTarget() instanceof ServerPlayer player && isTransforming(player)
                && event.getEntity() instanceof ChangedEntity) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onProjectionJoin(EntityJoinLevelEvent event) {
        // Changed's render-only body has no AI or independent identity. It must
        // never become a world NPC, including after its human host has died.
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof ChangedEntity body
                && body.getUnderlyingPlayer() != null && !TrueTransfurCompat.isInstinctBody(body))
            event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onStaleProjection(net.minecraftforge.event.entity.living.LivingEvent.LivingTickEvent event) {
        if (!(event.getEntity() instanceof ChangedEntity body)
                || !(body.level() instanceof net.minecraft.server.level.ServerLevel level)
                || body.getUnderlyingPlayer() == null || TrueTransfurCompat.isInstinctBody(body)) return;
        Player owner = body.getUnderlyingPlayer();
        if (level.getEntity(body.getUUID()) == body && (!owner.isAlive() || owner.isRemoved()
                || ProcessTransfur.getPlayerTransfurVariantSafe(owner)
                        .map(form -> form.getChangedEntity() != body).orElse(true))) body.discard();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onKeepConscious(ProcessTransfur.KeepConsciousEvent event) {
        // Changed does not post this event for an explicitly safe conversion.
        // Optional infection mods must not silently undo an explicit false rule.
        if (!isKept(event.player)) {
            var player = event.player;
            var item = player.getUseItem().getItem();
            var cause = event.context == null ? null : event.context.cause();
            // Check the actual use action, not just a cause supplied by another
            // entity. A dropped syringe or a thrown flask is not player consent.
            event.shouldKeepConscious = player.isUsingItem() && event.context != null && event.context.source() == null
                    && ((cause == TransfurCause.SYRINGE || cause == TransfurCause.SYRINGE_LEFT_HAND)
                            && item instanceof net.ltxprogrammer.changed.item.LatexSyringe
                        || cause == TransfurCause.ATE_LATEX && item instanceof net.ltxprogrammer.changed.item.LatexFilledMug
                        || cause == TransfurCause.FACE_HAZARD && item instanceof net.ltxprogrammer.changed.item.LatexFlask);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onInstinctDecision(TransfurEvents.LatexAssimilationDecisionEvent event) {
        if (event.getEntity() instanceof Player player && !isKept(player)
                && event.getSourceEntity() instanceof ChangedEntity body && TrueTransfurCompat.isInstinctBody(body)
                && event.getDecision() != null && body.getSelfVariant() != null
                && event.getDecision().method() == LatexAssimilationDecision.Method.ABSORPTION) {
            // Native NPC absorption discards its source. An Instinct body belongs
            // to another real player and must survive the victim's conversion.
            var decision = event.getDecision();
            event.setDecision(LatexAssimilationDecision.strong(LatexAssimilationDecision.Method.REPLICATION,
                    body.getSelfVariant(), TransfurContext.npcLatexHazard(body, TransfurCause.GRAB_REPLICATE),
                    decision.transfurProgress(), decision.postTransfurListener()));
        }
    }

    /** Direct secondary conversions bypass Changed's normal survival decision. */
    public static void applyInvoluntaryOutcome(Player player, TransfurVariantInstance<?> instance) {
        if (!isKept(player)) {
            instance.willSurviveTransfur = false;
            // Changed commits the real death/replacement when progression crosses
            // one. Starting at one would leave a living, non-conscious host forever.
            instance.transfurProgressionO = instance.transfurProgression = 0.0F;
        }
    }
}
