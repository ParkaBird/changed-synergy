package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.beast.AbstractDarkLatexEntity;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;

/**
 * Persistent wheel settings for bonded creatures that have neither Changed's
 * dark-latex pet implementation nor Addon's generic pet mixin.
 */
public final class BondedPetSettings {
    public static final int TARGET_TRANSFURABLE = 0;
    public static final int TARGET_MONSTERS = 1;
    public static final int TARGET_HOSTILE_TO_OWNER = 2;
    public static final int ATTACK_ALWAYS_KILL = 0;
    public static final int ATTACK_TRY_TRANSFUR = 1;
    public static final int CONDITION_NEVER = 0;
    public static final int CONDITION_ALWAYS = 1;
    public static final int CONDITION_OWNER_HOSTILE = 2;
    public static final int FAVOR_NONE = 0;
    public static final int FAVOR_FISHING = 1;
    public static final int FAVOR_CAVING = 2;

    private static final String DATA_KEY = "ChangedSynergyBondedPetSettings";
    private static final String TARGET = "TargetType";
    private static final String ATTACK = "AttackType";
    private static final String CONDITION = "AttackCondition";
    private static final String FAVOR = "Favor";

    private BondedPetSettings() {
    }

    public static boolean usesFallbackBackend(ChangedEntity pet) {
        return !(pet instanceof AbstractDarkLatexEntity)
                && !ChangedAddonCompat.supportsBondedPetBackend(pet);
    }

    /** target type, attack type, attack condition and favor ordinals. */
    public static int[] stateFor(ChangedEntity pet) {
        if (!usesFallbackBackend(pet)) {
            return ChangedAddonCompat.bondedMenuState(pet);
        }
        CompoundTag data = data(pet);
        return new int[]{
                clamp(data.getInt(TARGET), 0, 2),
                clamp(data.getInt(ATTACK), 0, 1),
                data.contains(CONDITION)
                        ? clamp(data.getInt(CONDITION), 0, 2)
                        : CONDITION_OWNER_HOSTILE,
                clamp(data.getInt(FAVOR), 0, 2)
        };
    }

    public static void initialize(ChangedEntity pet) {
        if (!usesFallbackBackend(pet)) {
            return;
        }
        CompoundTag data = data(pet);
        if (!data.contains(CONDITION)) {
            data.putInt(CONDITION, CONDITION_OWNER_HOSTILE);
        }
    }

    public static boolean handleCommand(ChangedEntity pet, String command) {
        if (!usesFallbackBackend(pet)) {
            return false;
        }
        CompoundTag data = data(pet);
        int[] state = stateFor(pet);
        switch (command) {
            case "cycle_target_type" -> data.putInt(TARGET, (state[0] + 1) % 3);
            case "cycle_attack_type" -> data.putInt(ATTACK, (state[1] + 1) % 2);
            case "cycle_attack_condition" ->
                    data.putInt(CONDITION, (state[2] + 1) % 3);
            case "favor_fishing" -> data.putInt(
                    FAVOR, state[3] == FAVOR_FISHING ? FAVOR_NONE : FAVOR_FISHING);
            case "favor_caving" -> data.putInt(
                    FAVOR, state[3] == FAVOR_CAVING ? FAVOR_NONE : FAVOR_CAVING);
            default -> {
                return false;
            }
        }
        if (command.startsWith("cycle_") && !"cycle_attack_type".equals(command)) {
            pet.setTarget(null);
        }
        pet.getNavigation().stop();
        return true;
    }

    public static int favor(ChangedEntity pet) {
        if (pet instanceof AbstractDarkLatexEntity darkLatex) {
            return darkLatex.getCurrentFavor().ordinal();
        }
        return usesFallbackBackend(pet)
                ? stateFor(pet)[3]
                : ChangedAddonCompat.bondedMenuState(pet)[3];
    }

    public static void clearFavor(ChangedEntity pet) {
        if (usesFallbackBackend(pet)) {
            data(pet).putInt(FAVOR, FAVOR_NONE);
            pet.getNavigation().stop();
        }
    }

    public static boolean hasWorkFavor(ChangedEntity pet) {
        int favor = favor(pet);
        return favor == FAVOR_FISHING || favor == FAVOR_CAVING;
    }

    public static boolean hasRunningUtilityGoal(ChangedEntity pet) {
        return pet.goalSelector.getAvailableGoals().stream()
                .filter(WrappedGoal::isRunning)
                .map(WrappedGoal::getGoal)
                .map(goal -> goal.getClass().getSimpleName())
                .anyMatch(name -> name.contains("FishingGoal")
                        || name.contains("CaveHarvestGoal")
                        || name.contains("CaveTorchingGoal")
                        || name.equals("FallbackBondedWorkGoal"));
    }

    public static boolean allowsDefense(
            ChangedEntity pet,
            @Nullable ServerPlayer owner,
            LivingEntity target) {
        if (!usesFallbackBackend(pet)) {
            return ChangedAddonCompat.allowsBondedDefense(pet, target);
        }
        if (LatexSocialMemory.isPetDefenseForced(pet, target)) {
            return true;
        }
        int[] state = stateFor(pet);
        if (state[2] == CONDITION_NEVER
                || state[2] == CONDITION_OWNER_HOSTILE
                        && !BondedOwnerDefenseGoal.hasRecentConflict(pet, owner, target)) {
            return false;
        }
        return matchesTargetType(pet, owner, target, state[0]);
    }

    public static boolean suppressTransfurAttack(ChangedEntity pet) {
        return usesFallbackBackend(pet)
                && LatexSocialMemory.hasActiveBond(pet)
                && stateFor(pet)[1] == ATTACK_ALWAYS_KILL;
    }

    public static boolean mayAcquireAlwaysTarget(ChangedEntity pet) {
        return usesFallbackBackend(pet)
                && LatexSocialMemory.hasActiveBond(pet)
                && stateFor(pet)[2] == CONDITION_ALWAYS;
    }

    public static boolean isValidConfiguredTarget(
            ChangedEntity pet,
            @Nullable ServerPlayer owner,
            LivingEntity target) {
        if (target == pet || target == owner || !target.isAlive()
                || target.isRemoved() || pet.isAlliedTo(target)
                || owner != null && target.isAlliedTo(owner)
                || !pet.canAttack(target)) {
            return false;
        }
        if (target instanceof Player player
                && (player.isCreative() || player.isSpectator()
                        || owner != null && !owner.canHarmPlayer(player))) {
            return false;
        }
        if (target instanceof ChangedEntity other
                && (LatexSocialMemory.hasSamePetOwner(pet, other)
                        || LatexCreatureCombatRules.areCompatriots(pet, other)
                        || LatexCreatureCombatRules.mustRejectTarget(pet, other))) {
            return false;
        }
        return allowsDefense(pet, owner, target);
    }

    @Nullable
    public static LivingEntity findAlwaysTarget(ChangedEntity pet, double radius) {
        ServerPlayer owner = LatexSocialMemory.getPetOwner(pet);
        if (owner == null || !mayAcquireAlwaysTarget(pet)) {
            return null;
        }
        return pet.level().getEntitiesOfClass(
                        LivingEntity.class,
                        pet.getBoundingBox().inflate(radius, radius * 0.5D, radius),
                        candidate -> isValidConfiguredTarget(pet, owner, candidate))
                .stream()
                .min(Comparator.comparingDouble(pet::distanceToSqr))
                .orElse(null);
    }

    private static boolean matchesTargetType(
            ChangedEntity pet,
            @Nullable ServerPlayer owner,
            LivingEntity target,
            int targetType) {
        LatexType petType = LatexType.getEntityLatexType(pet);
        LatexType targetTypeValue = LatexType.getEntityLatexType(target);
        boolean hostileLatex = petType != null && targetTypeValue != null
                && petType.isHostileTo(targetTypeValue);
        return switch (targetType) {
            case TARGET_MONSTERS -> hostileLatex
                    || target.getType().getCategory() == MobCategory.MONSTER;
            case TARGET_HOSTILE_TO_OWNER -> owner != null
                    && (target instanceof Mob mob && mob.getTarget() == owner
                            || owner.getLastHurtMob() == target
                            || owner.getLastHurtByMob() == target);
            default -> hostileLatex
                    || target.getType().is(ChangedTags.EntityTypes.HUMANOIDS)
                    || target instanceof ChangedEntity;
        };
    }

    private static CompoundTag data(ChangedEntity pet) {
        CompoundTag persistent = pet.getPersistentData();
        if (!persistent.contains(DATA_KEY)) {
            CompoundTag defaults = new CompoundTag();
            defaults.putInt(TARGET, TARGET_TRANSFURABLE);
            defaults.putInt(ATTACK, ATTACK_TRY_TRANSFUR);
            defaults.putInt(CONDITION, CONDITION_OWNER_HOSTILE);
            defaults.putInt(FAVOR, FAVOR_NONE);
            persistent.put(DATA_KEY, defaults);
        }
        return persistent.getCompound(DATA_KEY);
    }

    private static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
