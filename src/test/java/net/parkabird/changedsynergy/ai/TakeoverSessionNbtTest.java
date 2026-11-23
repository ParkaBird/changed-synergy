package net.parkabird.changedsynergy.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.minecraft.nbt.CompoundTag;
import net.parkabird.changedsynergy.ai.TakeoverSession.Kind;
import net.parkabird.changedsynergy.ai.TakeoverSession.Phase;
import net.parkabird.changedsynergy.ai.TakeoverSession.ReleaseReason;
import org.junit.jupiter.api.Test;

class TakeoverSessionNbtTest {
    @Test
    void roundTripPreservesControlledOrdinarySession() {
        TakeoverSession session = TakeoverSession.ordinary(100L, 600L, true, false);
        CompoundTag tag = TakeoverSessionNbt.write(session);

        TakeoverSession restored = TakeoverSessionNbt.read(tag, 120L);
        assertEquals(Kind.ORDINARY, restored.getKind());
        assertEquals(Phase.CONTROLLED, restored.getPhase());
        assertTrue(restored.isCompensationEligible());
    }

    @Test
    void readInterruptsExoskeletonOnRestartRecovery() {
        TakeoverSession session = TakeoverSession.exoskeleton(50L);
        CompoundTag tag = TakeoverSessionNbt.write(session);

        TakeoverSession restored = TakeoverSessionNbt.read(tag, 80L);
        assertEquals(Kind.EXOSKELETON, restored.getKind());
        assertEquals(Phase.RELEASING, restored.getPhase());
        assertEquals(ReleaseReason.INTERRUPTED, restored.getReleaseReason());
        assertFalse(restored.isCompensationEligible());
    }

    @Test
    void readRejectsCorruptOrOversizedPayloads() {
        TakeoverSession session = TakeoverSession.ordinary(10L, 600L, false, false);
        CompoundTag badVersion = TakeoverSessionNbt.write(session);
        badVersion.putInt("version", 999);
        assertThrows(IllegalArgumentException.class, () -> TakeoverSessionNbt.read(badVersion, 20L));

        CompoundTag oversized = TakeoverSessionNbt.write(session);
        oversized.putIntArray("sequence", new int[51]);
        assertThrows(IllegalArgumentException.class, () -> TakeoverSessionNbt.read(oversized, 20L));
    }
}
