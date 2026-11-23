package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CreatureArmorServiceTest {
    @Test
    void borrowedArmorReducesDamageToCombinedTotalWithoutExtraWhenEmpty() {
        float raw = 10.0F;
        assertEquals(raw, CreatureArmorService.scaleDamageWithBorrowedArmor(raw, 0.0D, 0.0D, 0.0D, 0.0D));

        // Player has boots (2 armor), bonded suit has chestplate (8 armor, 2 toughness).
        float scaled = CreatureArmorService.scaleDamageWithBorrowedArmor(raw, 2.0D, 0.0D, 8.0D, 2.0D);
        assertTrue(scaled < raw && scaled > 0.0F);
    }
}
