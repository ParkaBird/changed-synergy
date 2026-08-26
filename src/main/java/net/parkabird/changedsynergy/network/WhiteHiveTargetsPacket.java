package net.parkabird.changedsynergy.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.WhiteHiveTargetClientState;

/** Player-local outline targets shared by nearby members of the white hive. */
public record WhiteHiveTargetsPacket(List<Integer> entityIds) {
    private static final int MAX_TARGETS = 64;

    public WhiteHiveTargetsPacket {
        entityIds = List.copyOf(entityIds.subList(
                0, Math.min(MAX_TARGETS, entityIds.size())));
    }

    public static void encode(
            WhiteHiveTargetsPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.entityIds.size());
        packet.entityIds.forEach(buffer::writeVarInt);
    }

    public static WhiteHiveTargetsPacket decode(FriendlyByteBuf buffer) {
        int size = Math.min(MAX_TARGETS, Math.max(0, buffer.readVarInt()));
        List<Integer> ids = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            ids.add(buffer.readVarInt());
        }
        return new WhiteHiveTargetsPacket(ids);
    }

    public static void handle(
            WhiteHiveTargetsPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> WhiteHiveTargetClientState.receive(
                        packet.entityIds)));
        context.setPacketHandled(true);
    }
}
