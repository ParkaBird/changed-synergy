import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import net.parkabird.changedsynergy.ai.FactionDiplomacy;

/** Run directly with Java 17; no game boot or external test framework required. */
public class FactionDiplomacyTest {
    private static int checks;
    private static void check(boolean value, String reason) {
        checks++;
        if (!value) throw new AssertionError(reason);
    }
    public static void main(String[] args) {
        for (var pair : FactionDiplomacy.RIVALRIES) {
            Map<String, Integer> scores = new HashMap<>();
            scores.put(pair.first(), 70); scores.put(pair.second(), 100);
            FactionDiplomacy.applyGain(scores, pair.first());
            check(scores.get(pair.second()) == 69, "new choice wins");
            scores.put(pair.first(), 100);
            FactionDiplomacy.applyGain(scores, pair.first());
            check(scores.get(pair.second()) == 54, "alliance growth pressure");
            var snapshot = new HashMap<>(scores);
            for (int i = 0; i < 100; i++) FactionDiplomacy.applyGain(scores, pair.first());
            check(scores.equals(snapshot), "capped gifts cannot repeatedly subtract");
            scores.put(pair.second(), 70);
            FactionDiplomacy.applyGain(scores, pair.second());
            check(scores.get(pair.first()) == 69 && scores.get(pair.second()) == 70, "switch alliance");
            scores.put(pair.second(), -100); scores.put(pair.first(), 100);
            FactionDiplomacy.applyGain(scores, pair.first());
            check(scores.get(pair.second()) == -100, "no free repair of hostility");
            scores.put(pair.first(), 100); scores.put(pair.second(), 100);
            FactionDiplomacy.normalize(scores);
            check(scores.get(pair.first()) == 100 && scores.get(pair.second()) == 54, "stable migration tie");
            snapshot = new HashMap<>(scores);
            FactionDiplomacy.normalize(scores);
            check(scores.equals(snapshot), "migration idempotent");
            scores.put(pair.first(), 69); scores.put(pair.second(), 100);
            FactionDiplomacy.applyGain(scores, pair.first());
            check(scores.get(pair.second()) == 100, "friendship does not penalize rivals");
        }
        Map<String, Integer> all = new HashMap<>();
        all.put("faction:white", 40); all.put("faction:dark", 40); all.put("faction:aquatic", 40);
        for (String region : FactionDiplomacy.REGIONS) all.put("faction:light:" + region, 40);
        check(FactionDiplomacy.allRespected(all), "universal friendship possible without alliances");
        for (String key : java.util.List.copyOf(all.keySet())) {
            all.put(key, 39); check(!FactionDiplomacy.allRespected(all), "every account required: " + key);
            all.put(key, 40);
        }
        check(!FactionDiplomacy.allRespected(Map.of()), "missing regions not friendly");
        Random random = new Random(7421);
        var keys = java.util.List.copyOf(all.keySet());
        for (int i = 0; i < 20000; i++) {
            String key = keys.get(random.nextInt(keys.size()));
            int amount = random.nextInt(21) - 5;
            all.put(key, Math.max(-100, Math.min(100, all.get(key) + amount)));
            if (amount > 0) FactionDiplomacy.applyGain(all, key);
            for (var pair : FactionDiplomacy.RIVALRIES) {
                check(all.get(pair.first()) < 70 || all.get(pair.second()) < 70, "exclusive alliances");
            }
        }
        System.out.println("Faction diplomacy: " + checks + " checks passed.");
    }
}
