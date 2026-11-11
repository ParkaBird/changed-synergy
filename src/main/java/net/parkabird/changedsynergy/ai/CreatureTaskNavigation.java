package net.parkabird.changedsynergy.ai;

import java.util.Map;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

/** Failure backoff shared by optional work and patrol goals, not combat or owner following. */
final class CreatureTaskNavigation {
    private record Failure(BlockPos target, Vec3 position, long retryAt, int attempts) {}
    private static final Map<ChangedEntity, Failure> FAILURES = new WeakHashMap<>();

    private CreatureTaskNavigation() {}

    static void clearFailure(ChangedEntity mob) { FAILURES.remove(mob); }

    static boolean moveTo(ChangedEntity mob, BlockPos target, int accuracy, double speed) {
        var navigation = mob.getNavigation();
        if (!mob.level().hasChunkAt(target)) return false;
        if (!navigation.isDone() && target.equals(navigation.getTargetPos())) return true;
        if (!PlayerOutpostGoal.canNavigate(mob)) return true;
        long now = mob.level().getGameTime();
        Failure previous = FAILURES.get(mob);
        if (previous != null && (!previous.target().equals(target)
                || mob.position().distanceToSqr(previous.position()) >= 4.0D
                || previous.retryAt() - now > 220L)) previous = null;
        if (previous != null && now < previous.retryAt()) return false;
        var path = NaturalCampService.path(mob, target, accuracy);
        // Distant deliveries may use partial paths, but a path ending at our feet is another failed search.
        boolean advances = path != null && (path.canReach()
                || path.getNodeCount() >= 2 && path.getEndNode() != null
                    && Vec3.atBottomCenterOf(path.getEndNode().asBlockPos()).distanceToSqr(Vec3.atBottomCenterOf(target))
                        + 1.0D < mob.position().distanceToSqr(Vec3.atBottomCenterOf(target)));
        if (advances && navigation.moveTo(path, speed)) {
            FAILURES.remove(mob);
            return true;
        }
        int attempts = previous == null ? 1 : Math.min(4, previous.attempts() + 1);
        long delay = Math.min(200L, 40L << (attempts - 1));
        FAILURES.put(mob, new Failure(target.immutable(), mob.position(),
                now + delay + Math.floorMod(mob.getId(), 20), attempts));
        return false;
    }
}
