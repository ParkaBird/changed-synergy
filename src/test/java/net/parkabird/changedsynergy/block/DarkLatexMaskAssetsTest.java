package net.parkabird.changedsynergy.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DarkLatexMaskAssetsTest {
    private static JsonObject loadJson(String resourcePath) {
        try (var stream = DarkLatexMaskAssetsTest.class.getResourceAsStream(resourcePath)) {
            assertNotNull(stream, "Missing resource: " + resourcePath);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception ex) {
            throw new RuntimeException(resourcePath, ex);
        }
    }

    @Test
    void maskBlockModelAndVariantsLayFlatWith16Rotations() {
        JsonObject blockstate = loadJson("/assets/changed_synergy/blockstates/dark_latex_mask.json");
        JsonObject variants = blockstate.getAsJsonObject("variants");
        for (int i = 0; i < 16; i++) {
            JsonObject entry = variants.getAsJsonObject("rotation=" + i);
            assertNotNull(entry, "Missing rotation=" + i);
            assertEquals(270, entry.get("x").getAsInt());
            assertTrue(entry.get("model").getAsString().startsWith("changed_synergy:block/dark_latex_mask"));
        }

        for (String suffix : new String[]{"", "_225", "_45", "_675"}) {
            JsonObject model = loadJson("/assets/changed_synergy/models/block/dark_latex_mask" + suffix + ".json");
            assertEquals(15, model.getAsJsonArray("elements").size());
            assertEquals("changed:item/3d/dark_latex_mask", model.getAsJsonObject("textures").get("0").getAsString());
            double maxZ = Double.NEGATIVE_INFINITY;
            double minZ = Double.POSITIVE_INFINITY;
            for (JsonElement element : model.getAsJsonArray("elements")) {
                var from = element.getAsJsonObject().getAsJsonArray("from");
                var to = element.getAsJsonObject().getAsJsonArray("to");
                minZ = Math.min(minZ, from.get(2).getAsDouble());
                maxZ = Math.max(maxZ, to.get(2).getAsDouble());
            }
            assertTrue(minZ <= 13.0D && maxZ >= 16.0D);
        }

        for (String lang : new String[]{"en_us", "zh_cn", "ru_ru"}) {
            JsonObject translations = loadJson("/assets/changed_synergy/lang/" + lang + ".json");
            assertTrue(translations.has("block.changed_synergy.dark_latex_mask"), "Missing translation in " + lang);
        }
    }
}
