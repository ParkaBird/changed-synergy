package net.parkabird.changedsynergy.ai;

/** Pure state math for the hypnosis gaze-resistance contest. */
public final class HypnosisGazeContest {
    public static final int DURATION_TICKS = 160;
    private static final double FULL_FOCUS_DEGREES = 5.0D;
    private static final double NO_FOCUS_DEGREES = 55.0D;
    private static final float RESISTANCE_START_ALIGNMENT = 0.72F;
    private static final float RESISTANCE_DECAY_ALIGNMENT = 0.90F;

    private HypnosisGazeContest() {
    }

    /** 1 means direct eye contact; 0 means the gaze is well clear of the hypnotist. */
    public static float alignmentFromDot(double dot) {
        double clamped = Math.max(-1.0D, Math.min(1.0D, dot));
        double degrees = Math.toDegrees(Math.acos(clamped));
        double linear = 1.0D - (degrees - FULL_FOCUS_DEGREES)
                / (NO_FOCUS_DEGREES - FULL_FOCUS_DEGREES);
        float value = (float)Math.max(0.0D, Math.min(1.0D, linear));
        return value * value * (3.0F - 2.0F * value);
    }

    /**
     * Sustained, deliberate look-away movement builds resistance. Direct eye
     * contact slowly erodes it; the middle band is stable to filter mouse and
     * network jitter.
     */
    public static float nextResistance(float current, float alignment) {
        float clampedCurrent = clamp01(current);
        float clampedAlignment = clamp01(alignment);
        if (clampedAlignment < RESISTANCE_START_ALIGNMENT) {
            float escapeEffort = (RESISTANCE_START_ALIGNMENT - clampedAlignment)
                    / RESISTANCE_START_ALIGNMENT;
            return clamp01(clampedCurrent + 0.0065F + 0.0125F * escapeEffort);
        }
        if (clampedAlignment > RESISTANCE_DECAY_ALIGNMENT) {
            float recapture = (clampedAlignment - RESISTANCE_DECAY_ALIGNMENT)
                    / (1.0F - RESISTANCE_DECAY_ALIGNMENT);
            return clamp01(clampedCurrent - (0.0015F + 0.0020F * recapture));
        }
        return clampedCurrent;
    }

    /** Uses Changed's own camera-tug strength scale. */
    public static double pullStrength(float alignment, float elapsedProgress) {
        return 0.045D + 0.055D * clamp01(alignment)
                + 0.025D * clamp01(elapsedProgress);
    }

    public static boolean isActivelyLookingAway(float alignment) {
        return alignment < RESISTANCE_START_ALIGNMENT;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }
}
