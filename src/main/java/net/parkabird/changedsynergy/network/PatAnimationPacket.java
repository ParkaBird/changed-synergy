package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.PatAnimationClientState;

public record PatAnimationPacket(
        int actorId,
        boolean active,
        int durationTicks,
        float cycleTicks) {
    public static void encode(
            PatAnimationPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.actorId);
        buffer.writeBoolean(packet.active);
        buffer.writeVarInt(packet.durationTicks);
        buffer.writeFloat(packet.cycleTicks);
    }

    public static PatAnimationPacket decode(FriendlyByteBuf buffer) {
        return new PatAnimationPacket(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt(),
                buffer.readFloat());
    }

    public static void handle(
            PatAnimationPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> PatAnimationClientState.update(
                packet.actorId,
                packet.active,
                packet.durationTicks,
                packet.cycleTicks));
        context.setPacketHandled(true);
    }
}
