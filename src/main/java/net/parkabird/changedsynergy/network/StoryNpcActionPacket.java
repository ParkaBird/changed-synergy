package net.parkabird.changedsynergy.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record StoryNpcActionPacket(UUID session,int revision,String action) {
    public static void encode(StoryNpcActionPacket packet,FriendlyByteBuf buffer){buffer.writeUUID(packet.session);buffer.writeVarInt(packet.revision);buffer.writeUtf(packet.action,64);}
    public static StoryNpcActionPacket decode(FriendlyByteBuf buffer){return new StoryNpcActionPacket(buffer.readUUID(),buffer.readVarInt(),buffer.readUtf(64));}
    public static void handle(StoryNpcActionPacket packet,Supplier<NetworkEvent.Context> supplier){var context=supplier.get();context.enqueueWork(()->{var player=context.getSender();if(player!=null)net.parkabird.changedsynergy.npc.NpcConversations.action(player,packet.session,packet.revision,packet.action);});context.setPacketHandled(true);}
}
