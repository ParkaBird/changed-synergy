package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.AbsorptionNegotiationClientState;

/** Keeps the conditional radial-wheel action synchronized after respawns. */
public record AbsorptionNegotiationStatePacket(boolean active) {
    public static void encode(
            AbsorptionNegotiationStatePacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
    }

    public static AbsorptionNegotiationStatePacket decode(
            FriendlyByteBuf buffer) {
        return new AbsorptionNegotiationStatePacket(buffer.readBoolean());
    }

    public static void handle(
            AbsorptionNegotiationStatePacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> AbsorptionNegotiationClientState.receive(
                        packet.active)));
        context.setPacketHandled(true);
    }
}
