package net.parkabird.changedsynergy.ai;

import java.util.EnumSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.CreatureCacheGuardService.Assignment;

/** Returns a cache guard to its original post after the native chase ends. */
public final class CacheGuardReturnGoal extends Goal {
    public static final int PRIORITY = 1;
    private static final int RETURN_TIMEOUT_TICKS = 260;
    private static final double ARRIVAL_DISTANCE_SQR = 2.5D * 2.5D;
    private static final double RETURN_SPEED = 0.3D;

    private final ChangedEntity guard;
    private Assignment assignment;
    private int repathTicks;
    private int returnTicks;

    public CacheGuardReturnGoal(ChangedEntity guard) {
        this.guard = guard;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (!(guard.level() instanceof ServerLevel)
                || guard.isNoAi()
                || CreatureLifeMemory.role(guard)
                        != CreatureLifeMemory.GroupRole.GUARD) {
            return false;
        }
        assignment = CreatureCacheGuardService.assignment(guard)
                .filter(Assignment::defenseCompleted)
                .orElse(null);
        return assignment != null;
    }

    @Override
    public boolean canContinueToUse() {
        return guard.isAlive()
                && !guard.isNoAi()
                && CreatureCacheGuardService.assignment(guard)
                        .filter(Assignment::defenseCompleted)
                        .isPresent();
    }

    @Override
    public void start() {
        repathTicks = 0;
        returnTicks = 0;
        CreatureCacheGuardService.clearCombatState(guard, null);
    }

    @Override
    public void tick() {
        if (assignment == null) {
            return;
        }
        returnTicks++;
        CreatureCacheGuardService.clearCombatState(guard, null);
        Vec3 post = Vec3.atBottomCenterOf(assignment.post());
        if (guard.distanceToSqr(post) <= ARRIVAL_DISTANCE_SQR
                || returnTicks > RETURN_TIMEOUT_TICKS) {
            CreatureCacheGuardService.finish(guard, true);
            assignment = null;
            return;
        }
        if (--repathTicks <= 0 || guard.getNavigation().isDone()) {
            repathTicks = 18;
            guard.getNavigation().moveTo(
                    post.x, post.y, post.z, RETURN_SPEED);
        }
    }

    @Override
    public void stop() {
        assignment = null;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }
}
