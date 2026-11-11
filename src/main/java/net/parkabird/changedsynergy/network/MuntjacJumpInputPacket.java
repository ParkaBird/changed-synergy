package net.parkabird.changedsynergy.network;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.entity.MuntjacJumpService;

/** Only key edges; the server derives tap/hold timing, charge duration and allowed species. */
public record MuntjacJumpInputPacket(int action) {
    public static final int RELEASE = 0, PRESS = 1, CANCEL = 2;
    public static void encode(MuntjacJumpInputPacket packet, FriendlyByteBuf buffer) { buffer.writeByte(packet.action); }
    public static MuntjacJumpInputPacket decode(FriendlyByteBuf buffer) { return new MuntjacJumpInputPacket(buffer.readUnsignedByte()); }
    public static void handle(MuntjacJumpInputPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null || packet.action < RELEASE || packet.action > CANCEL) return;
            if (packet.action == CANCEL) MuntjacJumpService.state(player).cancel(false);
            else MuntjacJumpService.input(player, packet.action == PRESS);
        });
        context.setPacketHandled(true);
    }
}
