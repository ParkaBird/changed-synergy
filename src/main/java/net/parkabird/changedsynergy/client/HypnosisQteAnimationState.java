package net.parkabird.changedsynergy.client;

import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;

/** Local-only animation clocks derived from server-authoritative QTE packets. */
public final class HypnosisQteAnimationState {
    private static final long SESSION_ENTRY_MILLIS = 360L;
    private static final long KEY_ENTRY_MILLIS = 190L;
    private static final long RESULT_MILLIS = 440L;

    private static int sessionId = -1;
    private static int expectedKey = -1;
    private static long sessionStartedAt;
    private static long keyStartedAt;
    private static long resultStartedAt;

    private HypnosisQteAnimationState() {
    }

    public static void receive(
            HypnosisQteSyncPacket previous,
            HypnosisQteSyncPacket packet) {
        long now = QteAnimationUtil.now();
        if (packet.state() == HypnosisQteSyncPacket.CLEAR) {
            clear();
            return;
        }
        if (packet.state() == HypnosisQteSyncPacket.ACTIVE) {
            boolean newSession = previous == null
                    || previous.state() != HypnosisQteSyncPacket.ACTIVE
                    || sessionId != packet.sessionId();
            if (newSession) {
                sessionId = packet.sessionId();
                expectedKey = packet.expectedKey();
                sessionStartedAt = now;
                keyStartedAt = now;
                resultStartedAt = 0L;
            } else if (expectedKey != packet.expectedKey()) {
                expectedKey = packet.expectedKey();
                keyStartedAt = now;
            }
            return;
        }
        if (previous == null
                || previous.state() == HypnosisQteSyncPacket.ACTIVE
                || previous.state() != packet.state()) {
            resultStartedAt = now;
        }
    }

    public static float entryProgress() {
        if (!QteAnimationUtil.enabled()) {
            return 1.0F;
        }
        return QteAnimationUtil.smooth(
                QteAnimationUtil.progress(
                        sessionStartedAt, SESSION_ENTRY_MILLIS));
    }

    public static float keyProgress() {
        if (!QteAnimationUtil.enabled()) {
            return 1.0F;
        }
        return QteAnimationUtil.easeOutBack(
                QteAnimationUtil.progress(
                        keyStartedAt, KEY_ENTRY_MILLIS));
    }

    public static float resultProgress() {
        if (!QteAnimationUtil.enabled()) {
            return 1.0F;
        }
        return QteAnimationUtil.smooth(
                QteAnimationUtil.progress(
                        resultStartedAt, RESULT_MILLIS));
    }

    public static void clear() {
        sessionId = -1;
        expectedKey = -1;
        sessionStartedAt = 0L;
        keyStartedAt = 0L;
        resultStartedAt = 0L;
    }
}
