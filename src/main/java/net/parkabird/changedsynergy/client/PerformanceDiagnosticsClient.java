package net.parkabird.changedsynergy.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.parkabird.changedsynergy.network.PerformanceSnapshotPacket;

/** Latest server sample formatted for the native config screen. */
public final class PerformanceDiagnosticsClient {
    private static final long STALE_AFTER_MS = 5_000L;
    private static PerformanceSnapshotPacket latest;
    private static long receivedAt;

    private PerformanceDiagnosticsClient() {
    }

    public static void receive(PerformanceSnapshotPacket packet) {
        latest = packet;
        receivedAt = Util.getMillis();
    }

    public static Component primarySummary() {
        int fps = Minecraft.getInstance().getFps();
        PerformanceSnapshotPacket snapshot = latest;
        if (isStale(snapshot)) {
            return Component.translatable(
                    "config.changed_synergy.performance.local_only", fps);
        }
        return Component.translatable(
                "config.changed_synergy.performance.server_summary",
                fps,
                format(snapshot.serverMspt()));
    }

    public static Component secondarySummary() {
        PerformanceSnapshotPacket snapshot = latest;
        if (isStale(snapshot)) {
            return Component.translatable(
                    "config.changed_synergy.performance.waiting");
        }
        float share = snapshot.serverMspt() <= 0.01F
                ? 0.0F : snapshot.latexAiMspt() / snapshot.serverMspt() * 100.0F;
        return Component.translatable(
                "config.changed_synergy.performance.ai_summary",
                format(snapshot.latexAiMspt()),
                format(share));
    }

    public static Component tertiarySummary() {
        PerformanceSnapshotPacket snapshot = latest;
        if (isStale(snapshot)) {
            return Component.empty();
        }
        return Component.translatable(
                "config.changed_synergy.performance.work_summary",
                snapshot.latexCount(),
                snapshot.optionalRuns(),
                snapshot.deferredRuns());
    }

    public static int color() {
        PerformanceSnapshotPacket snapshot = latest;
        if (isStale(snapshot)) {
            return 0xA0A0A0;
        }
        if (snapshot.serverMspt() >= 50.0F || snapshot.budgetLimited()) {
            return 0xFFAA55;
        }
        return snapshot.serverMspt() >= 40.0F ? 0xFFFF55 : 0x55FF55;
    }

    private static String format(float value) {
        return String.format(java.util.Locale.ROOT, "%.1f", value);
    }

    private static boolean isStale(PerformanceSnapshotPacket snapshot) {
        return snapshot == null || Util.getMillis() - receivedAt > STALE_AFTER_MS;
    }
}
