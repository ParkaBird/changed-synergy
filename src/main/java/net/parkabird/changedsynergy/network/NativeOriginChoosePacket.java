package net.parkabird.changedsynergy.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.origin.NativeOriginService;

public record NativeOriginChoosePacket(UUID nonce, ResourceLocation form, boolean fallback, boolean cancel) {
    public static void encode(NativeOriginChoosePacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.nonce); buffer.writeNullable(packet.form, FriendlyByteBuf::writeResourceLocation);
        buffer.writeBoolean(packet.fallback); buffer.writeBoolean(packet.cancel);
    }
    public static NativeOriginChoosePacket decode(FriendlyByteBuf buffer) {
        return new NativeOriginChoosePacket(buffer.readUUID(), buffer.readNullable(FriendlyByteBuf::readResourceLocation), buffer.readBoolean(), buffer.readBoolean());
    }
    public static void handle(NativeOriginChoosePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            if (context.getSender() != null) NativeOriginService.choose(context.getSender(), packet);
        });
        context.setPacketHandled(true);
    }
}
