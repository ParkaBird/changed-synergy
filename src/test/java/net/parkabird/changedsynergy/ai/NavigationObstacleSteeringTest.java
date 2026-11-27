package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class NavigationObstacleSteeringTest {
    @Test
    void lateralDirectionOrderPrefersTurnDirectionThenMemoryThenKeepRight() {
        // Turning left (-Z -> -X? No, walking +Z: right perp is -X, ahead +X is left side = -1).
        assertArrayEquals(new int[]{-1, 1}, NavigationObstacleSteering.lateralDirectionOrder(0.0D, 1.0D, 1.0D, 0.0D, 0));
        // Walking +Z, ahead -X aligns with right perp (-1, 0) -> side +1 first.
        assertArrayEquals(new int[]{1, -1}, NavigationObstacleSteering.lateralDirectionOrder(0.0D, 1.0D, -1.0D, 0.0D, 0));
        // Straight ahead defaults to keep-right (+1), unless recent memory overrides.
        assertArrayEquals(new int[]{1, -1}, NavigationObstacleSteering.lateralDirectionOrder(0.0D, 1.0D, 0.0D, 1.0D, 0));
        assertArrayEquals(new int[]{-1, 1}, NavigationObstacleSteering.lateralDirectionOrder(0.0D, 1.0D, -1.0D, 0.0D, -1));
    }

    @Test
    void computeHoldTargetMovesSidewaysUntilForwardClearsThenHoldsClearedLane() {
        double[] phase1 = NavigationObstacleSteering.computeHoldTarget(0.48D, 9.64D, 0.60D, 9.64D, 0.0D, 1.0D, false);
        assertEquals(0.60D, phase1[0], 1.0E-6D);
        assertEquals(9.74D, phase1[1], 1.0E-6D);

        // Once forward is clear at x=0.59, keep lane x=0.60 while stepping cleanly ahead (+0.75 Z).
        double[] phase2 = NavigationObstacleSteering.computeHoldTarget(0.59D, 9.65D, 0.60D, 9.64D, 0.0D, 1.0D, true);
        assertEquals(0.60D, phase2[0], 1.0E-6D);
        assertEquals(10.40D, phase2[1], 1.0E-6D);
    }
}
