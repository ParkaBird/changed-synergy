package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.PatAnimationService;
import net.parkabird.changedsynergy.ai.SynergyPatService;

public record PatAnimationControlPacket(
        int targetId,
        boolean active,
        boolean triggerReaction) {
    public static void encode(
            PatAnimationControlPacket packet,
            FriendlyByteBuf buffer) {
        buffer.writeVarInt(packet.targetId);
        buffer.writeBoolean(packet.active);
        buffer.writeBoolean(packet.triggerReaction);
    }

    public static PatAnimationControlPacket decode(FriendlyByteBuf buffer) {
        return new PatAnimationControlPacket(
                buffer.readVarInt(),
                buffer.readBoolean(),
                buffer.readBoolean());
    }

    public static void handle(
            PatAnimationControlPacket packet,
            Supplier<NetworkEvent.Context> supplier) {
        NetworkEvent.Context context = supplier.get();
        ServerPlayer sender = context.getSender();
        if (sender != null) {
            context.enqueueWork(() -> {
                if (!packet.active) {
                    PatAnimationService.stop(sender);
                    return;
                }
                Entity entity = sender.level().getEntity(packet.targetId);
                if (entity instanceof LivingEntity target) {
                    if (packet.triggerReaction) {
                        SynergyPatService.perform(sender, target, false);
                    }
                    PatAnimationService.refreshContinuous(sender, target);
                }
            });
        }
        context.setPacketHandled(true);
    }
}
