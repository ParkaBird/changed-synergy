package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import net.parkabird.changedsynergy.ai.CreaturePersonality.Trait;
import org.junit.jupiter.api.Test;

class CreaturePersonalityPresetTest {
    @Test
    void npcPresetAlwaysSelectsDistinctSecondaryTrait() {
        for (Trait dominant : Trait.values()) {
            Trait secondary = CreaturePersonality.secondaryTraitForPreset(dominant);
            assertNotNull(secondary);
            assertNotEquals(dominant, secondary);
        }
        assertEquals(Trait.CALM, CreaturePersonality.secondaryTraitForPreset(Trait.PLAYFUL));
        assertEquals(Trait.CALM, CreaturePersonality.secondaryTraitForPreset(Trait.POLITE));
    }
}
