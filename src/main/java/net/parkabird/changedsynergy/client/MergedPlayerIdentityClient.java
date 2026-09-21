package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Client copy of temporary merged names, keyed by the stable player UUID. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class MergedPlayerIdentityClient {
    private static final Map<UUID, Component> NAMES = new HashMap<>();

    private MergedPlayerIdentityClient() {
    }

    public static void receive(UUID playerId, String json, boolean active) {
        if (!active) {
            NAMES.remove(playerId);
            return;
        }
        try {
            Component name = Component.Serializer.fromJson(json);
            if (name != null) NAMES.put(playerId, name);
        } catch (RuntimeException ignored) {
            NAMES.remove(playerId);
        }
    }

    @Nullable
    public static Component name(UUID playerId) {
        return NAMES.get(playerId);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        NAMES.clear();
    }
}
