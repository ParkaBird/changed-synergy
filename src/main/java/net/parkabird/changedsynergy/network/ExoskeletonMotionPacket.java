package net.parkabird.changedsynergy.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.TakeoverClientState;

/** Authoritative, smoothly rendered locomotion for a worn exoskeleton controller. */
public record ExoskeletonMotionPacket(UUID sessionId, double x, double y, double z,
        double velocityX, double velocityY, double velocityZ, float yaw) {
    public static void encode(ExoskeletonMotionPacket packet, FriendlyByteBuf buffer) {
        buffer.writeUUID(packet.sessionId);
        buffer.writeDouble(packet.x);
        buffer.writeDouble(packet.y);
        buffer.writeDouble(packet.z);
        buffer.writeDouble(packet.velocityX);
        buffer.writeDouble(packet.velocityY);
        buffer.writeDouble(packet.velocityZ);
        buffer.writeFloat(packet.yaw);
    }

    public static ExoskeletonMotionPacket decode(FriendlyByteBuf buffer) {
        return new ExoskeletonMotionPacket(buffer.readUUID(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readDouble(),
                buffer.readDouble(), buffer.readDouble(), buffer.readFloat());
    }

    public static void handle(ExoskeletonMotionPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> TakeoverClientState.receiveExoskeletonMotion(packet));
        context.setPacketHandled(true);
    }
}
