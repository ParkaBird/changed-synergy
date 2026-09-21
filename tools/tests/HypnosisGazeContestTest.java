import net.parkabird.changedsynergy.ai.HypnosisGazeContest;

/** Java 17 standalone checks for hypnosis gaze-contest balance and boundaries. */
public final class HypnosisGazeContestTest {
    private static int checks;

    private static void check(boolean value, String message) {
        checks++;
        if (!value) {
            throw new AssertionError(message);
        }
    }

    public static void main(String[] args) {
        check(HypnosisGazeContest.alignmentFromDot(1.0D) == 1.0F,
                "direct eye contact is full alignment");
        check(HypnosisGazeContest.alignmentFromDot(-1.0D) == 0.0F,
                "looking behind is no alignment");
        check(HypnosisGazeContest.alignmentFromDot(0.94D)
                        > HypnosisGazeContest.alignmentFromDot(0.75D),
                "alignment falls continuously with angle");

        float resistance = 0.0F;
        for (int tick = 0; tick < 80; tick++) {
            resistance = HypnosisGazeContest.nextResistance(resistance, 0.0F);
        }
        check(resistance == 1.0F, "sustained deliberate look-away escapes");

        resistance = 0.45F;
        for (int tick = 0; tick < 20; tick++) {
            resistance = HypnosisGazeContest.nextResistance(resistance, 0.8F);
        }
        check(Math.abs(resistance - 0.45F) < 0.0001F,
                "middle dead band filters small gaze jitter");

        float recaptured = resistance;
        for (int tick = 0; tick < 20; tick++) {
            recaptured = HypnosisGazeContest.nextResistance(recaptured, 1.0F);
        }
        check(recaptured < resistance && recaptured > 0.0F,
                "direct eye contact slowly erodes rather than resets progress");

        check(HypnosisGazeContest.pullStrength(1.0F, 1.0F)
                        > HypnosisGazeContest.pullStrength(0.0F, 0.0F),
                "camera pull is strongest under direct late-stage focus");
        check(HypnosisGazeContest.pullStrength(0.0F, 0.0F) > 0.0D,
                "camera pull never disappears while looking away");

        System.out.println("HypnosisGazeContestTest: " + checks + " checks passed");
    }
}
