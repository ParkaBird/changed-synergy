package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.ScoutTargetClientState;

/** A short-lived outline sent only to viewers matching the scout's faction. */
public record ScoutTargetPacket(int entityId) {
    public static void encode(
            ScoutTargetPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityId);
    }

    public static ScoutTargetPacket decode(FriendlyByteBuf buffer) {
        return new ScoutTargetPacket(buffer.readVarInt());
    }

    public static void handle(
            ScoutTargetPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> ScoutTargetClientState.receive(
                        packet.entityId)));
        context.setPacketHandled(true);
    }
}
