package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.parkabird.changedsynergy.ai.HypnosisQteService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

public record HypnosisQteInputPacket(int sessionId, int pressedKey) {
    public static void encode(HypnosisQteInputPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.sessionId);
        buffer.writeByte(packet.pressedKey);
    }

    public static HypnosisQteInputPacket decode(FriendlyByteBuf buffer) {
        return new HypnosisQteInputPacket(buffer.readVarInt(), buffer.readByte());
    }

    public static void handle(
            HypnosisQteInputPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> HypnosisQteService.handleInput(
                    sender, packet.sessionId, packet.pressedKey));
        }
        context.setPacketHandled(true);
    }
}
