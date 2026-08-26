package net.parkabird.changedsynergy.client;

import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Invalidates the custom post chain whenever client resources are reloaded. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class PureWhiteVisionClientModEvents {
    private PureWhiteVisionClientModEvents() {
    }

    @SubscribeEvent
    public static void registerReloadListener(
            RegisterClientReloadListenersEvent event) {
        event.registerReloadListener((ResourceManagerReloadListener)
                resourceManager -> PureWhiteVisionRenderer.requestReload());
    }
}
