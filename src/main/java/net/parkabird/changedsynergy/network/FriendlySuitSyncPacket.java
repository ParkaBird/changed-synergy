package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.parkabird.changedsynergy.client.FriendlySuitClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/**
 * Synchronizes the complete owner-controlled suit relation. Changed's native
 * packet omits the control bit, while Addon's follow-up packet can arrive before
 * its client-side grab reference exists.
 */
public record FriendlySuitSyncPacket(int grabberId, int ownerId, boolean active) {
    public static void encode(FriendlySuitSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.grabberId);
        buffer.writeVarInt(packet.ownerId);
        buffer.writeBoolean(packet.active);
    }

    public static FriendlySuitSyncPacket decode(FriendlyByteBuf buffer) {
        return new FriendlySuitSyncPacket(
                buffer.readVarInt(), buffer.readVarInt(), buffer.readBoolean());
    }

    public static void handle(
            FriendlySuitSyncPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> FriendlySuitClientState.receive(
                        packet.grabberId, packet.ownerId, packet.active)));
        context.setPacketHandled(true);
    }
}
