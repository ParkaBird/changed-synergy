package net.parkabird.changedsynergy.client.model;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import org.junit.jupiter.api.Test;

class BurrowShawlLayersTest {
    @Test
    void allCutsSplitStateClosedOntoHeadAndKeepCollarOnTorso() {
        List<Supplier<LayerDefinition>> cuts = List.of(
                BurrowShawlLayers::manulMale,
                BurrowShawlLayers::manulFemale,
                BurrowShawlLayers::grayFoxMale,
                BurrowShawlLayers::grayFoxFemale,
                BurrowShawlLayers::bipedal,
                BurrowShawlLayers::female);

        for (Supplier<LayerDefinition> supplier : cuts) {
            ModelPart root = supplier.get().bakeRoot();
            assertTrue(root.hasChild("Head"));
            assertTrue(root.hasChild("Torso"));

            ModelPart head = root.getChild("Head");
            assertTrue(head.hasChild("State_Closed"));
            assertFalse(head.hasChild("State_Open"));

            ModelPart torso = root.getChild("Torso");
            assertTrue(torso.hasChild("Outfit_Shawl"));
            assertTrue(torso.hasChild("State_Open"));
            assertFalse(torso.hasChild("State_Closed"));
        }
    }
}
