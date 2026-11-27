package net.parkabird.changedsynergy.world.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

class MenuBufferBoundsTest {
    @Test
    void clampsSyncedListCountsIntoSafeRange() {
        assertEquals(0, PlayerRelationshipMenu.clampSyncedCount(Integer.MIN_VALUE));
        assertEquals(0, PlayerRelationshipMenu.clampSyncedCount(-1));
        assertEquals(0, PlayerRelationshipMenu.clampSyncedCount(0));
        assertEquals(17, PlayerRelationshipMenu.clampSyncedCount(17));
        assertEquals(64, PlayerRelationshipMenu.clampSyncedCount(64));
        assertEquals(64, PlayerRelationshipMenu.clampSyncedCount(65));
        assertEquals(64, PlayerRelationshipMenu.clampSyncedCount(10_000));

        FriendlyByteBuf negative = new FriendlyByteBuf(Unpooled.buffer());
        negative.writeVarInt(-42);
        assertEquals(0, PlayerRelationshipMenu.clampSyncedCount(negative.readVarInt()));

        FriendlyByteBuf oversized = new FriendlyByteBuf(Unpooled.buffer());
        oversized.writeVarInt(999);
        assertEquals(64, PlayerRelationshipMenu.clampSyncedCount(oversized.readVarInt()));
    }
}
