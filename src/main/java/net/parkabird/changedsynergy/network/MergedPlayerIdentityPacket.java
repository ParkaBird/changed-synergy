package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.MergedPlayerIdentityClient;

/** Synchronizes the temporary display name created by a completed takeover merge. */
public record MergedPlayerIdentityPacket(
        UUID playerId,
        String nameJson,
        boolean active) {
    public static void encode(
            MergedPlayerIdentityPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.playerId);
        buffer.writeUtf(packet.nameJson, 1024);
        buffer.writeBoolean(packet.active);
    }

    public static MergedPlayerIdentityPacket decode(FriendlyByteBuf buffer) {
        return new MergedPlayerIdentityPacket(
                buffer.readUUID(), buffer.readUtf(1024), buffer.readBoolean());
    }

    public static void handle(
            MergedPlayerIdentityPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> MergedPlayerIdentityClient.receive(
                        packet.playerId, packet.nameJson, packet.active)));
        context.setPacketHandled(true);
    }
}
