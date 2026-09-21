package net.parkabird.changedsynergy.compat.addon;

import java.util.Comparator;
import java.util.EnumSet;
import net.foxyas.changedaddon.ability.api.GrabEntityAbilityExtensor;
import net.foxyas.changedaddon.entity.api.IGrabberEntity;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedSounds;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.BondedTeleportSafety;
import net.parkabird.changedsynergy.ai.CompanionFollowNavigation;
import net.parkabird.changedsynergy.ai.HuntMemory;
import net.parkabird.changedsynergy.ai.HypnosisProfile;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.event.LatexSocialEvents;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.FriendlySocialHugSyncPacket;

/** Carries a wounded bonded owner out of an active fight without assimilating them. */
public final class OrganicOwnerEvacuationGoal extends Goal {
    private static final String NEXT_RESCUE = "ChangedSynergyNextOrganicEvacuation";
    private static final double APPROACH_SPEED = 1.0D;
    /** Original emergency-evacuation pace. */
    private static final double ESCAPE_SPEED = 0.42D;
    private static final float HORSE_STEP_HEIGHT = 1.0F;
    private static final double GRAB_REACH_SQR = 2.5D * 2.5D;
    private static final double THREAT_SCAN_RANGE = 20.0D;
    private static final double MIN_PROGRESS_SQR = 0.35D * 0.35D;
    private static final int MINIMUM_HOLD_TICKS = 50;
    private static final int STUCK_RELEASE_TICKS = 60;
    private static final int MAXIMUM_HOLD_TICKS = 220;
    private static final int WATCHDOG_HOLD_TICKS = 240;

    private final ChangedEntity mob;
    private final IGrabberEntity grabber;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer owner;
    private GrabEntityAbilityInstance ability;
    private LivingEntity threat;
    private Vec3 dangerOrigin;
    private boolean holding;
    private boolean finished;
    private int holdTicks;
    private int repathTicks;
    private int stuckTicks;
    private Vec3 lastProgressPosition;
    private float previousMaxUpStep;
    private boolean raisedStepHeight;

    public OrganicOwnerEvacuationGoal(
            ChangedEntity mob,
            IGrabberEntity grabber) {
        this.mob = mob;
        this.grabber = grabber;
        this.followNavigation = new CompanionFollowNavigation(mob);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (mob.level().isClientSide
                || !LatexSocialMemory.isOrganic(mob)
                || HypnosisProfile.isHypnoticCreature(mob)
                || mob.getPersistentData().getLong(NEXT_RESCUE)
                        > mob.level().getGameTime()
                || !grabber.canUseGrab()
                || !grabber.canEntityGrab(mob.getType(), mob.level())) {
            return false;
        }
        owner = LatexSocialMemory.getPetOwner(mob);
        if (owner == null
                || !owner.isAlive()
                || owner.isCreative()
                || owner.isSpectator()
                || owner.level() != mob.level()
                || !LatexSocialMemory.organicRescueMode(mob)
                        .shouldRescue(owner)
                || GrabEntityAbility.getGrabber(owner) != null) {
            clearState();
            return false;
        }
        threat = findThreat();
        if (threat == null) {
            clearState();
            return false;
        }
        ability = grabber.getGrabAbilityInstance();
        if (!(ability instanceof GrabEntityAbilityExtensor)
                || ability.grabbedEntity != null) {
            clearState();
            return false;
        }
        dangerOrigin = threat.position();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        if (finished
                || owner == null
                || !owner.isAlive()
                || owner.level() != mob.level()
                || ability == null
                || !LatexSocialMemory.isPetOwner(mob, owner)) {
            return false;
        }
        if (holding) {
            return ability.grabbedEntity == owner
                    && LatexSocialMemory.isOrganicEvacuationActive(mob, owner);
        }
        return LatexSocialMemory.isPetOwner(mob, owner)
                && LatexSocialMemory.organicRescueMode(mob).shouldRescue(owner)
                && GrabEntityAbility.getGrabber(owner) == null;
    }

    @Override
    public void start() {
        holding = false;
        finished = false;
        holdTicks = 0;
        repathTicks = 0;
        stuckTicks = 0;
        lastProgressPosition = null;
        mob.setSprinting(false);
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.setLastHurtByMob(null);
        LatexSocialMemory.clearPetDefense(mob, null);
        HuntMemory.clear(mob);
        followNavigation.reset();
    }

    @Override
    public void tick() {
        if (owner == null || ability == null) {
            return;
        }
        mob.setTarget(null);
        mob.setAggressive(false);
        mob.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        LatexSocialEvents.calmTowards(mob, owner);

        if (!holding) {
            followNavigation.tickProgress(
                    mob.distanceToSqr(owner) > GRAB_REACH_SQR);
            if (mob.getBoundingBox().inflate(0.75D)
                            .intersects(owner.getBoundingBox())
                    || mob.distanceToSqr(owner) <= GRAB_REACH_SQR) {
                beginHold();
                return;
            }
            if (followNavigation.isStalled()
                    && mob.level() instanceof ServerLevel level) {
                boolean recovered = BondedTeleportSafety.teleportNearOwner(
                        level, mob, owner);
                followNavigation.resetProgress();
                if (recovered) {
                    return;
                }
            }
            if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
                repathTicks = 5;
                followNavigation.moveToward(owner, APPROACH_SPEED);
            }
            return;
        }

        holdTicks++;
        if (holdTicks % 20 == 0) {
            recoverPair();
        }
        if (holdTicks % 10 == 0) {
            LivingEntity currentThreat = findThreat();
            if (currentThreat != null) {
                threat = currentThreat;
            }
            Vec3 currentPosition = mob.position();
            if (lastProgressPosition != null
                    && currentPosition.distanceToSqr(lastProgressPosition)
                            < MIN_PROGRESS_SQR) {
                stuckTicks += 10;
            } else {
                stuckTicks = 0;
            }
            lastProgressPosition = currentPosition;
        }
        if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
            repathTicks = 12;
            moveAwayFromDanger();
        }

        boolean recentlyHurt = owner.tickCount
                - owner.getLastHurtByMobTimestamp() <= 60;
        boolean safe = holdTicks >= MINIMUM_HOLD_TICKS
                && !recentlyHurt
                && findThreat() == null
                && (dangerOrigin == null
                        || mob.position().distanceToSqr(dangerOrigin) >= 12.0D * 12.0D);
        if (safe
                || stuckTicks >= STUCK_RELEASE_TICKS
                || holdTicks >= MAXIMUM_HOLD_TICKS) {
            finishHold(true);
        }
    }

    @Override
    public void stop() {
        if (holding) {
            finishHold(owner != null && owner.isAlive() && mob.isAlive());
        }
        mob.setSprinting(false);
        mob.getNavigation().stop();
        followNavigation.reset();
        if (ability instanceof GrabEntityAbilityExtensor extensor) {
            extensor.setSafeModeAuthoritative(false);
        }
        LatexSocialMemory.endOrganicEvacuation(mob);
        clearState();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public boolean isInterruptable() {
        return false;
    }

    private void beginHold() {
        if (owner == null
                || !(ability instanceof GrabEntityAbilityExtensor extensor)) {
            finished = true;
            return;
        }
        mob.getNavigation().stop();
        grabber.applyGrabCooldown(0);
        LatexSocialMemory.beginOrganicEvacuation(
                mob, owner, WATCHDOG_HOLD_TICKS);
        extensor.setAllowGrabTransfurred(true);
        extensor.setSafeModeAuthoritative(true);
        ability.suited = false;
        ability.grabbedHasControl = false;
        ability.grabStrength = 1.0F;
        ability.attackDown = false;
        ability.useDown = false;
        if (!ability.grabEntity(owner)) {
            LatexSocialMemory.endOrganicEvacuation(mob);
            extensor.setSafeModeAuthoritative(false);
            finished = true;
            return;
        }
        enableHorseStepHeight();
        holding = true;
        holdTicks = 0;
        stuckTicks = 0;
        lastProgressPosition = mob.position();
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                new GrabEntityPacket(mob, owner, GrabType.ARMS));
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> owner),
                new FriendlySocialHugSyncPacket(
                        mob.getId(), owner.getId(), true,
                        WATCHDOG_HOLD_TICKS));
        ChangedSounds.broadcastSound(
                mob, ChangedSounds.LATEX_GRAB_ENTITY, 0.8F, 0.90F);
        NpcDialogue.trigger(mob, owner, evacuationCue(true));
    }

    private void finishHold(boolean announce) {
        if (owner != null && ability != null && ability.grabbedEntity == owner) {
            ability.attackDown = false;
            ability.useDown = false;
            ability.releaseEntity(false);
            Changed.PACKET_HANDLER.send(
                    PacketDistributor.TRACKING_ENTITY.with(grabber::asMob),
                    new GrabEntityPacket(mob, owner, GrabType.RELEASE));
            ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> owner),
                    new FriendlySocialHugSyncPacket(
                            mob.getId(), owner.getId(), false, 0));
            if (announce) {
                NpcDialogue.trigger(mob, owner, evacuationCue(false));
            }
        }
        if (ability instanceof GrabEntityAbilityExtensor extensor) {
            extensor.setSafeModeAuthoritative(false);
        }
        LatexSocialMemory.endOrganicEvacuation(mob);
        mob.getPersistentData().putLong(
                NEXT_RESCUE, mob.level().getGameTime() + 100L);
        grabber.applyGrabCooldown(40);
        mob.setSprinting(false);
        mob.getNavigation().stop();
        restoreStepHeight();
        holding = false;
        finished = true;
    }

    private void enableHorseStepHeight() {
        if (raisedStepHeight) {
            return;
        }
        previousMaxUpStep = mob.maxUpStep();
        mob.setMaxUpStep(Math.max(previousMaxUpStep, HORSE_STEP_HEIGHT));
        raisedStepHeight = true;
    }

    private void restoreStepHeight() {
        if (!raisedStepHeight) {
            return;
        }
        mob.setMaxUpStep(previousMaxUpStep);
        raisedStepHeight = false;
    }

    private void moveAwayFromDanger() {
        Vec3 danger = threat != null && threat.isAlive()
                ? threat.position() : dangerOrigin;
        if (danger == null) {
            return;
        }
        Vec3 escape = DefaultRandomPos.getPosAway(mob, 18, 7, danger);
        if (escape == null) {
            Vec3 away = mob.position().subtract(danger);
            if (away.lengthSqr() < 0.01D) {
                away = new Vec3(1.0D, 0.0D, 0.0D);
            }
            escape = mob.position().add(away.normalize().scale(10.0D));
        }
        mob.getNavigation().moveTo(
                escape.x, escape.y, escape.z, ESCAPE_SPEED);
    }

    private void recoverPair() {
        if (owner == null || !owner.isAlive() || !mob.isAlive()) {
            return;
        }
        float recovery = CreaturePersonality.bondedRecoveryFraction(mob, owner);
        if (owner.getHealth() < owner.getMaxHealth()) {
            owner.heal(Math.max(1.0F, owner.getMaxHealth() * recovery));
        }
        if (mob.getHealth() < mob.getMaxHealth()) {
            mob.heal(Math.max(1.0F, mob.getMaxHealth() * recovery));
        }
    }

    private LivingEntity findThreat() {
        if (owner == null) {
            return null;
        }
        LivingEntity recent = owner.getLastHurtByMob();
        if (recent != null
                && recent.isAlive()
                && owner.tickCount - owner.getLastHurtByMobTimestamp() <= 120
                && recent != mob) {
            return recent;
        }
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive() && target != owner) {
            return target;
        }
        return owner.level().getEntitiesOfClass(
                        Mob.class,
                        owner.getBoundingBox().inflate(THREAT_SCAN_RANGE),
                        candidate -> candidate.isAlive()
                                && candidate != mob
                                && (candidate.getTarget() == owner
                                        || candidate.getTarget() == mob))
                .stream()
                .min(Comparator.comparingDouble(
                        candidate -> candidate.distanceToSqr(owner)))
                .orElse(null);
    }

    private NpcDialogue.Cue evacuationCue(boolean start) {
        return switch (CreaturePersonality.relationshipTier(mob, owner)) {
            case CLOSE -> start
                    ? NpcDialogue.Cue.ORGANIC_EVACUATION_START_CLOSE
                    : NpcDialogue.Cue.ORGANIC_EVACUATION_RELEASE_CLOSE;
            case FAMILIAR -> start
                    ? NpcDialogue.Cue.ORGANIC_EVACUATION_START_FAMILIAR
                    : NpcDialogue.Cue.ORGANIC_EVACUATION_RELEASE_FAMILIAR;
            default -> start
                    ? NpcDialogue.Cue.ORGANIC_EVACUATION_START_NEW
                    : NpcDialogue.Cue.ORGANIC_EVACUATION_RELEASE_NEW;
        };
    }

    private void clearState() {
        restoreStepHeight();
        owner = null;
        ability = null;
        threat = null;
        dangerOrigin = null;
        holding = false;
        finished = false;
        holdTicks = 0;
        repathTicks = 0;
        stuckTicks = 0;
        lastProgressPosition = null;
    }
}
