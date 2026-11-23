package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class NaturalCampRoutineTest {
    @Test
    void extendedRangeScalesLargeStructuresAndOutsideStragglersUpToCap() {
        // Small camps still get an 80-block floor over the vanilla 40-block follow range.
        assertEquals(80, NaturalCampService.computeExtendedRange(40, 52.0D, 0.0D));
        // Multi-floor spans grow proportionally and cap at 128 blocks.
        assertEquals(105, NaturalCampService.computeExtendedRange(40, 105.0D, 0.0D));
        assertEquals(128, NaturalCampService.computeExtendedRange(40, 145.0D, 0.0D));
        assertEquals(128, NaturalCampService.computeExtendedRange(40, 70.0D, 90.0D));
        assertFalse(NaturalCampService.MEAL_CALL_UNTIL.isEmpty());
        assertFalse(NaturalCampService.MEAL_CALL_LEADER.isEmpty());
    }
}
