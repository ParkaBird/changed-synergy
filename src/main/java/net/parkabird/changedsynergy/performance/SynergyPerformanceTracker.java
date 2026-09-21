package net.parkabird.changedsynergy.performance;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PerformanceSnapshotPacket;

/**
 * Measures complete Changed-entity AI and schedules optional Synergy work.
 * All mutable counters belong to the server thread.
 */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SynergyPerformanceTracker {
    public enum Feature {
        COMMUNITY,
        COMPANION_WORK,
        SOCIAL,
        HUNT
    }

    private static final int SAMPLE_INTERVAL_TICKS = 20;
    private static final int OVER_BUDGET_ROTATION = 8;
    private static long tickStartedNanos;
    private static long livingHookStartedNanos;
    private static long currentLatexAiNanos;
    private static int currentLatexCount;
    private static int currentOptionalRuns;
    private static int currentDeferredRuns;
    private static boolean currentBudgetLimited;
    private static long tickSerial;
    private static double averageServerMspt;
    private static double averageLatexAiMspt;
    private static double averageLatexCount;
    private static double averageOptionalRuns;
    private static double averageDeferredRuns;
    private static boolean budgetLimitedSinceSample;

    private SynergyPerformanceTracker() {
    }

    /** Called from the Changed-entity AI timing mixin. Zero disables the timer. */
    public static long beginLatexAi() {
        currentLatexCount++;
        return shouldMeasure() ? System.nanoTime() : 0L;
    }

    public static void endLatexAi(long startedNanos) {
        if (startedNanos != 0L) {
            currentLatexAiNanos += Math.max(0L, System.nanoTime() - startedNanos);
        }
    }

    public static boolean shouldMeasure() {
        return ChangedSynergyConfig.COMMON.performanceDiagnostics.get()
                || ChangedSynergyConfig.COMMON.adaptiveAiBudget.get();
    }

    /** Includes Forge living-tick hooks, where several Synergy AI services run. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLatexLivingTickStart(LivingEvent.LivingTickEvent event) {
        if (event.getEntity() instanceof ChangedEntity creature
                && !creature.level().isClientSide
                && shouldMeasure()) {
            livingHookStartedNanos = System.nanoTime();
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onLatexLivingTickEnd(LivingEvent.LivingTickEvent event) {
        if (livingHookStartedNanos == 0L
                || !(event.getEntity() instanceof ChangedEntity creature)
                || creature.level().isClientSide) {
            return;
        }
        currentLatexAiNanos += Math.max(
                0L, System.nanoTime() - livingHookStartedNanos);
        livingHookStartedNanos = 0L;
    }

    /**
     * Admits one optional decision pass. Expensive searches are phase-staggered,
     * distant idle creatures are slowed further, and budget deferrals rotate so
     * the same entities are not starved by stable entity tick order.
     */
    public static boolean allowBackground(
            ChangedEntity creature,
            Feature feature,
            int baseInterval) {
        if (!(creature.level() instanceof ServerLevel)
                || !featureEnabled(feature)) {
            return false;
        }
        int interval = Math.max(1, baseInterval);
        if (!isScheduled(creature, interval)) {
            return false;
        }
        boolean critical = isCritical(creature, feature);
        if (ChangedSynergyConfig.COMMON.distantAiThrottling.get()
                && !critical
                && !hasNearbyPlayer(creature)) {
            interval = Math.min(480, interval * ChangedSynergyConfig.COMMON
                    .distantAiIntervalMultiplier.get());
            if (!isScheduled(creature, interval)) {
                return false;
            }
        }
        if (ChangedSynergyConfig.COMMON.adaptiveAiBudget.get()
                && !critical
                && currentLatexAiNanos >= budgetNanos()
                && Math.floorMod((long)creature.getId() + tickSerial,
                        OVER_BUDGET_ROTATION) != 0L) {
            currentDeferredRuns++;
            currentBudgetLimited = true;
            return false;
        }
        currentOptionalRuns++;
        return true;
    }

    public static int configuredBackgroundInterval() {
        return ChangedSynergyConfig.COMMON.backgroundScanInterval.get();
    }

    public static boolean featureEnabled(Feature feature) {
        return switch (feature) {
            case COMMUNITY -> ChangedSynergyConfig.COMMON.communityAi.get();
            case COMPANION_WORK -> ChangedSynergyConfig.COMMON.companionWorkAi.get();
            case SOCIAL -> ChangedSynergyConfig.COMMON.ambientSocialAi.get();
            case HUNT -> ChangedSynergyConfig.COMMON.enhancedHuntAi.get();
        };
    }

    private static boolean hasNearbyPlayer(ChangedEntity creature) {
        double range = ChangedSynergyConfig.COMMON.distantAiRange.get();
        return creature.level().getNearestPlayer(creature, range) != null;
    }

    private static boolean isScheduled(ChangedEntity creature, int interval) {
        int phase = ChangedSynergyConfig.COMMON.staggerBackgroundAi.get()
                ? Math.floorMod(creature.getId(), interval) : 0;
        return Math.floorMod(creature.tickCount, interval) == phase;
    }

    private static boolean isCritical(
            ChangedEntity creature,
            Feature feature) {
        if (creature.getTarget() != null) {
            return true;
        }
        // Work searches are optional even for a bonded pet. Other categories
        // may contain owner-safety decisions and therefore keep bonded
        // creatures in the foreground.
        return feature != Feature.COMPANION_WORK
                && (LatexSocialMemory.petOwnerUuid(creature).isPresent()
                        || LatexSocialMemory.hasActiveBond(creature));
    }

    private static long budgetNanos() {
        return Math.round(ChangedSynergyConfig.COMMON.latexAiBudgetMs.get()
                * 1_000_000.0D);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onServerTickStart(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.START) {
            return;
        }
        tickStartedNanos = System.nanoTime();
        currentLatexAiNanos = 0L;
        currentLatexCount = 0;
        currentOptionalRuns = 0;
        currentDeferredRuns = 0;
        currentBudgetLimited = false;
        livingHookStartedNanos = 0L;
        tickSerial++;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerTickEnd(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || tickStartedNanos == 0L) {
            return;
        }
        double serverMs = Math.max(0L, System.nanoTime() - tickStartedNanos)
                / 1_000_000.0D;
        double latexMs = currentLatexAiNanos / 1_000_000.0D;
        averageServerMspt = smooth(averageServerMspt, serverMs);
        averageLatexAiMspt = smooth(averageLatexAiMspt, latexMs);
        averageLatexCount = smooth(averageLatexCount, currentLatexCount);
        averageOptionalRuns = smooth(averageOptionalRuns, currentOptionalRuns);
        averageDeferredRuns = smooth(averageDeferredRuns, currentDeferredRuns);
        budgetLimitedSinceSample |= currentBudgetLimited;
        MinecraftServer server = event.getServer();
        if (ChangedSynergyConfig.COMMON.performanceDiagnostics.get()
                && server.getTickCount() % SAMPLE_INTERVAL_TICKS == 0) {
            sendSnapshot(server);
            budgetLimitedSinceSample = false;
        }
    }

    private static double smooth(double previous, double sample) {
        return previous == 0.0D ? sample : previous * 0.9D + sample * 0.1D;
    }

    private static void sendSnapshot(MinecraftServer server) {
        PerformanceSnapshotPacket packet = new PerformanceSnapshotPacket(
                (float)averageServerMspt,
                (float)averageLatexAiMspt,
                Math.max(0, (int)Math.round(averageLatexCount)),
                Math.max(0, (int)Math.round(averageOptionalRuns)),
                Math.max(0, (int)Math.round(averageDeferredRuns)),
                budgetLimitedSinceSample);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ChangedSynergyNetwork.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }
}
