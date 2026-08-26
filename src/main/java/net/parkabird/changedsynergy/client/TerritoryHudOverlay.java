package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.TerritorySyncPacket;

/** Persistent facility readout plus short biome-entry subtitles. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class TerritoryHudOverlay {
    private TerritoryHudOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(
                "territory_hud",
                TerritoryHudOverlay::render);
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ChangedSynergyClientConfig.CLIENT.territoryHud.get()
                || minecraft.options.hideGui
                || minecraft.player == null) {
            return;
        }
        TerritorySyncPacket state = TerritoryClientState.current();
        if (state == null) {
            return;
        }
        if (state.facility()) {
            renderFacility(
                    minecraft.font,
                    graphics,
                    state);
        }
        if (TerritoryClientState.subtitleActive()) {
            renderSubtitle(
                    minecraft.font,
                    graphics,
                    screenWidth,
                    screenHeight);
        }
    }

    private static void renderFacility(
            Font font,
            GuiGraphics graphics,
            TerritorySyncPacket state) {
        Component site = Component.translatable(
                "overlay.changed_synergy.facility.site",
                state.facilityCode());
        Component zone = TerritoryClientState.zoneName(state.zoneId());
        int width = Math.max(font.width(site), font.width(zone)) + 14;
        int accent = TerritoryClientState.color(state.factionOrdinal());
        graphics.fill(6, 6, 6 + width, 33, 0x98090C12);
        graphics.fill(6, 6, 9, 33, 0xFF000000 | accent);
        graphics.drawString(font, site, 13, 10, 0xFFE7EBF0, true);
        graphics.drawString(font, zone, 13, 22, 0xFF000000 | accent, true);
    }

    private static void renderSubtitle(
            Font font,
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight) {
        Component title = TerritoryClientState.subtitleTitle();
        Component detail = TerritoryClientState.subtitleDetail();
        Component incomingTitle = TerritoryClientState.incomingTitle();
        Component incomingDetail = TerritoryClientState.incomingDetail();
        boolean transitioning = TerritoryClientState.subtitleTransitioning();

        int titleWidth = font.width(title);
        int detailWidth = font.width(detail);
        int outgoingPanelWidth = Math.max(titleWidth, detailWidth) + 24;
        int incomingTitleWidth = font.width(incomingTitle);
        int incomingDetailWidth = font.width(incomingDetail);
        int incomingPanelWidth = Math.max(
                incomingTitleWidth,
                incomingDetailWidth) + 24;
        float transitionProgress = TerritoryClientState
                .contentTransitionProgress();
        int panelWidth = transitioning
                ? Math.round(Mth.lerp(
                        transitionProgress,
                        outgoingPanelWidth,
                        incomingPanelWidth))
                : outgoingPanelWidth;
        int revealedWidth = Math.max(
                1,
                Math.round(panelWidth
                        * TerritoryClientState.panelExpansion()));
        int left = (screenWidth - revealedWidth) / 2;
        int right = left + revealedWidth;
        int y = Math.max(42, (int)(screenHeight * 0.22F));

        float backgroundAlpha = TerritoryClientState.backgroundAlpha();
        int panelAlpha = Mth.clamp(
                Math.round(backgroundAlpha * 150.0F), 0, 150);
        int ornamentAlpha = Mth.clamp(
                Math.round(backgroundAlpha * 255.0F), 0, 255);
        int ornament = TerritoryClientState.subtitleOrnamentColor();
        graphics.fill(
                left,
                y - 7,
                right,
                y + 25,
                panelAlpha << 24 | 0x081018);
        graphics.fill(
                left,
                y - 7,
                right,
                y - 5,
                ornamentAlpha << 24 | ornament);

        int factionOrdinal = TerritoryClientState.subtitleFactionOrdinal();
        int titleColor = TerritoryClientState.color(factionOrdinal);
        int detailColor = TerritoryClientState.factionStandingTextColor(
                TerritoryClientState.subtitleReputationScore());
        drawSubtitleText(
                font,
                graphics,
                title,
                titleWidth,
                screenWidth,
                y,
                titleColor,
                TerritoryClientState.outgoingTextAlpha());
        if (!detail.getString().isBlank()) {
            drawSubtitleText(
                    font,
                    graphics,
                    detail,
                    detailWidth,
                    screenWidth,
                    y + 13,
                    detailColor,
                    TerritoryClientState.outgoingTextAlpha());
        }

        if (transitioning) {
            int incomingFaction = TerritoryClientState
                    .incomingFactionOrdinal();
            int incomingTitleColor = TerritoryClientState.color(
                    incomingFaction);
            int incomingDetailColor = TerritoryClientState
                    .factionStandingTextColor(
                    TerritoryClientState.incomingReputationScore());
            float incomingAlpha = TerritoryClientState
                    .incomingTextAlpha();
            drawSubtitleText(
                    font,
                    graphics,
                    incomingTitle,
                    incomingTitleWidth,
                    screenWidth,
                    y,
                    incomingTitleColor,
                    incomingAlpha);
            if (!incomingDetail.getString().isBlank()) {
                drawSubtitleText(
                        font,
                        graphics,
                        incomingDetail,
                        incomingDetailWidth,
                        screenWidth,
                        y + 13,
                        incomingDetailColor,
                        incomingAlpha);
            }
        }
    }

    private static void drawSubtitleText(
            Font font,
            GuiGraphics graphics,
            Component text,
            int width,
            int screenWidth,
            int y,
            int color,
            float alpha) {
        int alphaByte = Mth.clamp(
                Math.round(alpha * 255.0F), 0, 255);
        if (alphaByte <= 3) {
            return;
        }
        graphics.drawString(
                font,
                text,
                (screenWidth - width) / 2,
                y,
                alphaByte << 24 | color,
                true);
    }
}
