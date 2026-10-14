package net.parkabird.changedsynergy.network;

import com.google.gson.Gson;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** A read-only view of the server's common settings; client TOML never controls gameplay. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public record ServerConfigSnapshotPacket(CompoundTag values) {
    private static final Gson JSON = new Gson();
    private static final Map<MinecraftServer, CompoundTag> LAST_SENT = new WeakHashMap<>();

    public static CompoundTag capture() {
        CompoundTag result = new CompoundTag();
        ChangedSynergyConfig.options().forEach((key, value) -> result.putString(key, JSON.toJson(value.get())));
        return result;
    }

    public static CompoundTag capture(MinecraftServer server) {
        CompoundTag result = capture();
        for (var setting : ChangedSynergyGameRules.settings())
            result.putString("gamerule." + setting.name(), setting.value(server.getGameRules()));
        return result;
    }

    public static void encode(ServerConfigSnapshotPacket packet, FriendlyByteBuf buffer) { buffer.writeNbt(packet.values); }
    public static ServerConfigSnapshotPacket decode(FriendlyByteBuf buffer) {
        CompoundTag values = buffer.readNbt();
        return new ServerConfigSnapshotPacket(values == null ? new CompoundTag() : values);
    }
    public static void handle(ServerConfigSnapshotPacket packet, Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                () -> () -> net.parkabird.changedsynergy.client.ServerConfigClient.receive(packet.values)));
        context.setPacketHandled(true);
    }

    @SubscribeEvent public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player)
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ServerConfigSnapshotPacket(capture(player.server)));
    }
    @SubscribeEvent public static void onTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 20 != 0) return;
        CompoundTag current = capture(event.getServer());
        if (current.equals(LAST_SENT.put(event.getServer(), current))) return;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            ChangedSynergyNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ServerConfigSnapshotPacket(current));
    }
}
