package net.parkabird.changedsynergy.compat.addon;

import java.util.EnumSet;
import net.foxyas.changedaddon.ability.api.GrabEntityAbilityExtensor;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.foxyas.changedaddon.mixins.entity.CombatTrackerAccessor;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ai.HunterArchetype;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.network.PacketDistributor;

/**
 * Organic creatures pin a target and assimilate through bites, claws or body
 * pressure. This goal never enters Changed's suit/absorption path.
 */
public final class OrganicAssimilationGrabGoal extends Goal {
    private static final int PRIMARY_HOLD_LIMIT = 104;
    private static final int SECONDARY_HOLD_TICKS = 106;
    private static final float PRIMARY_PROGRESS_MIN = 3.0F;
    private static final float PRIMARY_PROGRESS_FRACTION = 0.17F;
    private static final double GRAB_DISTANCE_SQR = 2.5 * 2.5;

    private final ChangedEntity mob;
    private final IGrabberEntity grabber;
    private ServerPlayer target;
    private GrabEntityAbilityInstance ability;
    private OrganicContactStyle style;
    private int holdTicks;
    private boolean targetStartedTransfurred;
    private boolean completed;

    public OrganicAssimilationGrabGoal(ChangedEntity mob, IGrabberEntity grabber) {
        this.mob = mob;
        this.grabber = grabber;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide
                || HypnosisProfile.isHypnoticCreature(mob)
                || !LatexSocialMemory.isOrganic(mob)
                || !mob.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                || mob.getSelfVariant() == null
                || !grabber.canUseGrab()
                || !grabber.canEntityGrab(mob.getType(), mob.level())
                || grabber.getGrabCooldown() > 0) {
            return false;
        }

        ServerPlayer player = findGrabTarget();
        if (player == null) {
            return false;
        }

        GrabEntityAbilityInstance candidate = grabber.getGrabAbilityInstance();
        if (!(candidate instanceof GrabEntityAbilityExtensor)
                || candidate.grabbedEntity != null
                || GrabEntityAbility.getGrabber(player) != null
                || !isWithinGrabReach(player)) {
            return false;
        }

        target = player;
        ability = candidate;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !completed
                && target != null
                && target.isAlive()
                && ability != null
                && ability.grabbedEntity == target
                && isEligibleTarget(target);
    }

    @Override
    public void start() {
        holdTicks = 0;
        completed = false;
        if (target == null || ability == null
                || !(ability instanceof GrabEntityAbilityExtensor extensor)) {
            return;
        }

        targetStartedTransfurred = ProcessTransfur.isPlayerTransfurred(target);
        style = OrganicContactStyle.forMob(mob);
        clearRecentCombat();
        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(target)) {
            target = null;
            return;
        }

        LatexSocialMemory.beginSecondaryGrab(mob, target);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, target, GrabType.ARMS));
        mob.setTarget(null);
        mob.getNavigation().stop();
        ChangedSounds.broadcastSound(mob, ChangedSounds.LATEX_GRAB_ENTITY, 1.0F, 0.92F);
        NpcDialogue.trigger(mob, target, style.cue);
        grabber.applyGrabCooldown(0);
    }

    @Override
    public void tick() {
        if (target == null || ability == null || ability.grabbedEntity != target) {
            return;
        }

        clearRecentCombat();
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        holdTicks++;

        if (targetStartedTransfurred) {
            if (holdTicks >= SECONDARY_HOLD_TICKS) {
                completeSecondaryAssimilation();
            }
            return;
        }

        if (holdTicks % style.pulseInterval == 0) {
            mob.swing(InteractionHand.MAIN_HAND);
            if (progressPrimaryAssimilation()) {
                completed = true;
                releaseGrab(target);
            }
        }
        if (holdTicks >= PRIMARY_HOLD_LIMIT && !completed) {
            // Full latex-protection can resist a contact sequence; the organic
            // creature releases instead of silently switching to an instant hit.
            releaseGrab(target);
        }
    }

    @Override
    public void stop() {
        ServerPlayer previousTarget = target;
        boolean wasCompleted = completed;
        if (previousTarget != null) {
            LatexSocialMemory.endSecondaryGrab(mob, previousTarget);
        }
        releaseGrab(previousTarget);

        if (ability instanceof GrabEntityAbilityExtensor extensor) {
            extensor.setSafeModeAuthoritative(false);
        }
        if (ability != null) {
            ability.attackDown = false;
            ability.useDown = false;
        }
        grabber.applyGrabCooldown(wasCompleted ? 80 : 40);

        target = null;
        ability = null;
        style = null;
        holdTicks = 0;
        targetStartedTransfurred = false;
        completed = false;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    private boolean isWithinGrabReach(ServerPlayer player) {
        return mob.getBoundingBox().inflate(0.75).intersects(player.getBoundingBox())
                || mob.distanceToSqr(player) <= GRAB_DISTANCE_SQR;
    }

    private boolean isEligibleTarget(ServerPlayer player) {
        return !InvoluntaryTransfurNegotiation.hasAbsorptionClaim(player)
                && LatexSocialMemory.mayInitiateHostileGrab(mob, player);
    }

    private ServerPlayer findGrabTarget() {
        if (mob.getTarget() instanceof ServerPlayer combatTarget
                && isEligibleTarget(combatTarget)
                && mob.hasLineOfSight(combatTarget)
                && isWithinGrabReach(combatTarget)) {
            return combatTarget;
        }
        return null;
    }

    private boolean progressPrimaryAssimilation() {
        if (target == null) {
            return false;
        }
        LatexAssimilationDecision<?> decision = mob.makeLatexAssimilationDecision(
                TransfurCause.GRAB_REPLICATE, target);
        if (decision == null) {
            return false;
        }
        float tolerance = (float)ProcessTransfur.getEntityTransfurTolerance(target);
        return ProcessTransfur.progressTransfur(
                target,
                decision.withTransfurProgress(Math.max(
                        PRIMARY_PROGRESS_MIN,
                        tolerance * PRIMARY_PROGRESS_FRACTION)));
    }

    private void completeSecondaryAssimilation() {
        if (completed || target == null || ability == null) {
            return;
        }
        TransfurVariant<?> sourceVariant = mob.getSelfVariant();
        if (sourceVariant == null) {
            return;
        }

        boolean bondConflict = LatexSocialMemory.hasOtherBondedCreature(target, mob);
        TransfurContext context = TransfurContext.npcLatexHazard(
                mob, TransfurCause.GRAB_REPLICATE);
        TransfurVariantInstance<?> instance = ProcessTransfur.setPlayerTransfurVariant(
                target, sourceVariant, context, 1.0F);
        if (instance == null) {
            return;
        }

        instance.transfurContext = context;
        instance.transfurProgressionO = 1.0F;
        instance.transfurProgression = 1.0F;
        instance.setTemporaryForSuit(false);
        IAbstractChangedEntity transformed =
                IAbstractChangedEntity.forPlayerWithVariant(target, instance);
        ProcessTransfur.onNewlyTransfurred(transformed);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                SyncTransfurPacket.Builder.of(target));
        ChangedSounds.broadcastSound(target, sourceVariant.sound, 1.0F, 1.0F);

        completed = true;
        releaseGrab(target);
        InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(target);
        if (bondConflict) {
            HuntAIEvents.celebrateOrganicBondConflict(mob, target);
        } else {
            LatexSocialEvents.calmTowards(mob, target);
            NpcDialogue.trigger(mob, target, Cue.ORGANIC_SECONDARY_TRANSFUR);
        }
    }

    private void releaseGrab(ServerPlayer player) {
        if (ability == null || player == null || ability.grabbedEntity != player) {
            return;
        }
        ability.releaseEntity(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, player, GrabType.RELEASE));
    }

    private void clearRecentCombat() {
        if (mob.getCombatTracker() instanceof CombatTrackerAccessor tracker) {
            tracker.getEntries().clear();
        }
        mob.setLastHurtByMob(null);
        mob.setLastHurtByPlayer(null);
    }

    private enum OrganicContactStyle {
        BITE(14, Cue.ORGANIC_GRAPPLE_BITE),
        CLAW(12, Cue.ORGANIC_GRAPPLE_CLAW),
        PIN(16, Cue.ORGANIC_GRAPPLE_PIN);

        private final int pulseInterval;
        private final Cue cue;

        OrganicContactStyle(int pulseInterval, Cue cue) {
            this.pulseInterval = pulseInterval;
            this.cue = cue;
        }

        private static OrganicContactStyle forMob(ChangedEntity mob) {
            return switch (HunterArchetype.of(mob)) {
                case CANINE, CRITTER -> BITE;
                case FELINE, DRACONIC, AVIAN -> CLAW;
                default -> PIN;
            };
        }
    }
}
