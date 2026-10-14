package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.ability.GrabEntityAbility;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Enemy;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.minecraft.world.entity.player.Player;

/** Generic owner-defense behavior for bonded latexes without a native pet implementation. */
public final class BondedOwnerDefenseGoal extends TargetGoal {
    public enum Mode {
        OWNER_HURT_BY,
        OWNER_HURT_TARGET
    }

    private final ChangedEntity pet;
    private final Mode mode;
    private ServerPlayer owner;
    private LivingEntity threat;
    private int timestamp;

    public BondedOwnerDefenseGoal(ChangedEntity pet, Mode mode) {
        super(pet, false);
        this.pet = pet;
        this.mode = mode;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        owner = LatexSocialMemory.getPetOwner(pet);
        if (!canProtectOwner(pet, owner)) {
            return false;
        }

        int newTimestamp;
        LivingEntity body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(owner);
        if (mode == Mode.OWNER_HURT_BY) {
            threat = body.getLastHurtByMob();
            newTimestamp = body.getLastHurtByMobTimestamp();
        } else {
            threat = body.getLastHurtMob();
            newTimestamp = body.getLastHurtMobTimestamp();
        }
        return newTimestamp != timestamp && canDefendAgainst(threat);
    }

    @Override
    public void start() {
        LatexSocialMemory.authorizePetDefense(pet, threat, 200L);
        pet.setTarget(threat);
        LivingEntity body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(owner);
        timestamp = mode == Mode.OWNER_HURT_BY
                ? body.getLastHurtByMobTimestamp()
                : body.getLastHurtMobTimestamp();
        super.start();
    }

    @Override
    public boolean canContinueToUse() {
        return canProtectOwner(pet, owner) && canDefendAgainst(threat)
                && super.canContinueToUse();
    }

    private boolean canDefendAgainst(LivingEntity candidate) {
        if (candidate == null || !candidate.isAlive() || candidate == owner
                || candidate instanceof Creeper || candidate instanceof Ghast
                || candidate.isAlliedTo(owner)) {
            return false;
        }
        if (candidate instanceof Player player
                && (player.isCreative() || player.isSpectator() || !owner.canHarmPlayer(player))) {
            return false;
        }
        if (candidate instanceof ChangedEntity other
                && (LatexSocialMemory.hasSamePetOwner(pet, other)
                        || LatexCreatureCombatRules.areCompatriots(pet, other)
                        || LatexCreatureCombatRules.sharePlayerAlliance(pet, other))) {
            return false;
        }
        return allowsConfiguredDefense(pet, owner, candidate)
                && pet.canAttack(candidate);
    }

    /**
     * Keeps Synergy's generic defense goals on the same attack policy selected
     * in Changed or Addon's native pet wheel.
     */
    public static boolean allowsConfiguredDefense(
            ChangedEntity pet,
            ServerPlayer owner,
            LivingEntity candidate) {
        if (!PlayerOutpostService.mayAttackTarget(pet, candidate)) return false;
        if (candidate instanceof ChangedEntity other && LatexCreatureCombatRules.sharePlayerAlliance(pet, other)) return false;
        if (ChangedSynergyConfig.COMMON.companionMonsterAssistOnly.get()
                && (!(candidate instanceof Enemy)
                        || !hasRecentConflict(pet, owner, candidate))) return false;
        if (pet instanceof AbstractDarkLatexEntity darkLatex) {
            DarkLatexAttackCondition condition = darkLatex.getAttackCondition();
            if (condition == DarkLatexAttackCondition.NEVER
                    || condition == DarkLatexAttackCondition.OWNER_IS_HOSTILE
                            && !hasRecentConflict(pet, owner, candidate)) {
                return false;
            }
            return BondedPetSettings.matchesTargetType(pet, owner, candidate, darkLatex.getTargetType().ordinal());
        }
        return BondedPetSettings.allowsDefense(pet, owner, candidate);
    }

    /** Automatic protection is local and must not override a wait order. */
    public static boolean canProtectOwner(ChangedEntity pet, ServerPlayer owner) {
        if (owner == null) return false;
        LivingEntity body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(owner);
        return owner != null && owner.isAlive() && !owner.isSpectator()
                && body.isAlive() && !body.isRemoved()
                && !(body == owner && net.parkabird.changedsynergy.compat.TrueTransfurCompat.isInstinctShell(owner))
                && pet.isAlive() && pet.level() == body.level()
                && LatexSocialMemory.isFollowingOwner(pet)
                && pet.distanceToSqr(body) <= 20.0D * 20.0D;
    }

    /**
     * The exact, short-lived conflicts that qualify for the default
     * "owner is hostile" policy. Merely selecting, approaching or looking at
     * the owner is deliberately not evidence of hostility.
     */
    public static boolean hasRecentConflict(
            ChangedEntity pet,
            ServerPlayer owner,
            LivingEntity candidate) {
        if (candidate == null) {
            return false;
        }
        if (owner != null) {
            var grabber = GrabEntityAbility.getGrabber(owner);
            var grab = grabber == null ? null : grabber.getAbilityInstanceSafe(
                    ChangedAbilities.GRAB_ENTITY_ABILITY.get()).orElse(null);
            if (grabber != null && grabber.getEntity() == candidate
                    && grab != null && grab.grabbedEntity == owner && !grab.suited
                    && !BondedOwnerGrabRescueGoal.isFriendlyGrab(owner, candidate, grab)) {
                return true;
            }
        }
        if (owner != null
                && (owner.getLastHurtMob() == candidate
                        && owner.tickCount - owner.getLastHurtMobTimestamp() < 600
                    || owner.getLastHurtByMob() == candidate
                        && owner.tickCount - owner.getLastHurtByMobTimestamp() < 600)) {
            return true;
        }
        if (owner != null) {
            LivingEntity body = net.parkabird.changedsynergy.compat.TrueTransfurCompat.interactionBody(owner);
            if (body != owner && (body.getLastHurtMob() == candidate
                    && body.tickCount - body.getLastHurtMobTimestamp() < 600
                    || body.getLastHurtByMob() == candidate
                    && body.tickCount - body.getLastHurtByMobTimestamp() < 600)) return true;
        }
        return pet.getLastHurtByMob() == candidate
                && pet.tickCount - pet.getLastHurtByMobTimestamp() < 600;
    }
}
