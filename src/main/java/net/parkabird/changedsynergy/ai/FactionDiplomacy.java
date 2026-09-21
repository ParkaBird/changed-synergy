package net.parkabird.changedsynergy.ai;

import java.util.List;
import java.util.Map;

/** Pure reputation policy. No entity, world or recursive reputation mutations. */
public final class FactionDiplomacy {
    public static final int ALLIED = 70;
    public static final int RESPECTED = 40;
    public static final List<String> REGIONS = List.of("cave", "taiga", "swamp", "jungle", "savanna",
            "desert", "badlands", "snowy", "mountain", "beach", "river", "ocean", "forest", "plains");
    public record Rivalry(String first, String second) {}
    public static final List<Rivalry> RIVALRIES = List.of(
            new Rivalry("faction:dark", "faction:white"),
            new Rivalry("faction:light:mountain", "faction:light:cave"),
            new Rivalry("faction:aquatic", "faction:light:desert"),
            new Rivalry("faction:light:forest", "faction:light:badlands"),
            new Rivalry("faction:light:taiga", "faction:light:jungle"),
            new Rivalry("faction:light:swamp", "faction:light:savanna"),
            new Rivalry("faction:light:plains", "faction:light:snowy"));
    private FactionDiplomacy() {}

    /** Old conflicting alliances keep the higher score; listed-first wins ties deterministically. */
    public static void normalize(Map<String, Integer> scores) {
        for (Rivalry pair : RIVALRIES) {
            int first = scores.getOrDefault(pair.first(), 0);
            int second = scores.getOrDefault(pair.second(), 0);
            if (first >= ALLIED && second >= ALLIED) {
                limit(scores, first >= second ? pair.second() : pair.first(), Math.max(first, second));
            }
        }
    }

    /** A positive gain into alliance is the player's new choice, not a recursive side effect. */
    public static void applyGain(Map<String, Integer> scores, String source) {
        int standing = scores.getOrDefault(source, 0);
        if (standing < ALLIED) return;
        for (Rivalry pair : RIVALRIES) {
            if (pair.first().equals(source)) limit(scores, pair.second(), standing);
            else if (pair.second().equals(source)) limit(scores, pair.first(), standing);
        }
    }

    private static void limit(Map<String, Integer> scores, String rival, int standing) {
        // 70 -> 69; 100 -> 54. Never lower an already worse score, reward hostility,
        // or repeatedly charge gifts made after the source has reached its cap.
        int ceiling = Math.max(RESPECTED, 69 - (Math.min(100, standing) - ALLIED) / 2);
        if (scores.getOrDefault(rival, 0) > ceiling) scores.put(rival, ceiling);
    }

    public static boolean allRespected(Map<String, Integer> scores) {
        if (scores.getOrDefault("faction:white", 0) < RESPECTED
                || scores.getOrDefault("faction:dark", 0) < RESPECTED
                || scores.getOrDefault("faction:aquatic", 0) < RESPECTED) return false;
        return REGIONS.stream().allMatch(region -> scores.getOrDefault("faction:light:" + region, 0) >= RESPECTED);
    }
}
