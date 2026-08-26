package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/** Right-click action for the native pet wheel's suit-owner entry. */
public record NativePetReassimilatePacket(int petId) {
    public static void encode(NativePetReassimilatePacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.petId);
    }

    public static NativePetReassimilatePacket decode(FriendlyByteBuf buffer) {
        return new NativePetReassimilatePacket(buffer.readVarInt());
    }

    public static void handle(
            NativePetReassimilatePacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                Entity entity = sender.level().getEntity(packet.petId);
                if (entity instanceof ChangedEntity pet) {
                    BondedSuitService.reassimilateNativePet(pet, sender);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
