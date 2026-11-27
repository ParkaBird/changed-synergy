package net.parkabird.changedsynergy.compat.addon;

import java.util.List;
import net.foxyas.changedaddon.ability.api.GrabEntityAbilityExtensor;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TransfurCause;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.LatexSocialRelation;
import net.parkabird.changedsynergy.ai.LatexFusionIntent;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.event.HuntAIEvents;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;

/**
 * Uses Addon's normal hold/escape presentation to re-assimilate a transformed
 * player after they have become a personal enemy, or when they belong to an
 * inherently hostile latex faction.
 */
public final class HostileTransfurredGrabGoal extends AddonSecondaryGrabGoal {
    private static final int ABSORB_TICKS = SECONDARY_HOLD_TICKS;

    public HostileTransfurredGrabGoal(ChangedEntity mob, IGrabberEntity grabber) {
        super(mob, grabber);
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide
                || !mob.level().getGameRules().getBoolean(ChangedSynergyGameRules.NPC_AI)
                || !(mob.getTarget() instanceof ServerPlayer player)
                || !isHostileTransfurredTarget(player)
                || mob.getSelfVariant() == null
                || !grabber.canUseGrab()
                || !grabber.canEntityGrab(mob.getType(), mob.level())
                || grabber.getGrabCooldown() > 0) {
            return false;
        }

        GrabEntityAbilityInstance candidateAbility = grabber.getGrabAbilityInstance();
        if (!(candidateAbility instanceof GrabEntityAbilityExtensor)
                || candidateAbility.grabbedEntity != null
                || GrabEntityAbility.getGrabber(player) != null
                || !isWithinGrabReach(player)) {
            return false;
        }

        target = player;
        ability = candidateAbility;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return !completed
                && target != null && target.isAlive()
                && ability != null && ability.grabbedEntity == target
                && isHostileTransfurredTarget(target);
    }

    @Override
    public void start() {
        holdTicks = 0;
        completed = false;
        beginArmHold(1.0F, false);
    }

    @Override
    public void tick() {
        if (!maintainHold()) {
            return;
        }
        if (++holdTicks >= ABSORB_TICKS) {
            completeSecondaryTransfur();
        }
    }

    @Override
    public void stop() {
        ServerPlayer previousTarget = target;
        boolean interrupted = endHold(80, 0);
        if (interrupted && previousTarget != null
                && (LatexFusionIntent.nativeFusionAvailable(mob, previousTarget)
                        || isHostileTransfurredTarget(previousTarget))) {
            mob.setTarget(previousTarget);
        }
    }

    private boolean isHostileTransfurredTarget(ServerPlayer player) {
        if (!ProcessTransfur.isPlayerTransfurred(player)
                || InvoluntaryTransfurNegotiation.hasAbsorptionClaim(player)
                || LatexFusionIntent.nativeFusionAvailable(mob, player)
                || !LatexSocialMemory.mayInitiateHostileGrab(mob, player)) {
            return false;
        }
        return LatexSocialMemory.isProvoked(mob, player)
                || LatexSocialRelation.between(mob, player) == LatexSocialRelation.RIVAL;
    }

    private void completeSecondaryTransfur() {
        if (target == null) {
            return;
        }
        ServerPlayer transfurredPlayer = target;
        boolean bondConflict = LatexSocialMemory.hasOtherBondedCreature(transfurredPlayer, mob);
        if (!applySecondaryTransfur(TransfurCause.GRAB_ABSORB)) {
            return;
        }
        settleNearbyHostility(transfurredPlayer);
        if (bondConflict) {
            HuntAIEvents.celebrateBondConflictAbsorption(mob, transfurredPlayer);
        } else {
            NpcDialogue.trigger(mob, transfurredPlayer, Cue.SECONDARY_TRANSFUR);
        }
    }

    /** Ends the whole nearby combat episode instead of calming only the grabber. */
    private void settleNearbyHostility(ServerPlayer player) {
        if (!(mob.level() instanceof ServerLevel level)) {
            return;
        }
        List<ChangedEntity> combatants = level.getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(32.0D),
                        candidate -> candidate.isAlive()
                                && LatexSocialMemory.isSocialLatex(candidate)
                                && (candidate == mob
                                        || LatexSocialMemory.hasHostilityToward(
                                                candidate, player)
                                        || LatexSocialMemory.isProvoked(
                                                candidate, player)));
        combatants.forEach(candidate -> {
            LatexSocialMemory.settleAfterSecondaryTransfur(candidate, player);
            LatexSocialEvents.clearPendingCombatReactions(candidate, player);
        });

        // Companion defence goals also remember the player's latest attacker.
        // Clear both halves of that incident so they cannot restart the fight
        // immediately after the punitive transfur has completed.
        player.setLastHurtByMob(null);
        player.setLastHurtMob(null);
        level.getEntitiesOfClass(
                        ChangedEntity.class,
                        player.getBoundingBox().inflate(32.0D),
                        companion -> companion.isAlive()
                                && (LatexSocialMemory.isBonded(companion, player)
                                        || LatexSocialMemory.isPetOwner(
                                                companion, player)
                                        || CreaturePersonality
                                                .hasTrustedRelationship(
                                                        companion, player)))
                .forEach(companion -> {
                    if (!(companion.getTarget() instanceof ChangedEntity opponent)
                            || !combatants.contains(opponent)) {
                        return;
                    }
                    companion.setTarget(null);
                    if (companion.getLastHurtByMob() == opponent) {
                        companion.setLastHurtByMob(null);
                    }
                    if (companion.getLastHurtMob() == opponent) {
                        companion.setLastHurtMob(null);
                    }
                    LatexSocialMemory.clearPetDefense(companion, opponent);
                    companion.setAggressive(false);
                    companion.getNavigation().stop();
                });
    }
}
