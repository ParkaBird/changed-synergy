package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.parkabird.changedsynergy.client.GrabQteClientState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

/** Mirrors the server-authoritative expected QTE key back to the grabbed player. */
public record GrabQteSyncPacket(
        int grabberId,
        int grabbedId,
        int currentKey,
        int lastKey,
        int ticksUnpressed) {
    public static void encode(GrabQteSyncPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.grabberId);
        buffer.writeVarInt(packet.grabbedId);
        buffer.writeVarInt(packet.currentKey + 1);
        buffer.writeVarInt(packet.lastKey + 1);
        buffer.writeVarInt(packet.ticksUnpressed);
    }

    public static GrabQteSyncPacket decode(FriendlyByteBuf buffer) {
        return new GrabQteSyncPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt() - 1,
                buffer.readVarInt() - 1,
                buffer.readVarInt());
    }

    public static void handle(
            GrabQteSyncPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> GrabQteClientState.receive(packet)));
        context.setPacketHandled(true);
    }
}
