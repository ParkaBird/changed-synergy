package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class HuntClaimKeyTest {
    @Test
    void huntClaimKeysStayOnTheExistingSaveFields() {
        assertEquals("ChangedSynergyHuntClaimedBy", CreatureSettlementService.HUNT_CLAIMED_BY);
        assertEquals("ChangedSynergyHuntClaimedUntil", CreatureSettlementService.HUNT_CLAIMED_UNTIL);
    }
}
