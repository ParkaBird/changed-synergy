package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.PerformanceDiagnosticsClient;

/** One compact, once-per-second server performance sample. */
public record PerformanceSnapshotPacket(
        float serverMspt,
        float latexAiMspt,
        int latexCount,
        int optionalRuns,
        int deferredRuns,
        boolean budgetLimited) {
    public static void encode(
            PerformanceSnapshotPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeFloat(packet.serverMspt);
        buffer.writeFloat(packet.latexAiMspt);
        buffer.writeVarInt(packet.latexCount);
        buffer.writeVarInt(packet.optionalRuns);
        buffer.writeVarInt(packet.deferredRuns);
        buffer.writeBoolean(packet.budgetLimited);
    }

    public static PerformanceSnapshotPacket decode(FriendlyByteBuf buffer) {
        return new PerformanceSnapshotPacket(
                buffer.readFloat(),
                buffer.readFloat(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readBoolean());
    }

    public static void handle(
            PerformanceSnapshotPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> PerformanceDiagnosticsClient.receive(packet)));
        context.setPacketHandled(true);
    }
}
