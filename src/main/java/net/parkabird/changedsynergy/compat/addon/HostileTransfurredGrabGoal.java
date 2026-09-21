package net.parkabird.changedsynergy.compat.addon;

import java.util.EnumSet;
import java.util.List;
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
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.network.packet.SyncTransfurPacket;
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
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.network.PacketDistributor;

/**
 * Uses Addon's normal hold/escape presentation to re-assimilate a transformed
 * player after they have become a personal enemy, or when they belong to an
 * inherently hostile latex faction.
 */
public final class HostileTransfurredGrabGoal extends Goal {
    private static final int ABSORB_TICKS = 106;
    private static final double GRAB_DISTANCE_SQR = 2.5 * 2.5;

    private final ChangedEntity mob;
    private final IGrabberEntity grabber;
    private ServerPlayer target;
    private GrabEntityAbilityInstance ability;
    private int holdTicks;
    private boolean completed;

    public HostileTransfurredGrabGoal(ChangedEntity mob, IGrabberEntity grabber) {
        this.mob = mob;
        this.grabber = grabber;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
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
        if (target == null || ability == null
                || !(ability instanceof GrabEntityAbilityExtensor extensor)) {
            return;
        }

        clearRecentCombat();
        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
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
        ChangedSounds.broadcastSound(mob, ChangedSounds.LATEX_GRAB_ENTITY, 1.0F, 1.0F);
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
        if (++holdTicks >= ABSORB_TICKS) {
            completeSecondaryTransfur();
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
        grabber.applyGrabCooldown(wasCompleted ? 80 : 0);

        target = null;
        ability = null;
        holdTicks = 0;
        completed = false;

        if (!wasCompleted && previousTarget != null
                && (LatexFusionIntent.nativeFusionAvailable(mob, previousTarget)
                        || isHostileTransfurredTarget(previousTarget))) {
            mob.setTarget(previousTarget);
        }
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
        if (completed || target == null || ability == null) {
            return;
        }

        TransfurVariant<?> sourceVariant = mob.getSelfVariant();
        if (sourceVariant == null) {
            return;
        }

        boolean bondConflict = LatexSocialMemory.hasOtherBondedCreature(target, mob);
        TransfurContext context = TransfurContext.npcLatexHazard(mob, TransfurCause.GRAB_ABSORB);
        // Secondary assimilation is an immediate form replacement. Replaying
        // Changed's full first-transfur animation made the protected hold look
        // interrupted and could race the release packet.
        float progress = 1.0F;
        TransfurVariantInstance<?> instance = ProcessTransfur.setPlayerTransfurVariant(
                target, sourceVariant, context, progress);
        if (instance == null) {
            return;
        }

        // The Changed setter intentionally returns early for the same variant;
        // refresh its context/progression so a true second assimilation is still visible.
        instance.transfurContext = context;
        instance.transfurProgressionO = progress;
        instance.transfurProgression = progress;
        instance.setTemporaryForSuit(false);
        IAbstractChangedEntity transformedPlayer =
                IAbstractChangedEntity.forPlayerWithVariant(target, instance);
        ProcessTransfur.onNewlyTransfurred(transformedPlayer);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                SyncTransfurPacket.Builder.of(target));
        ChangedSounds.broadcastSound(target, sourceVariant.sound, 1.0F, 1.0F);

        completed = true;
        releaseGrab(target);
        InvoluntaryTransfurNegotiation.abandonForSecondaryTransfur(target);
        net.parkabird.changedsynergy.ai.FactionHostilityGrace.beginSecondary(mob, target);
        settleNearbyHostility(target);
        if (bondConflict) {
            HuntAIEvents.celebrateBondConflictAbsorption(mob, target);
        } else {
            NpcDialogue.trigger(mob, target, Cue.SECONDARY_TRANSFUR);
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

    private void releaseGrab(ServerPlayer grabbedPlayer) {
        if (ability == null || grabbedPlayer == null || ability.grabbedEntity != grabbedPlayer) {
            return;
        }
        ability.releaseEntity(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, grabbedPlayer, GrabType.RELEASE));
    }

    /**
     * Addon's drop goal reads CombatTracker's old damage entries. They describe
     * the attacks that established hostility, not a new interruption of this hold.
     */
    private void clearRecentCombat() {
        if (mob.getCombatTracker() instanceof CombatTrackerAccessor tracker) {
            tracker.getEntries().clear();
        }
        mob.setLastHurtByMob(null);
        mob.setLastHurtByPlayer(null);
    }
}
