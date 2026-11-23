package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Optional;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import org.junit.jupiter.api.Test;

class PlayerOutpostDataTest {
    @Test
    void parseMigratesLegacyCompostToFarmAndDropsDeadConstant() {
        assertEquals(Optional.of(Work.FARM), Work.parse("COMPOST"));
        assertEquals(Optional.of(Work.FARM), Work.parse("compost"));
        assertEquals(Optional.empty(), Work.parse("nonexistent_work"));
        assertTrue(Arrays.stream(Work.values()).noneMatch(work -> work.name().equals("COMPOST")));
        assertFalse(Work.parse("gather").isEmpty());
    }
}
