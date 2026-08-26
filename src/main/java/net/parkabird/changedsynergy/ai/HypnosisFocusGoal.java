package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;

/** Holds a hypnotist's attention on the victim without letting melee run beside the QTE. */
public final class HypnosisFocusGoal extends Goal {
    private final ChangedEntity creature;

    public HypnosisFocusGoal(ChangedEntity creature) {
        this.creature = creature;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    @Override
    public boolean canUse() {
        return victim() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return victim() != null;
    }

    @Override
    public void start() {
        creature.getNavigation().stop();
        creature.setAggressive(false);
    }

    @Override
    public void tick() {
        ServerPlayer victim = victim();
        creature.getNavigation().stop();
        creature.setAggressive(false);
        if (victim != null) {
            creature.getLookControl().setLookAt(victim, 30.0F, 30.0F);
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    private ServerPlayer victim() {
        return HypnosisQteService.getActiveVictim(creature);
    }
}
