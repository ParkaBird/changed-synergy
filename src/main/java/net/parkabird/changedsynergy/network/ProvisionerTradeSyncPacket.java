package net.parkabird.changedsynergy.network;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.ProvisionerTradeService.TradeOffer;
import net.parkabird.changedsynergy.world.inventory.ProvisionerTradeMenu;

/** Refreshes a still-open barter screen after a server-authoritative trade. */
public record ProvisionerTradeSyncPacket(
        int containerId,
        List<TradeOffer> offers,
        String statusKey) {
    public static void encode(
            ProvisionerTradeSyncPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.containerId);
        ProvisionerTradeMenu.writeOffers(buffer, packet.offers);
        buffer.writeUtf(packet.statusKey == null ? "" : packet.statusKey);
    }

    public static ProvisionerTradeSyncPacket decode(FriendlyByteBuf buffer) {
        return new ProvisionerTradeSyncPacket(
                buffer.readVarInt(),
                ProvisionerTradeMenu.readOffers(buffer),
                buffer.readUtf(256));
    }

    public static void handle(
            ProvisionerTradeSyncPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(
                Dist.CLIENT,
                () -> () -> {
                    Minecraft minecraft = Minecraft.getInstance();
                    if (minecraft.player != null
                            && minecraft.player.containerMenu
                                    instanceof ProvisionerTradeMenu menu
                            && menu.containerId == packet.containerId) {
                        menu.applySync(packet.offers, packet.statusKey);
                    }
                }));
        context.setPacketHandled(true);
    }
}
