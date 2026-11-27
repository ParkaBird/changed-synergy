package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.SilkMothWingFlapClient;

public record SilkMothWingFlapPacket(int playerId) {
    public static void encode(SilkMothWingFlapPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.playerId);
    }

    public static SilkMothWingFlapPacket decode(FriendlyByteBuf buffer) {
        return new SilkMothWingFlapPacket(buffer.readVarInt());
    }

    public static void handle(SilkMothWingFlapPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(
                net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> SilkMothWingFlapClient.receive(packet.playerId)));
        context.setPacketHandled(true);
    }
}
