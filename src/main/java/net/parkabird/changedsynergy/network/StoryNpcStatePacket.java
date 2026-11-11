package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

public record StoryNpcStatePacket(CompoundTag state) {
    public static void encode(StoryNpcStatePacket packet,FriendlyByteBuf buffer){buffer.writeNbt(packet.state);}
    public static StoryNpcStatePacket decode(FriendlyByteBuf buffer){var tag=buffer.readNbt();return new StoryNpcStatePacket(tag==null?new CompoundTag():tag);}
    public static void handle(StoryNpcStatePacket packet,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.parkabird.changedsynergy.client.StoryNpcScreen.receive(packet.state)));context.setPacketHandled(true);}
}
