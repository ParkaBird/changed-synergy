package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import net.parkabird.changedsynergy.event.LatexSocialEvents;

/** Opens the persistent absorption negotiation action from either player wheel. */
public record OpenAbsorptionNegotiationPacket() {
    public static void encode(
            OpenAbsorptionNegotiationPacket packet,
            FriendlyByteBuf buffer) {
    }

    public static OpenAbsorptionNegotiationPacket decode(
            FriendlyByteBuf buffer) {
        return new OpenAbsorptionNegotiationPacket();
    }

    public static void handle(
            OpenAbsorptionNegotiationPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer player = context.getSender();
        if (player != null) {
            context.enqueueWork(() -> open(player));
        }
        context.setPacketHandled(true);
    }

    private static void open(ServerPlayer player) {
        if (!player.isAlive() || player.isSpectator()
                || !InvoluntaryTransfurNegotiation.hasAbsorptionClaim(player)) {
            return;
        }
        long cooldown = InvoluntaryTransfurNegotiation
                .negotiationCooldownTicks(player);
        if (cooldown > 0L) {
            player.displayClientMessage(Component.translatable(
                    "message.changed_synergy.negotiation.cooldown",
                    Math.max(1L, (cooldown + 19L) / 20L)), true);
            return;
        }
        InvoluntaryTransfurNegotiation.claimAbsorptionOpeningLine(player);
        LatexSocialEvents.openAbsorptionNegotiationMenu(player);
    }
}
