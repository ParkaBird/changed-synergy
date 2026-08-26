package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.ltxprogrammer.changed.util.Color3;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LegacyTransfurVisuals {
    private static final ResourceLocation GOO_OUTLINE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/misc/goo_outline.png");
    private static final int TEXTURE_SIZE = 256;

    private LegacyTransfurVisuals() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("legacy_transfur_goo", LegacyTransfurVisuals::renderScreenOverlay);
    }

    @SubscribeEvent
    public static void addPlayerLayers(EntityRenderersEvent.AddLayers event) {
        for (String skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer != null) {
                renderer.addLayer(new LegacyTransfurProgressLayer(renderer));
            }
        }
    }

    private static void renderScreenOverlay(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        AbstractClientPlayer player = Minecraft.getInstance().player;
        if (!ChangedSynergyClientConfig.CLIENT.legacyTransfurScreenEffect.get()
                || player == null) {
            return;
        }

        float progress = LegacyTransfurVisualState.progressFor(player);
        if (progress <= 0.0F) {
            return;
        }

        gui.setupOverlayRenderState(true, false);
        boolean organic = LegacyTransfurVisualState.isOrganicFor(player);
        Color3 color = LegacyTransfurVisualState.colorFor(player);
        float opacity = ChangedSynergyClientConfig.CLIENT.legacyTransfurScreenOpacity.get().floatValue();
        if (organic) {
            OrganicTransfurMaskRenderer.render(
                    graphics,
                    screenWidth,
                    screenHeight,
                    color,
                    progress,
                    opacity,
                    player.tickCount + partialTick);
            return;
        }

        float alpha = progress * opacity;
        int yMargin = Math.round((1.0F - progress) * 20.0F);
        int xMargin = screenHeight <= 0 ? yMargin : Math.round(yMargin * (screenWidth / (float)screenHeight));

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        graphics.setColor(color.red(), color.green(), color.blue(), alpha);
        graphics.blit(
                GOO_OUTLINE,
                -xMargin,
                -yMargin,
                screenWidth + xMargin * 2,
                screenHeight + yMargin * 2,
                0.0F,
                0.0F,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.disableBlend();
    }
}
