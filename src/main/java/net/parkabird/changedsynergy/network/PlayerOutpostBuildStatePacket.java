package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.PlayerOutpostBuildClient;

public record PlayerOutpostBuildStatePacket(boolean active, int palette) {
    public static void encode(PlayerOutpostBuildStatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.active);
        buffer.writeVarInt(packet.palette);
    }

    public static PlayerOutpostBuildStatePacket decode(FriendlyByteBuf buffer) {
        return new PlayerOutpostBuildStatePacket(buffer.readBoolean(), buffer.readVarInt());
    }

    public static void handle(PlayerOutpostBuildStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> PlayerOutpostBuildClient.apply(packet.active(), packet.palette())));
        context.setPacketHandled(true);
    }
}
