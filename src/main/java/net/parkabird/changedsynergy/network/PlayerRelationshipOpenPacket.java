package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.world.inventory.PlayerRelationshipMenu;

/** Opens the player relationship hub from Changed's existing ability key. */
public record PlayerRelationshipOpenPacket() {
    public static void encode(
            PlayerRelationshipOpenPacket packet,
            FriendlyByteBuf buffer) {
    }

    public static PlayerRelationshipOpenPacket decode(FriendlyByteBuf buffer) {
        return new PlayerRelationshipOpenPacket();
    }

    public static void handle(
            PlayerRelationshipOpenPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                if (sender.isAlive()
                        && !sender.isSpectator()
                        && !net.parkabird.changedsynergy.ai.TakeoverService.active(sender)
                        && !net.parkabird.changedsynergy.ai.HypnosisQteService.isPlayerControlLocked(sender)
                        && !net.parkabird.changedsynergy.origin.NativeOriginService.pending(sender)
                        && !(sender.containerMenu instanceof PlayerRelationshipMenu)) {
                    PlayerRelationshipMenu.open(sender);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
