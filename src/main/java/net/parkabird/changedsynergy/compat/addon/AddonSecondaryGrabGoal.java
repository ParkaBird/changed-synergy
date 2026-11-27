package net.parkabird.changedsynergy.compat.addon;

import java.util.EnumSet;
import net.foxyas.changedaddon.ability.api.GrabEntityAbilityExtensor;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.foxyas.changedaddon.mixins.entity.CombatTrackerAccessor;
import net.ltxprogrammer.changed.Changed;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.FactionHostilityGrace;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.ai.TransfurConsciousness;

/** Shared arm-hold and secondary-transfur lifecycle for Changed Addon grabbers. */
abstract class AddonSecondaryGrabGoal extends Goal {
    protected static final int SECONDARY_HOLD_TICKS = 106;
    protected static final double GRAB_DISTANCE_SQR = 2.5D * 2.5D;

    protected final ChangedEntity mob;
    protected final IGrabberEntity grabber;
    protected ServerPlayer target;
    protected GrabEntityAbilityInstance ability;
    protected int holdTicks;
    protected boolean completed;

    protected AddonSecondaryGrabGoal(ChangedEntity mob, IGrabberEntity grabber) {
        this.mob = mob;
        this.grabber = grabber;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    protected boolean isWithinGrabReach(ServerPlayer player) {
        return mob.getBoundingBox().inflate(0.75D).intersects(player.getBoundingBox())
                || mob.distanceToSqr(player) <= GRAB_DISTANCE_SQR;
    }

    protected boolean beginArmHold(float soundPitch, boolean clearGrabbedControl) {
        if (target == null || ability == null
                || !(ability instanceof GrabEntityAbilityExtensor extensor)) {
            return false;
        }
        clearRecentCombat();
        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        if (clearGrabbedControl) {
            ability.grabbedHasControl = false;
        }
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(target)) {
            target = null;
            return false;
        }

        LatexSocialMemory.beginSecondaryGrab(mob, target);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, target, GrabType.ARMS));
        mob.setTarget(null);
        mob.getNavigation().stop();
        ChangedSounds.broadcastSound(mob, ChangedSounds.LATEX_GRAB_ENTITY, 1.0F, soundPitch);
        grabber.applyGrabCooldown(0);
        return true;
    }

    protected boolean maintainHold() {
        if (target == null || ability == null || ability.grabbedEntity != target) {
            return false;
        }
        clearRecentCombat();
        mob.setTarget(null);
        mob.getNavigation().stop();
        mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        return true;
    }

    protected void releaseGrab(ServerPlayer grabbedPlayer) {
        if (ability == null || grabbedPlayer == null || ability.grabbedEntity != grabbedPlayer) {
            return;
        }
        ability.releaseEntity(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, grabbedPlayer, GrabType.RELEASE));
    }

    protected boolean endHold(int completedCooldown, int interruptedCooldown) {
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
        grabber.applyGrabCooldown(wasCompleted ? completedCooldown : interruptedCooldown);

        target = null;
        ability = null;
        holdTicks = 0;
        completed = false;
        return !wasCompleted;
    }

    protected boolean applySecondaryTransfur(TransfurCause cause) {
        if (completed || target == null || ability == null) {
            return false;
        }
        TransfurVariant<?> sourceVariant = mob.getSelfVariant();
        if (sourceVariant == null) {
            return false;
        }

        TransfurContext context = TransfurContext.npcLatexHazard(mob, cause);
        // Secondary assimilation is an immediate form replacement. Replaying
        // Changed's full first-transfur animation made the protected hold look
        // interrupted and could race the release packet.
        float progress = 1.0F;
        TransfurVariantInstance<?> instance = ProcessTransfur.setPlayerTransfurVariant(
                target, sourceVariant, context, progress);
        if (instance == null) {
            return false;
        }

        // The Changed setter intentionally returns early for the same variant;
        // refresh its context/progression so a true second assimilation is still visible.
        instance.transfurContext = context;
        instance.transfurProgressionO = progress;
        instance.transfurProgression = progress;
        instance.setTemporaryForSuit(false);
        TransfurConsciousness.applyInvoluntaryOutcome(target, instance);
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
        FactionHostilityGrace.beginSecondary(mob, target);
        return true;
    }

    /**
     * Addon's drop goal reads CombatTracker's old damage entries. They describe
     * the attacks that established hostility, not a new interruption of this hold.
     */
    protected void clearRecentCombat() {
        if (mob.getCombatTracker() instanceof CombatTrackerAccessor tracker) {
            tracker.getEntries().clear();
        }
        mob.setLastHurtByMob(null);
        mob.setLastHurtByPlayer(null);
    }
}
