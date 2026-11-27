package net.parkabird.changedsynergy.npc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ShoalAlignmentTest {
    private static JsonObject loadJson(String resourcePath) {
        try (var stream = ShoalAlignmentTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing resource: " + resourcePath);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new RuntimeException(resourcePath, ex);
        }
    }

    @Test
    void shoalChiefAndCampVoicesCoverAllNodesAndTranslations() {
        var chief = loadJson("/data/changed_synergy/story_npcs/shoal_chief.json");
        assertEquals("the_shoal", chief.get("template").getAsString());
        assertEquals("changed_synergy:latex_swordfish", chief.get("species").getAsString());
        assertTrue(chief.get("chief").getAsBoolean());

        var nodes = chief.getAsJsonObject("nodes");
        for (String role : List.of("fishing", "dorm", "storage", "workshop")) {
            assertTrue(nodes.has("resident_" + role), "Missing resident_" + role);
            assertTrue(nodes.has("resident_" + role + "_0"), "Missing resident_" + role + "_0");
        }
        assertTrue(nodes.has("clue_disc"));
        assertTrue(nodes.has("clue_manifest"));

        var requiredKeys = new HashSet<String>();
        requiredKeys.add("npc.changed_synergy.shoal_chief.title");
        requiredKeys.add("npc.changed_synergy.shoal_chief.name");
        requiredKeys.add("camp.changed_synergy.home.shoal");
        for (var entry : nodes.entrySet()) {
            var node = entry.getValue().getAsJsonObject();
            for (JsonElement lineEl : node.getAsJsonArray("lines")) {
                for (JsonElement partEl : lineEl.getAsJsonObject().getAsJsonArray("parts")) {
                    requiredKeys.add(partEl.getAsJsonObject().get("text").getAsString());
                }
            }
            for (JsonElement choiceEl : node.getAsJsonArray("choices")) {
                requiredKeys.add(choiceEl.getAsJsonObject().get("text").getAsString());
            }
        }

        var common = loadJson("/data/changed_synergy/camp_dialogue/common.json").getAsJsonObject("lines");
        var stewardTopics = new HashSet<String>();
        var burrowResTopics = new HashSet<String>();
        var scavengerTopics = new HashSet<String>();
        var shoalResTopics = new HashSet<String>();
        for (String key : common.keySet()) {
            if (key.startsWith("steward.")) stewardTopics.add(key.substring("steward.".length()));
            else if (key.startsWith("burrow_resident.")) burrowResTopics.add(key.substring("burrow_resident.".length()));
            else if (key.startsWith("scavenger.")) {
                scavengerTopics.add(key.substring("scavenger.".length()));
                for (JsonElement p : common.getAsJsonArray(key)) {
                    requiredKeys.add(p.getAsJsonObject().get("text").getAsString());
                }
            } else if (key.startsWith("shoal_resident.")) {
                shoalResTopics.add(key.substring("shoal_resident.".length()));
                for (JsonElement p : common.getAsJsonArray(key)) {
                    requiredKeys.add(p.getAsJsonObject().get("text").getAsString());
                }
            }
        }
        assertEquals(stewardTopics, scavengerTopics, "scavenger camp topics must match steward parity");
        assertEquals(burrowResTopics, shoalResTopics, "shoal_resident camp topics must match burrow_resident parity");

        for (String lang : List.of("en_us", "zh_cn", "ru_ru")) {
            var dict = loadJson("/assets/changed_synergy/lang/" + lang + ".json");
            for (String key : requiredKeys) {
                assertTrue(dict.has(key), lang + " missing key: " + key);
                assertFalse(dict.get(key).getAsString().isBlank(), lang + " blank key: " + key);
            }
        }
        var foodTag = loadJson("/data/changed_synergy/tags/items/camp_food.json").getAsJsonArray("values");
        var items = new HashSet<String>();
        foodTag.forEach(e -> items.add(e.getAsString()));
        assertTrue(items.containsAll(Set.of("minecraft:cod", "minecraft:salmon", "minecraft:dried_kelp", "changed:orange")));
    }
}
