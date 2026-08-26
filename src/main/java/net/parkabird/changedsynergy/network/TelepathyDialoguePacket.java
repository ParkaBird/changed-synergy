package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.TelepathyDanmakuState;

/** Sends ordinary creature speech to the lightweight telepathic overlay. */
public record TelepathyDialoguePacket(
        Component message,
        Component speaker,
        Component dialogue,
        int accentColor) {
    public static void encode(
            TelepathyDialoguePacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeComponent(packet.message);
        buffer.writeComponent(packet.speaker);
        buffer.writeComponent(packet.dialogue);
        buffer.writeInt(packet.accentColor);
    }

    public static TelepathyDialoguePacket decode(FriendlyByteBuf buffer) {
        return new TelepathyDialoguePacket(
                buffer.readComponent(),
                buffer.readComponent(),
                buffer.readComponent(),
                buffer.readInt());
    }

    public static void handle(
            TelepathyDialoguePacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> TelepathyDanmakuState.receive(
                        packet.message,
                        packet.speaker,
                        packet.dialogue,
                        packet.accentColor)));
        context.setPacketHandled(true);
    }
}
