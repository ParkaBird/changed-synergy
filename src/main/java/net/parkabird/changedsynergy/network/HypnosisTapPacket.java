package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.HypnosisQteService;

/** Only an input edge; the server owns participants, timing and progress. */
public record HypnosisTapPacket(int sessionId, boolean pressed) {
    public static void encode(HypnosisTapPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.sessionId);
        buffer.writeBoolean(packet.pressed);
    }
    public static HypnosisTapPacket decode(FriendlyByteBuf buffer) {
        return new HypnosisTapPacket(buffer.readVarInt(), buffer.readBoolean());
    }
    public static void handle(HypnosisTapPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) {
                HypnosisQteService.playerTap(context.getSender(), packet.sessionId(), packet.pressed());
            }
        });
        context.setPacketHandled(true);
    }
}
