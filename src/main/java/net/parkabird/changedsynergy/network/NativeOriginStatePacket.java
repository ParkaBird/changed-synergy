package net.parkabird.changedsynergy.network;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.client.NativeOriginClient;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog.Entry;

public record NativeOriginStatePacket(int phase, UUID nonce, boolean available, boolean canCancel,
        Component message, List<Entry> entries) {
    public static final int MAX_ENTRIES = 4096;
    public static void encode(NativeOriginStatePacket packet, FriendlyByteBuf buffer) {
        if (packet.entries.size() > MAX_ENTRIES) throw new IllegalArgumentException("Invalid origin catalog size");
        buffer.writeVarInt(packet.phase); buffer.writeUUID(packet.nonce);
        buffer.writeBoolean(packet.available); buffer.writeBoolean(packet.canCancel); buffer.writeComponent(packet.message);
        buffer.writeCollection(packet.entries, (buf, entry) -> {
            buf.writeResourceLocation(entry.form()); buf.writeUtf(entry.faction(), 16); buf.writeUtf(entry.region(), 32);
        });
    }
    public static NativeOriginStatePacket decode(FriendlyByteBuf buffer) {
        int phase = buffer.readVarInt(); UUID nonce = buffer.readUUID(); boolean available = buffer.readBoolean();
        boolean canCancel = buffer.readBoolean();
        Component message = buffer.readComponent();
        int count = buffer.readVarInt();
        if (count < 0 || count > MAX_ENTRIES) throw new IllegalArgumentException("Invalid origin catalog size");
        var entries = new java.util.ArrayList<Entry>();
        for (int i = 0; i < count; i++) entries.add(new Entry(buffer.readResourceLocation(), buffer.readUtf(16), buffer.readUtf(32)));
        return new NativeOriginStatePacket(phase, nonce, available, canCancel, message, List.copyOf(entries));
    }
    public static void handle(NativeOriginStatePacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> NativeOriginClient.receive(packet)));
        context.setPacketHandled(true);
    }
}
