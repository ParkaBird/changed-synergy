package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.ChangedOverlays;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.util.PureWhiteVision;

/** Replaces Changed's reduced-vision veil with Synergy's custom post-process. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PureWhiteBlindnessOverlayGuard {
    private static boolean suppressionReported;

    private PureWhiteBlindnessOverlayGuard() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void beforeOverlay(RenderGuiOverlayEvent.Pre event) {
        // RegisterGuiOverlaysEvent derives its namespace from the active mod
        // loading context.  In a large mod pack that runtime namespace is not
        // guaranteed to equal the ResourceLocation constant Changed exposes,
        // but the registered path remains uniquely "variant_blindness".
        if (!ChangedOverlays.VARIANT_BLINDNESS_OVERLAY.getPath().equals(
                event.getOverlay().id().getPath())) {
            return;
        }

        var player = Minecraft.getInstance().player;
        boolean pureWhiteForm = player != null
                && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(PureWhiteVision::isPureWhiteForm)
                .orElse(false);
        if (pureWhiteForm || PureWhiteVisionClientState.active()) {
            event.setCanceled(true);
            if (!suppressionReported) {
                suppressionReported = true;
                ChangedSynergyMod.LOGGER.info(
                        "Suppressing Changed reduced-vision HUD overlay {}",
                        event.getOverlay().id());
            }
        }
    }
}
