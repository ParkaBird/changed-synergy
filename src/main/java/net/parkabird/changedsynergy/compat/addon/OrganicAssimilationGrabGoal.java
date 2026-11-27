package net.parkabird.changedsynergy.compat.addon;

import net.foxyas.changedaddon.ability.api.GrabEntityAbilityExtensor;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ai.HunterArchetype;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;

/**
 * Organic creatures pin a target and assimilate through bites, claws or body
 * pressure. This goal never enters Changed's suit/absorption path.
 */
public final class OrganicAssimilationGrabGoal extends AddonSecondaryGrabGoal {
    private static final int PRIMARY_HOLD_LIMIT = 104;
    private static final float PRIMARY_PROGRESS_MIN = 3.0F;
    private static final float PRIMARY_PROGRESS_FRACTION = 0.17F;

    private OrganicContactStyle style;
    private boolean targetStartedTransfurred;

    public OrganicAssimilationGrabGoal(ChangedEntity mob, IGrabberEntity grabber) {
        super(mob, grabber);
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
        if (target == null || ability == null) {
            return;
        }
        targetStartedTransfurred = ProcessTransfur.isPlayerTransfurred(target);
        style = OrganicContactStyle.forMob(mob);
        ServerPlayer grabbedTarget = target;
        if (beginArmHold(0.92F, true)) {
            NpcDialogue.trigger(mob, grabbedTarget, style.cue);
        }
    }

    @Override
    public void tick() {
        if (!maintainHold()) {
            return;
        }
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
        boolean interrupted = endHold(80, 40);
        style = null;
        targetStartedTransfurred = false;

        if (interrupted && previousTarget != null
                && LatexFusionIntent.nativeFusionAvailable(mob, previousTarget)) {
            mob.setTarget(previousTarget);
        }
    }

    private boolean isEligibleTarget(ServerPlayer player) {
        return !InvoluntaryTransfurNegotiation.hasAbsorptionClaim(player)
                && !LatexFusionIntent.nativeFusionAvailable(mob, player)
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
        if (target == null) {
            return;
        }
        ServerPlayer transfurredPlayer = target;
        boolean bondConflict = LatexSocialMemory.hasOtherBondedCreature(transfurredPlayer, mob);
        if (!applySecondaryTransfur(TransfurCause.GRAB_REPLICATE)) {
            return;
        }
        if (bondConflict) {
            HuntAIEvents.celebrateOrganicBondConflict(mob, transfurredPlayer);
        } else {
            LatexSocialEvents.calmTowards(mob, transfurredPlayer);
            NpcDialogue.trigger(mob, transfurredPlayer, Cue.ORGANIC_SECONDARY_TRANSFUR);
        }
    }

    private enum OrganicContactStyle {
        BITE(16, Cue.ORGANIC_GRAPPLE_BITE),
        CLAW(14, Cue.ORGANIC_GRAPPLE_CLAW),
        PIN(18, Cue.ORGANIC_GRAPPLE_PIN);

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
