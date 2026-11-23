package net.parkabird.changedsynergy.network;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

class PlayerOutpostBuildActionPacketTest {
    @Test
    void decodeAcceptsFinitePlaceLocation() {
        PlayerOutpostBuildActionPacket packet = new PlayerOutpostBuildActionPacket(
                PlayerOutpostBuildActionPacket.PLACE,
                new BlockPos(12, 64, -8),
                Direction.UP,
                new Vec3(12.5D, 65.0D, -7.5D));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PlayerOutpostBuildActionPacket.encode(packet, buffer);

        PlayerOutpostBuildActionPacket decoded = PlayerOutpostBuildActionPacket.decode(buffer);
        assertEquals(PlayerOutpostBuildActionPacket.PLACE, decoded.action());
        assertEquals(new BlockPos(12, 64, -8), decoded.pos());
        assertEquals(Direction.UP, decoded.face());
        assertEquals(new Vec3(12.5D, 65.0D, -7.5D), decoded.location());
    }

    @Test
    void decodeRejectsNonFinitePlaceCoordinates() {
        PlayerOutpostBuildActionPacket invalid = new PlayerOutpostBuildActionPacket(
                PlayerOutpostBuildActionPacket.PLACE,
                new BlockPos(1, 2, 3),
                Direction.NORTH,
                new Vec3(Double.NaN, Double.POSITIVE_INFINITY, 0.0D));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        PlayerOutpostBuildActionPacket.encode(invalid, buffer);

        PlayerOutpostBuildActionPacket decoded = PlayerOutpostBuildActionPacket.decode(buffer);
        assertEquals(-1, decoded.action());
        assertEquals(Vec3.ZERO, decoded.location());
    }
}
