package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;

/** A bonded creature reaches its reverted owner to shelter either partner. */
public final class BondedEmergencySuitGoal extends Goal {
    private static final double SUIT_REACH_SQR = 2.5 * 2.5;
    private static final double NORMAL_RUN_SPEED = 0.35D;
    private static final double URGENT_RESCUE_SPEED = 1.0D;
    private static final float SELF_RECOVERY_TRIGGER_HEALTH =
            LatexCreatureCombatRules.BONDED_SAFETY_HEALTH_RATIO;
    private static final float SELF_RECOVERY_STOP_HEALTH = 0.55F;

    private final ChangedEntity pet;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer owner;
    private boolean selfRecovery;
    private int repathTicks;

    public BondedEmergencySuitGoal(ChangedEntity pet) {
        this.pet = pet;
        this.followNavigation = new CompanionFollowNavigation(pet);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        owner = LatexSocialMemory.getPetOwner(pet);
        boolean ownerEmergency = owner != null
                && BondedSuitService.needsEmergencyRescue(pet, owner);
        selfRecovery = !ownerEmergency
                && needsSelfRecovery(SELF_RECOVERY_TRIGGER_HEALTH);
        return owner != null
                && (ownerEmergency || selfRecovery)
                && BondedSuitService.canStartSuit(pet, owner)
                && BondedSuitService.ability(pet) != null
                && LatexSocialMemory.canStartEmergencyRescue(pet)
                && !BondedSuitService.isSuitingOwner(pet, owner);
    }

    @Override
    public boolean canContinueToUse() {
        return owner != null
                && BondedSuitService.canStartSuit(pet, owner)
                && (BondedSuitService.needsEmergencyRescue(pet, owner)
                        || selfRecovery
                                && needsSelfRecovery(SELF_RECOVERY_STOP_HEALTH))
                && LatexSocialMemory.isPetOwner(pet, owner)
                && !BondedSuitService.isSuitingOwner(pet, owner);
    }

    @Override
    public void start() {
        repathTicks = 0;
        pet.setTarget(null);
        pet.setLastHurtByMob(null);
        LatexSocialMemory.clearPetDefense(pet, null);
        HuntMemory.clear(pet);
        NpcDialogue.emoteOnly(pet, Emote.NERVOUS);
        followNavigation.reset();
    }

    @Override
    public void tick() {
        if (owner == null) {
            return;
        }
        pet.setTarget(null);
        pet.getLookControl().setLookAt(owner, 30.0F, 30.0F);
        boolean drowningRescue = BondedSuitService.needsDrowningRescue(pet, owner);
        boolean outOfReach = !pet.getBoundingBox().inflate(0.75)
                .intersects(owner.getBoundingBox())
                && pet.distanceToSqr(owner) > SUIT_REACH_SQR;
        followNavigation.tickProgress(outOfReach);
        if (pet.getBoundingBox().inflate(0.75).intersects(owner.getBoundingBox())
                || pet.distanceToSqr(owner) <= SUIT_REACH_SQR) {
            pet.getNavigation().stop();
            BondedSuitService.SuitReason reason;
            if (BondedSuitService.isTransfurRescueRequested(pet, owner)) {
                reason = BondedSuitService.SuitReason.TRANSFUR;
            } else if (drowningRescue) {
                reason = BondedSuitService.SuitReason.DROWNING;
            } else if (BondedSuitService.needsEmergencyRescue(pet, owner)) {
                reason = BondedSuitService.SuitReason.EMERGENCY;
            } else {
                reason = BondedSuitService.SuitReason.COMBAT;
            }
            BondedSuitService.suitOwner(
                    pet,
                    owner,
                    reason);
            return;
        }
        if (followNavigation.isStalled()
                && pet.level() instanceof ServerLevel level) {
            boolean recovered = BondedTeleportSafety.teleportNearOwner(
                    level, pet, owner);
            followNavigation.resetProgress();
            if (recovered) {
                return;
            }
        }
        if (--repathTicks <= 0 || pet.getNavigation().isDone()) {
            repathTicks = 5;
            double speed = BondedSuitService.isTransfurRescueRequested(
                    pet, owner)
                    ? NORMAL_RUN_SPEED
                    : URGENT_RESCUE_SPEED;
            followNavigation.moveToward(owner, speed);
        }
    }

    @Override
    public void stop() {
        owner = null;
        selfRecovery = false;
        pet.getNavigation().stop();
        followNavigation.reset();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private boolean needsSelfRecovery(float healthThreshold) {
        return pet.getHealth() <= pet.getMaxHealth() * healthThreshold;
    }
}
