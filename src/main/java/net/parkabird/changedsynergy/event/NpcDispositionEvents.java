package net.parkabird.changedsynergy.event;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService;
import net.parkabird.changedsynergy.ai.FactionReputation;
import net.parkabird.changedsynergy.ai.DarkLatexDisguise;
import net.parkabird.changedsynergy.ai.HuntMemory;
import net.parkabird.changedsynergy.ai.HumanIntent;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexSocialRelation;
import net.parkabird.changedsynergy.ai.ProvisionerFishingFocus;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reconciles Changed's combat targets with persistent social relationships.
 * Friendly and bonded safety remains active even when enhanced NPC AI is disabled.
 */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class NpcDispositionEvents {
    private static final double CLOSE_NOTICE_RANGE_SQR = 5.0D * 5.0D;
    private static final double NORMAL_VIEW_DOT = 0.25D;
    private static final double SNEAKING_VIEW_DOT = 0.45D;
    private static final double SPRINTING_VIEW_DOT = 0.0D;

    private NpcDispositionEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 10 != 0) {
            return;
        }

        double scanRange = ChangedSynergyConfig.COMMON.npcAwarenessRange.get();
        for (ChangedEntity creature : player.level().getEntitiesOfClass(
                ChangedEntity.class,
                player.getBoundingBox().inflate(scanRange),
                LivingEntity::isAlive)) {
            updateCreature(player, creature);
        }
    }

    private static void updateCreature(ServerPlayer player, ChangedEntity creature) {
        boolean survival = player.isAlive() && !player.isCreative() && !player.isSpectator();
        if (!survival) {
            if (creature.getTarget() == player) {
                LatexSocialEvents.calmTowards(creature, player);
            }
            return;
        }

        if (CreatureCacheGuardService.isDefendingAgainst(creature, player)) {
            if (creature.getTarget() != player) {
                creature.setTarget(player);
            }
            return;
        }

        if (ProvisionerFishingFocus.ignoresPassingPlayer(creature, player)) {
            if (creature.getTarget() == player) {
                LatexSocialEvents.calmTowards(creature, player);
            }
            return;
        }

        boolean socialFusion = LatexFusionIntent.mayInitiate(creature, player);
        if (!socialFusion && LatexSocialMemory.shouldRemainNeutral(creature, player)) {
            if (creature.getTarget() == player) {
                LatexSocialEvents.calmTowards(creature, player);
            }
            return;
        }

        boolean organicTransformedTarget = LatexSocialMemory.isOrganic(creature)
                && ProcessTransfur.isPlayerTransfurred(player);
        boolean disguisedDarkRival = DarkLatexDisguise.appearsAsDarkRivalToWhite(creature, player);
        if (!socialFusion && creature.isAlliedTo(player)
                && !LatexSocialMemory.isProvoked(creature, player)
                && !organicTransformedTarget
                && !disguisedDarkRival) {
            return;
        }

        if (creature instanceof TamableLatexEntity tamable && tamable.isTame()) {
            if (tamable.getOwner() == player) {
                maintainNativeCompanion(creature);
            }
            if (creature.getTarget() == player) {
                LatexSocialEvents.calmTowards(creature, player);
            }
            return;
        }

        if (!canTarget(creature, player)) {
            if (creature.getTarget() == player
                    && LatexSocialMemory.isNeutralOrganicHumanContact(creature, player)) {
                LatexSocialEvents.calmTowards(creature, player);
            }
            return;
        }

        LivingEntity current = creature.getTarget();
        if (current == null || !current.isAlive() || current == player
                || creature.distanceToSqr(player) < creature.distanceToSqr(current)) {
            creature.setTarget(player);
        }
    }

    /** Shared decision used by search, disguise and grab behaviours. */
    public static boolean canTarget(ChangedEntity creature, ServerPlayer player) {
        if (!player.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                || !hasHostileDisposition(creature, player)) {
            return false;
        }

        return canVisuallyAcquire(creature, player);
    }

    /**
     * Returns whether this creature genuinely regards the player as hostile.
     * Unlike {@link #canTarget}, this deliberately ignores line of sight and
     * range so companion defence cannot mistake a social approach (which may
     * temporarily use the vanilla target field) for an attack.
     */
    public static boolean hasHostileDisposition(
            ChangedEntity creature,
            ServerPlayer player) {
        if (mustRejectTarget(creature, player)) {
            return false;
        }

        if (LatexFusionIntent.mayInitiate(creature, player)) {
            return true;
        }

        boolean transformedOrganicTarget = LatexSocialMemory.isOrganic(creature)
                && ProcessTransfur.isPlayerTransfurred(player);
        boolean disguisedDarkRival = DarkLatexDisguise.appearsAsDarkRivalToWhite(creature, player);
        if (creature.isAlliedTo(player)
                && !LatexSocialMemory.isProvoked(creature, player)
                && !transformedOrganicTarget
                && !disguisedDarkRival) {
            return false;
        }

        boolean armed = creature.getPersistentData().getBoolean("ChangedSynergyArmed");
        boolean pacified = ChangedAddonCompat.pacifiedEffect()
                .map(effect -> {
                    if (armed && creature.hasEffect(effect)) {
                        creature.removeEffect(effect);
                    }
                    return !armed && creature.hasEffect(effect);
                })
                .orElse(false);
        if (pacified && ChangedSynergyConfig.COMMON.respectPacifiedLatexes.get()) {
            return false;
        }

        if (FactionReputation.isHostile(creature, player)) {
            return true;
        }
        if (disguisedDarkRival || LatexSocialMemory.isProvoked(creature, player)) {
            return true;
        }
        if (LatexSocialMemory.isOrganic(creature)) {
            // Unprovoked humans were rejected by the neutral gate above.
            // Transfurred outsiders remain ordinary combat-capable targets.
            return transformedOrganicTarget;
        }

        LatexSocialRelation relation = LatexSocialRelation.between(creature, player);
        return relation == LatexSocialRelation.RIVAL
                || relation == LatexSocialRelation.HUMAN
                        && HumanIntent.of(creature) != HumanIntent.GREET;
    }

    /**
     * Applies to native Changed target goals as well as Synergy's scanner.
     * A first target assignment needs real visual contact; an attacker or a
     * previously confirmed target may instead be retained within pursuit range.
     */
    public static boolean mayAcceptTargetAssignment(
            ChangedEntity creature,
            ServerPlayer player) {
        if (mustRejectTarget(creature, player)) {
            return false;
        }
        if (!player.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                || !HuntAIEvents.isEligibleHunter(creature)
                || creature.getTarget() == player) {
            return true;
        }
        if (creature.getLastHurtByMob() == player
                        && creature.tickCount - creature.getLastHurtByMobTimestamp() <= 200
                || HuntMemory.targets(creature, player) && HuntMemory.wasConfirmed(creature)) {
            return isWithinPursuitRange(creature, player);
        }
        return canTarget(creature, player);
    }

    /** True only when a new target is visible, close enough and inside a plausible field of view. */
    public static boolean canVisuallyAcquire(
            ChangedEntity creature,
            ServerPlayer player) {
        if (!creature.hasLineOfSight(player)) {
            return false;
        }

        double distanceSqr = creature.distanceToSqr(player);
        double range = visualAcquisitionRange(creature, player);
        if (distanceSqr > range * range) {
            return false;
        }
        if (distanceSqr <= CLOSE_NOTICE_RANGE_SQR) {
            return true;
        }

        Vec3 direction = player.getEyePosition().subtract(creature.getEyePosition());
        if (direction.lengthSqr() < 1.0E-6D) {
            return true;
        }
        Vec3 look = creature.getViewVector(1.0F);
        double threshold = player.isSprinting()
                ? SPRINTING_VIEW_DOT
                : player.isCrouching() ? SNEAKING_VIEW_DOT : NORMAL_VIEW_DOT;
        return look.dot(direction.normalize()) >= threshold;
    }

    public static boolean isWithinPursuitRange(
            ChangedEntity creature,
            ServerPlayer player) {
        double nativeRange = creature.getAttributeValue(Attributes.FOLLOW_RANGE);
        double configuredRange = ChangedSynergyConfig.COMMON.maximumPursuitRange.get()
                * CreaturePersonality.pursuitRangeMultiplier(creature);
        double range = Math.min(nativeRange, configuredRange);
        return creature.distanceToSqr(player) <= range * range;
    }

    private static double visualAcquisitionRange(
            ChangedEntity creature,
            ServerPlayer player) {
        double visibility = player.isSprinting() ? 1.15D
                : player.isCrouching() ? 0.68D : 1.0D;
        if (player.isInvisible()) {
            visibility *= 0.40D;
        }
        double configuredRange = ChangedSynergyConfig.COMMON.visualAcquisitionRange.get()
                * CreaturePersonality.pursuitRangeMultiplier(creature)
                * visibility
                * FactionReputation.detectionMultiplier(creature, player);
        return Math.min(creature.getAttributeValue(Attributes.FOLLOW_RANGE), configuredRange);
    }

    /**
     * Relationship-only target rejection. Unlike {@link #canTarget}, this does
     * not depend on enhanced-AI range, line of sight or gamerule state.
     */
    public static boolean mustRejectTarget(ChangedEntity creature, ServerPlayer player) {
        if (!player.isAlive() || player.isCreative() || player.isSpectator()
                || HypnosisQteService.shouldPreventTargeting(creature, player)) {
            return true;
        }
        if (ProvisionerFishingFocus.ignoresPassingPlayer(creature, player)) {
            return true;
        }
        if (CreatureCacheGuardService.isDefendingAgainst(creature, player)) {
            return false;
        }
        if (LatexFusionIntent.mayInitiate(creature, player)) {
            return false;
        }
        if (LatexSocialMemory.shouldRemainNeutral(creature, player)
                || creature instanceof TamableLatexEntity tamable && tamable.isTame()
                || LatexSocialMemory.isNeutralOrganicHumanContact(creature, player)) {
            return true;
        }

        boolean transformedOrganicTarget = LatexSocialMemory.isOrganic(creature)
                && ProcessTransfur.isPlayerTransfurred(player);
        boolean disguisedDarkRival =
                DarkLatexDisguise.appearsAsDarkRivalToWhite(creature, player);
        return creature.isAlliedTo(player)
                && !LatexSocialMemory.isProvoked(creature, player)
                && !transformedOrganicTarget
                && !disguisedDarkRival;
    }

    private static void maintainNativeCompanion(ChangedEntity companion) {
        if (ChangedSynergyConfig.COMMON.pacifyTamedCompanions.get()) {
            ChangedAddonCompat.pacifiedEffect().ifPresent(effect -> companion.addEffect(
                    new MobEffectInstance(effect, 120, 0, false, false)));
        }

        int regeneration = ChangedSynergyConfig.COMMON.tamedCompanionRegeneration.get();
        if (regeneration > 0) {
            companion.addEffect(new MobEffectInstance(
                    MobEffects.REGENERATION, 120, regeneration - 1, false, false));
        }
    }
}
