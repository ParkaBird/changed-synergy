package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.ai.DarkLatexAttackCondition;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal.Flag;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Ghast;
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
        if (owner == null || !owner.isAlive() || owner.isSpectator()) {
            return false;
        }

        int newTimestamp;
        if (mode == Mode.OWNER_HURT_BY) {
            threat = owner.getLastHurtByMob();
            newTimestamp = owner.getLastHurtByMobTimestamp();
        } else {
            threat = owner.getLastHurtMob();
            newTimestamp = owner.getLastHurtMobTimestamp();
        }
        return newTimestamp != timestamp && canDefendAgainst(threat);
    }

    @Override
    public void start() {
        LatexSocialMemory.authorizePetDefense(pet, threat, 200L);
        pet.setTarget(threat);
        timestamp = mode == Mode.OWNER_HURT_BY
                ? owner.getLastHurtByMobTimestamp()
                : owner.getLastHurtMobTimestamp();
        super.start();
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
                        || LatexCreatureCombatRules.areCompatriots(pet, other))) {
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
        if (LatexSocialMemory.isPetDefenseForced(pet, candidate)) {
            return true;
        }
        if (pet instanceof AbstractDarkLatexEntity darkLatex) {
            DarkLatexAttackCondition condition = darkLatex.getAttackCondition();
            if (condition == DarkLatexAttackCondition.NEVER
                    || condition == DarkLatexAttackCondition.OWNER_IS_HOSTILE
                            && !hasRecentConflict(pet, owner, candidate)) {
                return false;
            }
            return darkLatex.wantsToAttack(candidate, owner);
        }
        return BondedPetSettings.allowsDefense(pet, owner, candidate);
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
        if (LatexSocialMemory.isPetDefenseForced(pet, candidate)) {
            return true;
        }
        if (owner != null
                && (owner.getLastHurtMob() == candidate
                        && owner.tickCount - owner.getLastHurtMobTimestamp() < 600
                    || owner.getLastHurtByMob() == candidate
                        && owner.tickCount - owner.getLastHurtByMobTimestamp() < 600)) {
            return true;
        }
        return pet.getLastHurtByMob() == candidate
                && pet.tickCount - pet.getLastHurtByMobTimestamp() < 600;
    }
}
