package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.RevivalGrabAbilityClient;

public record RevivalGrabAbilityPacket(int entityId) {
    public static void encode(RevivalGrabAbilityPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
    }

    public static RevivalGrabAbilityPacket decode(FriendlyByteBuf buffer) {
        return new RevivalGrabAbilityPacket(buffer.readVarInt());
    }

    public static void handle(RevivalGrabAbilityPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> RevivalGrabAbilityClient.register(packet.entityId));
        context.setPacketHandled(true);
    }
}
