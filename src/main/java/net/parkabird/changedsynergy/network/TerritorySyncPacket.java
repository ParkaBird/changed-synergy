package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.TerritoryClientState;

/** Synchronizes the player's current biome or active facility room. */
public record TerritorySyncPacket(
        boolean displayEnabled,
        boolean facility,
        String facilityCode,
        String zoneId,
        String templateId,
        String biomeId,
        int factionOrdinal,
        String populationRegion,
        int reputationScore,
        @Nullable BlockPos outpostMarker) {
    public static void encode(
            TerritorySyncPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeBoolean(packet.displayEnabled);
        buffer.writeBoolean(packet.facility);
        buffer.writeUtf(packet.facilityCode, 32);
        buffer.writeUtf(packet.zoneId, 128);
        buffer.writeUtf(packet.templateId, 256);
        buffer.writeUtf(packet.biomeId, 128);
        buffer.writeVarInt(packet.factionOrdinal + 1);
        buffer.writeUtf(packet.populationRegion, 64);
        buffer.writeVarInt(packet.reputationScore);
        buffer.writeBoolean(packet.outpostMarker != null);
        if (packet.outpostMarker != null) buffer.writeBlockPos(packet.outpostMarker);
    }

    public static TerritorySyncPacket decode(FriendlyByteBuf buffer) {
        return new TerritorySyncPacket(
                buffer.readBoolean(),
                buffer.readBoolean(),
                buffer.readUtf(32),
                buffer.readUtf(128),
                buffer.readUtf(256),
                buffer.readUtf(128),
                buffer.readVarInt() - 1,
                buffer.readUtf(64),
                buffer.readVarInt(),
                buffer.readBoolean() ? buffer.readBlockPos() : null);
    }

    public static void handle(
            TerritorySyncPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> TerritoryClientState.receive(packet)));
        context.setPacketHandled(true);
    }
}
