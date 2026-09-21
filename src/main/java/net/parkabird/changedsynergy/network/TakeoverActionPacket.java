package net.parkabird.changedsynergy.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.TakeoverService;

public record TakeoverActionPacket(UUID sessionId, int action, int key, int sequence) {
    public static final int REQUEST_CONTROL = 0, START_STRUGGLE = 1, QTE_INPUT = 2,
            RETURN_CONTROL = 3, CONFIRM_STRUGGLE = 4;

    public static void encode(TakeoverActionPacket p, FriendlyByteBuf b) {
        b.writeUUID(p.sessionId);
        b.writeVarInt(p.action);
        b.writeVarInt(p.key);
        b.writeVarInt(p.sequence);
    }

    public static TakeoverActionPacket decode(FriendlyByteBuf b) {
        return new TakeoverActionPacket(b.readUUID(), b.readVarInt(), b.readVarInt(), b.readVarInt());
    }

    public static void handle(TakeoverActionPacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        var sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> TakeoverService.handleAction(
                    sender, p.sessionId, p.action, p.key, p.sequence));
        }
        context.setPacketHandled(true);
    }
}
