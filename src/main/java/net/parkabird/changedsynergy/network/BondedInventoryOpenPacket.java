package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.network.NetworkEvent;

/** Requests a bonded inventory independently of the radial menu update packet. */
public record BondedInventoryOpenPacket(int petId) {
    public static void encode(BondedInventoryOpenPacket packet, FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.petId);
    }

    public static BondedInventoryOpenPacket decode(FriendlyByteBuf buffer) {
        return new BondedInventoryOpenPacket(buffer.readVarInt());
    }

    public static void handle(
            BondedInventoryOpenPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                Entity entity = sender.level().getEntity(packet.petId);
                if (entity instanceof ChangedEntity pet) {
                    BondedInventoryService.open(sender, pet);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
