package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Supplies the wheel's ALWAYS mode for creatures without a native pet backend. */
public final class FallbackBondedTargetGoal extends Goal {
    private static final double SEARCH_RADIUS = 16.0D;
    private final ChangedEntity pet;
    @Nullable private LivingEntity candidate;

    public FallbackBondedTargetGoal(ChangedEntity pet) {
        this.pet = pet;
        setFlags(EnumSet.of(Flag.TARGET));
    }

    @Override
    public boolean canUse() {
        if (pet.getTarget() != null || pet.getRandom().nextInt(10) != 0
                || !BondedPetSettings.mayAcquireAlwaysTarget(pet)) {
            return false;
        }
        candidate = BondedPetSettings.findAlwaysTarget(pet, SEARCH_RADIUS);
        return candidate != null;
    }

    @Override
    public void start() {
        if (candidate != null) {
            pet.setTarget(candidate);
        }
        candidate = null;
    }

    @Override
    public boolean canContinueToUse() {
        return false;
    }
}
