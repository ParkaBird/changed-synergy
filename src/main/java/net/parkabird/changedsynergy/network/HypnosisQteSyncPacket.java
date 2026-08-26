package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record HypnosisQteSyncPacket(
        int sessionId,
        int hypnotistId,
        int expectedKey,
        int lastKey,
        int ticksUnpressed,
        float controlStrength,
        int ticksRemaining,
        int state) {
    public static final int ACTIVE = 0;
    public static final int SUCCESS = 1;
    public static final int FAILED = 2;
    public static final int INTERRUPTED = 3;
    public static final int CLEAR = 4;

    public static void encode(HypnosisQteSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.sessionId);
        buffer.writeVarInt(packet.hypnotistId);
        buffer.writeByte(packet.expectedKey);
        buffer.writeByte(packet.lastKey);
        buffer.writeVarInt(packet.ticksUnpressed);
        buffer.writeFloat(packet.controlStrength);
        buffer.writeVarInt(packet.ticksRemaining);
        buffer.writeByte(packet.state);
    }

    public static HypnosisQteSyncPacket decode(FriendlyByteBuf buffer) {
        return new HypnosisQteSyncPacket(
                buffer.readVarInt(), buffer.readVarInt(), buffer.readByte(),
                buffer.readByte(), buffer.readVarInt(), buffer.readFloat(),
                buffer.readVarInt(), buffer.readByte());
    }

    public static void handle(
            HypnosisQteSyncPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> HypnosisQteClientState.receive(packet)));
        context.setPacketHandled(true);
    }
}
