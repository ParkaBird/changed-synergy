package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Tells the grabbed client that this arm hold is a timed social hug, not a QTE. */
public record FriendlySocialHugSyncPacket(
        int grabberId,
        int grabbedId,
        boolean active,
        int durationTicks) {
    public static void encode(
            FriendlySocialHugSyncPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.grabberId);
        buffer.writeVarInt(packet.grabbedId);
        buffer.writeBoolean(packet.active);
        buffer.writeVarInt(packet.durationTicks);
    }

    public static FriendlySocialHugSyncPacket decode(FriendlyByteBuf buffer) {
        return new FriendlySocialHugSyncPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readVarInt());
    }

    public static void handle(
            FriendlySocialHugSyncPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> FriendlySocialHugState.update(
                packet.grabberId,
                packet.grabbedId,
                packet.active,
                packet.durationTicks));
        context.setPacketHandled(true);
    }
}
