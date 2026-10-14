package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.NativeOriginStatePacket;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class NativeOriginClient {
    private NativeOriginClient() {}
    private static NativeOriginStatePacket state;
    public static void receive(NativeOriginStatePacket packet) {
        var minecraft = Minecraft.getInstance();
        if (packet.phase() == 3) {
            state = null;
            if (minecraft.screen instanceof NativeOriginScreen) minecraft.setScreen(null);
            if (minecraft.player != null) minecraft.player.sendSystemMessage(packet.message());
        } else {
            state = packet;
            if (minecraft.screen instanceof NativeOriginScreen screen) screen.receive(packet);
        }
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var minecraft = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && state != null && minecraft.level != null
                && minecraft.player != null && minecraft.player.isAlive()
                && !(minecraft.screen instanceof NativeOriginScreen)) minecraft.setScreen(new NativeOriginScreen(state));
    }
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event) {
        state = null; NativeOriginPolicy.clientSelective = false; NativeOriginPolicy.clientNatives = java.util.Set.of();
        NativeOriginPolicy.clientInstincts = java.util.Set.of();
    }
}
