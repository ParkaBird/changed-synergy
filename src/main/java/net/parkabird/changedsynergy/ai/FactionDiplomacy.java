package net.parkabird.changedsynergy.ai;

import java.util.List;
import java.util.Map;

/** Pure reputation policy. No entity, world or recursive reputation mutations. */
public final class FactionDiplomacy {
    public static final int ALLIED = 70;
    public static final int RESPECTED = 40;
    public static final List<String> REGIONS = List.of("cave", "taiga", "swamp", "jungle", "savanna",
            "arid", "snowy", "mountain", "beach", "river", "ocean", "forest", "plains");
    public record Rivalry(String first, String second) {}
    public static final List<Rivalry> RIVALRIES = List.of(
            new Rivalry("faction:dark", "faction:white"),
            new Rivalry("faction:light:mountain", "faction:light:cave"),
            new Rivalry("faction:aquatic", "faction:light:arid"),
            new Rivalry("faction:light:forest", "faction:light:arid"),
            new Rivalry("faction:light:taiga", "faction:light:jungle"),
            new Rivalry("faction:light:swamp", "faction:light:savanna"),
            new Rivalry("faction:light:plains", "faction:light:snowy"));
    private FactionDiplomacy() {}

    public static String canonicalRegion(String region) {
        return "desert".equals(region) || "badlands".equals(region) ? "arid" : region;
    }

    public static String canonicalAccount(String account) {
        return switch (account) {
            case "light:desert", "light:badlands" -> "light:arid";
            case "faction:light:desert", "faction:light:badlands" -> "faction:light:arid";
            default -> account;
        };
    }

    public static int mergeAridStanding(int first, int second) {
        return first < 0 || second < 0 ? Math.min(first, second) : Math.max(first, second);
    }

    /** Old conflicting alliances keep the higher score; listed-first wins ties deterministically. */
    public static void normalize(Map<String, Integer> scores) {
        // Resolve surviving alliances by strength; an alliance removed here cannot penalize a third party.
        var accounts = new java.util.LinkedHashSet<String>();
        RIVALRIES.forEach(pair -> accounts.add(pair.first()));
        RIVALRIES.forEach(pair -> accounts.add(pair.second()));
        for (String account : accounts.stream().sorted(java.util.Comparator.comparingInt(
                (String key) -> scores.getOrDefault(key, 0)).reversed()).toList()) {
            if (scores.getOrDefault(account, 0) < ALLIED) continue;
            for (Rivalry pair : RIVALRIES) {
                String rival = pair.first().equals(account) ? pair.second()
                        : pair.second().equals(account) ? pair.first() : null;
                if (rival != null && scores.getOrDefault(rival, 0) >= ALLIED)
                    limit(scores, rival, scores.get(account));
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
