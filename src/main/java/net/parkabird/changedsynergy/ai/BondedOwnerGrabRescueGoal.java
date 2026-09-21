package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.ability.IAbstractChangedEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.Emote;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket;
import net.ltxprogrammer.changed.network.packet.GrabEntityPacket.GrabType;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.network.PacketDistributor;

/**
 * A bonded creature intercepts a hostile arm-grab, strikes the actual grabber,
 * and leaves that grabber as its combat target after the owner is released.
 */
public final class BondedOwnerGrabRescueGoal extends Goal {
    private static final long OWNER_DEFENSE_TICKS = 600L;
    private static final long REGRAB_BLOCK_TICKS = 100L;
    private static final double RESCUE_SPEED = 1.0D;
    private static final double ATTACK_REACH_SQR = 2.75D * 2.75D;

    private final ChangedEntity pet;
    private final CompanionFollowNavigation followNavigation;
    private ServerPlayer owner;
    private LivingEntity grabberEntity;
    private GrabEntityAbilityInstance grabAbility;
    private int repathTicks;

    public BondedOwnerGrabRescueGoal(ChangedEntity pet) {
        this.pet = pet;
        this.followNavigation = new CompanionFollowNavigation(pet);
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        owner = LatexSocialMemory.getPetOwner(pet);
        GrabContext context = findHostileGrab(owner);
        if (context == null || !canDefendAgainst(pet, owner, context.grabber())) {
            clearState();
            return false;
        }

        grabberEntity = context.grabber();
        grabAbility = context.ability();
        boolean forcedRevertedRescue = !ProcessTransfur.isPlayerTransfurred(owner);
        if (!forcedRevertedRescue
                && !BondedOwnerDefenseGoal.allowsConfiguredDefense(
                        pet, owner, grabberEntity)) {
            clearState();
            return false;
        }
        if (forcedRevertedRescue) {
            LatexSocialMemory.authorizeForcedPetDefense(
                    pet, grabberEntity, OWNER_DEFENSE_TICKS);
        } else {
            LatexSocialMemory.authorizePetDefense(
                    pet, grabberEntity, OWNER_DEFENSE_TICKS);
        }
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        GrabContext context = findHostileGrab(owner);
        if (context == null || context.grabber() != grabberEntity
                || !canDefendAgainst(pet, owner, grabberEntity)) {
            return false;
        }
        if (ProcessTransfur.isPlayerTransfurred(owner)
                && !BondedOwnerDefenseGoal.allowsConfiguredDefense(
                        pet, owner, grabberEntity)) {
            return false;
        }
        grabAbility = context.ability();
        return true;
    }

    @Override
    public void start() {
        repathTicks = 0;
        followNavigation.reset();
        pet.setTarget(grabberEntity);
        NpcDialogue.emoteOnly(pet, Emote.ANGRY);
    }

    @Override
    public void tick() {
        if (owner == null || grabberEntity == null || grabAbility == null) {
            return;
        }

        pet.setTarget(grabberEntity);
        pet.getLookControl().setLookAt(grabberEntity, 30.0F, 30.0F);
        if (pet.getBoundingBox().inflate(0.8D).intersects(grabberEntity.getBoundingBox())
                || pet.distanceToSqr(grabberEntity) <= ATTACK_REACH_SQR) {
            pet.getNavigation().stop();
            pet.swing(InteractionHand.MAIN_HAND);
            pet.doHurtTarget(grabberEntity);
            releaseOwner();
            return;
        }

        followNavigation.tickProgress(true);
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
            followNavigation.moveToward(grabberEntity, RESCUE_SPEED);
        }
    }

    @Override
    public void stop() {
        pet.getNavigation().stop();
        followNavigation.reset();
        if (grabberEntity != null && grabberEntity.isAlive()) {
            // The rescue ends the hold, not the fight.
            pet.setTarget(grabberEntity);
        }
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

    /** Alerts every loaded bond whose current function-wheel policy permits combat. */
    public static void alertBondedCreatures(
            ServerPlayer owner,
            LivingEntity attacker) {
        if (owner == null || attacker == null || !owner.isAlive()) {
            return;
        }
        for (ChangedEntity pet : LatexSocialMemory.loadedBondedCreatures(owner)) {
            if (!canDefendAgainst(pet, owner, attacker)
                    || !BondedOwnerDefenseGoal.allowsConfiguredDefense(
                            pet, owner, attacker)) {
                continue;
            }
            LatexSocialMemory.authorizePetDefense(
                    pet, attacker, OWNER_DEFENSE_TICKS);
            pet.setTarget(attacker);
        }
    }

    public static boolean canDefendAgainst(
            ChangedEntity pet,
            ServerPlayer owner,
            LivingEntity attacker) {
        if (pet == null || owner == null || attacker == null
                || !pet.isAlive() || !owner.isAlive() || !attacker.isAlive()
                || attacker == owner || attacker == pet
                || pet.level() != attacker.level()) {
            return false;
        }
        if (attacker instanceof Player player
                && (player.isCreative() || player.isSpectator())) {
            return false;
        }
        if (attacker instanceof ChangedEntity other
                && (LatexSocialMemory.isBonded(other, owner)
                        || LatexSocialMemory.isPetOwner(other, owner)
                        || LatexSocialMemory.hasSamePetOwner(pet, other)
                        || LatexCreatureCombatRules.areCompatriots(pet, other))) {
            return false;
        }
        return true;
    }

    private GrabContext findHostileGrab(ServerPlayer player) {
        if (player == null || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        IAbstractChangedEntity abstraction = GrabEntityAbility.getGrabber(player);
        if (abstraction == null) {
            return null;
        }
        LivingEntity currentGrabber = abstraction.getEntity();
        GrabEntityAbilityInstance ability = abstraction
                .getAbilityInstanceSafe(ChangedAbilities.GRAB_ENTITY_ABILITY.get())
                .orElse(null);
        if (currentGrabber == null || ability == null
                || ability.grabbedEntity != player || ability.suited) {
            return null;
        }
        return new GrabContext(currentGrabber, ability);
    }

    private void releaseOwner() {
        if (owner == null || grabberEntity == null || grabAbility == null
                || grabAbility.grabbedEntity != owner || grabAbility.suited) {
            return;
        }
        if (grabberEntity instanceof ChangedEntity changedGrabber) {
            LatexSocialMemory.blockGrabAgainst(
                    changedGrabber, owner, REGRAB_BLOCK_TICKS);
            LatexSocialMemory.endSecondaryGrab(changedGrabber, owner);
        }
        grabAbility.attackDown = false;
        grabAbility.useDown = false;
        grabAbility.releaseEntity(false);
        Changed.PACKET_HANDLER.send(
                PacketDistributor.TRACKING_ENTITY.with(() -> grabberEntity),
                new GrabEntityPacket(grabberEntity, owner, GrabType.RELEASE));
    }

    private void clearState() {
        owner = null;
        grabberEntity = null;
        grabAbility = null;
        repathTicks = 0;
    }

    private record GrabContext(
            LivingEntity grabber,
            GrabEntityAbilityInstance ability) {
    }
}
