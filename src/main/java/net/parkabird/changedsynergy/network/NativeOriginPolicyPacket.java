package net.parkabird.changedsynergy.network;

import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;

public record NativeOriginPolicyPacket(boolean selective, Set<UUID> natives, Set<UUID> instinctPlayers) {
    public NativeOriginPolicyPacket(boolean selective,Set<UUID> natives){this(selective,natives,natives);}
    public static void encode(NativeOriginPolicyPacket packet, FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.selective); buffer.writeCollection(packet.natives, FriendlyByteBuf::writeUUID);
        buffer.writeCollection(packet.instinctPlayers,FriendlyByteBuf::writeUUID);
    }
    public static NativeOriginPolicyPacket decode(FriendlyByteBuf buffer) {
        boolean selective = buffer.readBoolean();
        return new NativeOriginPolicyPacket(selective,readIds(buffer),readIds(buffer));
    }
    private static Set<UUID> readIds(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 0 || count > 10000) throw new IllegalArgumentException("Invalid origin policy size");
        var ids = new java.util.HashSet<UUID>();
        for (int i = 0; i < count; i++) ids.add(buffer.readUUID());
        return Set.copyOf(ids);
    }
    public static void handle(NativeOriginPolicyPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
            NativeOriginPolicy.clientSelective = packet.selective;
            NativeOriginPolicy.clientNatives = packet.natives;
            NativeOriginPolicy.clientInstincts = packet.instinctPlayers;
        }));
        context.setPacketHandled(true);
    }
}
