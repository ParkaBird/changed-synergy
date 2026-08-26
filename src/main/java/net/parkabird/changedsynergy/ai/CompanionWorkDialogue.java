package net.parkabird.changedsynergy.ai;

import java.util.Map;
import java.util.WeakHashMap;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;

/**
 * Adds stage-based speech to both work backends. Native Changed pets and
 * Synergy-bonded creatures deliberately share the bonded-companion wording;
 * their distinct relationship data and acquisition paths remain untouched.
 */
public final class CompanionWorkDialogue {
    private static final String NEXT_START =
            "ChangedSynergyNextCompanionWorkStart";
    private static final String NEXT_RESULT =
            "ChangedSynergyNextCompanionWorkResult";
    private static final long START_COOLDOWN = 500L;
    private static final long RESULT_COOLDOWN = 360L;
    private static final Map<ChangedEntity, ObservedWork> OBSERVED =
            new WeakHashMap<>();

    public enum WorkKind {
        FISHING,
        MINING
    }

    private record ObservedWork(
            WorkKind kind,
            long startedTick) {
    }

    private CompanionWorkDialogue() {
    }

    /** Watches Changed/Addon native utility goals that Synergy does not own. */
    public static void tick(ChangedEntity creature, @Nullable ServerPlayer owner) {
        if (!(creature.level() instanceof ServerLevel level) || owner == null) {
            OBSERVED.remove(creature);
            return;
        }
        WorkKind current = runningNativeWork(creature);
        ObservedWork previous = OBSERVED.get(creature);
        if (previous != null && previous.kind() != current) {
            long elapsed = level.getGameTime() - previous.startedTick();
            if (elapsed >= minimumSuccessTicks(previous.kind())) {
                announceSuccess(creature, owner, previous.kind());
            }
            OBSERVED.remove(creature);
            previous = null;
        }
        if (current != null && previous == null) {
            OBSERVED.put(creature, new ObservedWork(
                    current, level.getGameTime()));
            announceStart(creature, owner, current);
        }
    }

    /** Called by Synergy's complete fallback worker at the real start point. */
    public static void announceStart(
            ChangedEntity creature,
            ServerPlayer owner,
            WorkKind kind) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (creature.getPersistentData().getLong(NEXT_START) > now) {
            return;
        }
        NpcDialogue.trigger(creature, owner, cue(kind, false));
        creature.getPersistentData().putLong(
                NEXT_START,
                now + START_COOLDOWN + creature.getRandom().nextInt(201));
    }

    /** Called only after real loot, ore or a useful cave light was produced. */
    public static void announceSuccess(
            ChangedEntity creature,
            ServerPlayer owner,
            WorkKind kind) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        long now = level.getGameTime();
        if (creature.getPersistentData().getLong(NEXT_RESULT) > now) {
            return;
        }
        NpcDialogue.trigger(creature, owner, cue(kind, true));
        creature.getPersistentData().putLong(
                NEXT_RESULT,
                now + RESULT_COOLDOWN + creature.getRandom().nextInt(161));
    }

    @Nullable
    private static WorkKind runningNativeWork(ChangedEntity creature) {
        for (WrappedGoal wrapped : creature.goalSelector
                .getAvailableGoals()) {
            if (!wrapped.isRunning()) {
                continue;
            }
            String name = wrapped.getGoal().getClass().getSimpleName();
            // FallbackBondedWorkGoal reports exact outcomes directly below.
            if ("FallbackBondedWorkGoal".equals(name)) {
                continue;
            }
            if (name.contains("FishingGoal")) {
                return WorkKind.FISHING;
            }
            if (name.contains("CaveHarvestGoal")
                    || name.contains("CaveTorchingGoal")
                    || name.contains("MiningGoal")) {
                return WorkKind.MINING;
            }
        }
        return null;
    }

    private static long minimumSuccessTicks(WorkKind kind) {
        return kind == WorkKind.FISHING ? 100L : 10L;
    }

    private static Cue cue(
            WorkKind kind,
            boolean success) {
        if (kind == WorkKind.FISHING) {
            return success
                    ? Cue.BOND_FISHING_SUCCESS
                    : Cue.BOND_FISHING_START;
        }
        return success
                ? Cue.BOND_MINING_SUCCESS
                : Cue.BOND_MINING_START;
    }
}
