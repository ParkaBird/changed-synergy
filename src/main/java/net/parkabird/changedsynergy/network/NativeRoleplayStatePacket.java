package net.parkabird.changedsynergy.network;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
public record NativeRoleplayStatePacket(UUID player,boolean nativeRole,Component name,boolean global) {
    public static void encode(NativeRoleplayStatePacket p,FriendlyByteBuf b) {b.writeUUID(p.player);b.writeBoolean(p.nativeRole);b.writeComponent(p.name);b.writeBoolean(p.global);}
    public static NativeRoleplayStatePacket decode(FriendlyByteBuf b) {return new NativeRoleplayStatePacket(b.readUUID(),b.readBoolean(),b.readComponent(),b.readBoolean());}
    public static void handle(NativeRoleplayStatePacket p,Supplier<NetworkEvent.Context> s) {
        var c=s.get();c.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->net.parkabird.changedsynergy.client.NativeRoleplayClient.receive(p)));c.setPacketHandled(true);
    }
}
