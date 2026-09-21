package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.PatAnimationService;

/** Server-authoritative, high-resolution mouse-wheel adjustment for a pat. */
public record PatAnimationSpeedPacket(double scrollDelta) {
    public static void encode(
            PatAnimationSpeedPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeDouble(packet.scrollDelta);
    }

    public static PatAnimationSpeedPacket decode(FriendlyByteBuf buffer) {
        return new PatAnimationSpeedPacket(buffer.readDouble());
    }

    public static void handle(
            PatAnimationSpeedPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        var sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> PatAnimationService.adjustSpeed(
                    sender, packet.scrollDelta));
        }
        context.setPacketHandled(true);
    }
}
