package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.parkabird.changedsynergy.client.LegacyTransfurVisualState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record TransfurVisualPacket(int entityId, int color, boolean organic) {
    public static final int CLEAR = -1;

    public static void encode(TransfurVisualPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
        buffer.writeInt(packet.color);
        buffer.writeBoolean(packet.organic);
    }

    public static TransfurVisualPacket decode(FriendlyByteBuf buffer) {
        return new TransfurVisualPacket(
                buffer.readVarInt(), buffer.readInt(), buffer.readBoolean());
    }

    public static void handle(TransfurVisualPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> LegacyTransfurVisualState.receive(
                        packet.entityId, packet.color, packet.organic)));
        context.setPacketHandled(true);
    }
}
