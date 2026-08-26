package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.EmoteClientState;

/**
 * Begins or ends one identified emote state without allowing an older
 * speaker's expiry packet to remove a newer bubble over the same entity.
 */
public record EmoteTransitionPacket(int targetId, int token, boolean begin) {
    public static void encode(
            EmoteTransitionPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.targetId);
        buffer.writeVarInt(packet.token);
        buffer.writeBoolean(packet.begin);
    }

    public static EmoteTransitionPacket decode(FriendlyByteBuf buffer) {
        return new EmoteTransitionPacket(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean());
    }

    public static void handle(
            EmoteTransitionPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> {
                    if (packet.begin) {
                        EmoteClientState.begin(packet.targetId, packet.token);
                    } else {
                        EmoteClientState.end(packet.targetId, packet.token);
                    }
                }));
        context.setPacketHandled(true);
    }
}
