package net.parkabird.changedsynergy.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.PlayerSocialClient;

/** Public player identity for a separately tracked INSTINCT body. */
public record PlayerBodyPacket(UUID body, UUID owner, Component name, String account) {
    public static void encode(PlayerBodyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.body);
        buffer.writeUUID(packet.owner);
        buffer.writeComponent(packet.name);
        buffer.writeUtf(packet.account, 16);
    }

    public static PlayerBodyPacket decode(FriendlyByteBuf buffer) {
        return new PlayerBodyPacket(buffer.readUUID(), buffer.readUUID(), buffer.readComponent(), buffer.readUtf(16));
    }

    public static void handle(PlayerBodyPacket packet, Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> PlayerSocialClient.receive(packet)));
        context.setPacketHandled(true);
    }
}
