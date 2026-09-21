package net.parkabird.changedsynergy.ai;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.process.TransfurEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.MergedPlayerIdentityClient;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.MergedPlayerIdentityPacket;

/** Keeps the absorbed creature's identity attached to the player until untransfur. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID)
public final class MergedPlayerIdentity {
    private static final String NAME = "SynergyMergedCarrierName";

    private MergedPlayerIdentity() {
    }

    public static void apply(ServerPlayer player, Component name) {
        persisted(player).putString(NAME, Component.Serializer.toJson(name));
        syncAll(player, name);
    }

    public static void clear(ServerPlayer player) {
        if (!persisted(player).contains(NAME)) return;
        persisted(player).remove(NAME);
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new MergedPlayerIdentityPacket(player.getUUID(), "", false));
        refreshTabName(player);
    }

    @Nullable
    public static Component displayName(Player player) {
        if (player.level().isClientSide) {
            return MergedPlayerIdentityClient.name(player.getUUID());
        }
        String json = persisted(player).getString(NAME);
        if (json.isEmpty()) return null;
        try {
            return Component.Serializer.fromJson(json);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    @SubscribeEvent
    public static void onNameFormat(PlayerEvent.NameFormat event) {
        Component name = displayName(event.getEntity());
        if (name != null) event.setDisplayname(name.copy());
    }

    @SubscribeEvent
    public static void onTabNameFormat(PlayerEvent.TabListNameFormat event) {
        Component name = displayName(event.getEntity());
        if (name != null) event.setDisplayName(name.copy());
    }

    @SubscribeEvent
    public static void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0
                || !persisted(player).contains(NAME)
                || ProcessTransfur.isPlayerTransfurred(player)) {
            return;
        }
        clear(player);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public static void onUntransfur(TransfurEvents.UntransfurPlayerEvent event) {
        if (!event.isCanceled() && event.getPlayer() instanceof ServerPlayer player) {
            clear(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joining)) return;
        for (ServerPlayer player : joining.server.getPlayerList().getPlayers()) {
            if (player == joining) continue;
            Component current = displayName(player);
            if (current != null) syncTo(player, current, joining);
        }
        Component current = displayName(joining);
        if (current != null) syncAll(joining, current);
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (!(event.getEntity() instanceof ServerPlayer watcher)
                || !(event.getTarget() instanceof ServerPlayer target)) {
            return;
        }
        Component current = displayName(target);
        if (current != null) syncTo(target, current, watcher);
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        if (!(event.getOriginal() instanceof ServerPlayer original)
                || !(event.getEntity() instanceof ServerPlayer clone)) {
            return;
        }
        String json = persisted(original).getString(NAME);
        if (!json.isEmpty()) {
            persisted(clone).putString(NAME, json);
            Component current = displayName(clone);
            if (current != null) syncAll(clone, current);
        }
    }

    private static void syncAll(ServerPlayer player, Component name) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.ALL.noArg(),
                new MergedPlayerIdentityPacket(
                        player.getUUID(), Component.Serializer.toJson(name), true));
        refreshTabName(player);
    }

    private static void syncTo(
            ServerPlayer subject,
            Component name,
            ServerPlayer receiver) {
        ChangedSynergyNetwork.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> receiver),
                new MergedPlayerIdentityPacket(
                        subject.getUUID(), Component.Serializer.toJson(name), true));
    }

    private static void refreshTabName(ServerPlayer player) {
        player.refreshDisplayName();
        player.refreshTabListName();
    }

    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
