package net.parkabird.changedsynergy.client;

import java.util.HashMap;
import java.util.Map;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.ltxprogrammer.changed.util.Color3;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.TransfurVisualPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class LegacyTransfurVisualState {
    private static final Map<Integer, VisualStyle> STYLES = new HashMap<>();
    private static final Color3 FALLBACK_COLOR = Color3.fromInt(0xE5EEF7);
    private static final ResourceLocation CHANGED_DANGER_OVERLAY =
            ResourceLocation.fromNamespaceAndPath("changed", "danger");

    private LegacyTransfurVisualState() {
    }

    public static void receive(int entityId, int color, boolean organic) {
        if (color == TransfurVisualPacket.CLEAR) {
            STYLES.remove(entityId);
        } else {
            STYLES.put(entityId, new VisualStyle(Color3.fromInt(color), organic));
        }
    }

    public static Color3 colorFor(Player player) {
        VisualStyle style = STYLES.get(player.getId());
        return style == null ? FALLBACK_COLOR : style.color;
    }

    public static boolean isOrganicFor(Player player) {
        VisualStyle style = player == null ? null : STYLES.get(player.getId());
        return style != null && style.organic;
    }

    public static float progressFor(Player player) {
        if (player == null || ProcessTransfur.isPlayerTransfurred(player)) {
            return 0.0F;
        }
        double tolerance = ProcessTransfur.getEntityTransfurTolerance(player);
        if (tolerance <= 0.0) {
            return 0.0F;
        }
        return Mth.clamp((float)(ProcessTransfur.getPlayerTransfurProgress(player) / tolerance), 0.0F, 1.0F);
    }

    /** Replace only Changed's partial-progress meter; keep its completed transfur animation overlay. */
    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Pre event) {
        Player player = Minecraft.getInstance().player;
        if (ChangedSynergyClientConfig.CLIENT.legacyTransfurScreenEffect.get()
                && CHANGED_DANGER_OVERLAY.equals(event.getOverlay().id())
                && player != null
                && progressFor(player) > 0.0F) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        STYLES.clear();
    }

    private record VisualStyle(Color3 color, boolean organic) {
    }
}
