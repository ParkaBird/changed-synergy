package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/** Holds a waiting aquatic companion on dry land until the owner calls it. */
public final class BondedShoreWaitGoal extends Goal {
    private final ChangedEntity mob;

    public BondedShoreWaitGoal(ChangedEntity mob) {
        this.mob = mob;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        return LatexSocialMemory.isWaitingOnShore(mob)
                && mob.getTarget() == null && !mob.isInWater();
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void start() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        mob.getNavigation().stop();
    }
}
