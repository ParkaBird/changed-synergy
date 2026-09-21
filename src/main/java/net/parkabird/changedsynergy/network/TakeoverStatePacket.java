package net.parkabird.changedsynergy.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.TakeoverClientState;

public record TakeoverStatePacket(UUID sessionId, int kind, int phase, int carrierId, int playerId,
        int releaseRemainingTicks, int releaseTotalTicks,
        int borrowCooldownTicks, boolean escapeUsed, int expectedKey,
        int sequence, int progress, int qteLength, float fade, String carrierName) {
    public static final int NORMAL = 0, EXOSKELETON = 1;
    public static final int CONTROLLED = 0, BORROWED = 1, STRUGGLE = 2,
            SLEEPING = 3, RESERVED = 4, RELEASING = 5, FINISHED = 6;

    public static void encode(TakeoverStatePacket p, FriendlyByteBuf b) {
        b.writeUUID(p.sessionId);
        b.writeVarInt(p.kind);
        b.writeVarInt(p.phase);
        b.writeVarInt(p.carrierId);
        b.writeVarInt(p.playerId);
        b.writeVarInt(p.releaseRemainingTicks);
        b.writeVarInt(p.releaseTotalTicks);
        b.writeVarInt(p.borrowCooldownTicks);
        b.writeBoolean(p.escapeUsed);
        b.writeVarInt(p.expectedKey);
        b.writeVarInt(p.sequence);
        b.writeVarInt(p.progress);
        b.writeVarInt(p.qteLength);
        b.writeFloat(p.fade);
        b.writeUtf(p.carrierName, 256);
    }

    public static TakeoverStatePacket decode(FriendlyByteBuf b) {
        return new TakeoverStatePacket(b.readUUID(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(),
                b.readVarInt(), b.readBoolean(),
                b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readVarInt(), b.readFloat(), b.readUtf(256));
    }

    public static void handle(TakeoverStatePacket p, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> TakeoverClientState.receive(p)));
        context.setPacketHandled(true);
    }
}
