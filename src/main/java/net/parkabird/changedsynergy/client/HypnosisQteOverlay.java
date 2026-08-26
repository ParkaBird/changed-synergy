package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.ltxprogrammer.changed.client.gui.GrabOverlay;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.HypnosisQteSyncPacket;

@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class HypnosisQteOverlay {
    private static final ResourceLocation GRAB_PROGRESS_BAR =
            ResourceLocation.fromNamespaceAndPath(
                    "changed", "textures/gui/grab_progress_bar_player.png");
    private static final ResourceLocation GRAB_ESCAPE_KEYS =
            ResourceLocation.fromNamespaceAndPath(
                    "changed", "textures/gui/grab_escape_keys.png");

    private HypnosisQteOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("hypnosis_qte", HypnosisQteOverlay::render);
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        HypnosisQteSyncPacket state = HypnosisQteClientState.current();
        if (state == null) {
            return;
        }

        renderHypnosisVision(graphics, partialTick, screenWidth, screenHeight);
        if (Minecraft.getInstance().options.hideGui) {
            return;
        }

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (state.state() == HypnosisQteSyncPacket.ACTIVE) {
            renderActiveQte(gui, graphics, partialTick, screenWidth, screenHeight, state);
        } else {
            renderResult(
                    gui,
                    graphics,
                    partialTick,
                    screenWidth,
                    screenHeight,
                    state.state());
        }
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderActiveQte(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            HypnosisQteSyncPacket state) {
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int barX = centerX - 100;
        int barY = centerY + 35;
        int themeRgb = HypnosisQteClientState.themeColor();
        Color3 theme = Color3.fromInt(themeRgb);
        Color3 background = Color3.fromInt(scaleRgb(themeRgb, 0.32F));
        boolean animated = QteAnimationUtil.enabled();
        boolean reducedMotion = QteAnimationUtil.reducedMotion();
        float entry = HypnosisQteAnimationState.entryProgress();
        float reveal = animated && !reducedMotion ? entry : 1.0F;

        if (animated) {
            renderFocusReticle(
                    graphics,
                    partialTick,
                    centerX,
                    centerY - 1,
                    themeRgb,
                    state.controlStrength(),
                    entry);
        }

        int halfReveal = Math.max(1, Math.round(100.0F * reveal));
        graphics.enableScissor(
                centerX - halfReveal,
                barY,
                centerX + halfReveal,
                barY + 32);
        GrabOverlay.renderBackground(
                graphics, GRAB_PROGRESS_BAR, barX, barY, 200, 32, background);
        GrabOverlay.renderForeground(
                graphics, GRAB_PROGRESS_BAR, barX, barY, 200, 32,
                Mth.clamp(state.controlStrength(), 0.0F, 1.0F), theme);
        graphics.disableScissor();

        int timeWidth = Math.round(196.0F * Mth.clamp(
                HypnosisQteClientState.ticksRemaining() / 120.0F,
                0.0F,
                1.0F));
        graphics.fill(barX + 2, barY + 30, barX + 2 + Math.max(0, timeWidth),
                barY + 32, withAlpha(theme.toInt(), 190));

        Component title = state.expectedKey() < 0
                ? Component.translatable("overlay.changed_synergy.hypnosis.focus")
                : Component.translatable("overlay.changed_synergy.hypnosis.title");
        float titleAlpha = animated
                ? QteAnimationUtil.smooth((entry - 0.52F) / 0.48F)
                : 1.0F;
        graphics.drawCenteredString(
                gui.getFont(), title, centerX, centerY - 42,
                withAlpha(
                        brightenRgb(themeRgb, 0.48F),
                        Math.round(titleAlpha * 255.0F)));

        int keyX = centerX - 8;
        int keyY = centerY + 20;
        float ticksUnpressed = HypnosisQteClientState.ticksUnpressed() + partialTick;
        KeyReference lastKey = key(state.lastKey());
        if (lastKey != null) {
            if (animated) {
                renderDepartingHypnosisKey(
                        gui,
                        graphics,
                        keyX,
                        keyY - 25,
                        lastKey,
                        ticksUnpressed,
                        themeRgb,
                        entry);
            } else {
                float alpha = Mth.clamp(
                        (12.0F - ticksUnpressed) / 7.0F,
                        0.0F,
                        1.0F);
                renderEscapeKeyAt(
                        gui,
                        graphics,
                        keyX,
                        keyY + animatePreviousKey(ticksUnpressed),
                        lastKey,
                        alpha,
                        themeRgb);
            }
        }

        KeyReference expectedKey = key(state.expectedKey());
        if (expectedKey != null) {
            if (animated) {
                renderArrivingHypnosisKey(
                        gui,
                        graphics,
                        partialTick,
                        keyX,
                        keyY - 25,
                        expectedKey,
                        themeRgb,
                        state.controlStrength(),
                        entry);
            } else {
                renderEscapeKeyAt(
                        gui,
                        graphics,
                        keyX,
                        keyY - 25,
                        expectedKey,
                        1.0F,
                        themeRgb);
            }
        }
    }

    private static void renderFocusReticle(
            GuiGraphics graphics,
            float partialTick,
            int centerX,
            int centerY,
            int theme,
            float controlStrength,
            float entry) {
        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.level == null
                ? 0.0F
                : minecraft.level.getGameTime()) + partialTick;
        boolean reduced = QteAnimationUtil.reducedMotion();
        float pressure = Mth.clamp(controlStrength, 0.0F, 1.0F);
        float ghost = 1.0F - QteAnimationUtil.clamp(entry);

        if (!reduced && ghost > 0.01F) {
            for (int layer = 0; layer < 3; layer++) {
                int size = 27 + layer * 10
                        + Math.round(26.0F * ghost);
                float alpha = ghost * (0.24F - layer * 0.045F);
                graphics.pose().pushPose();
                graphics.pose().translate(centerX, centerY, 0.0F);
                graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                        45.0F + layer * 6.0F));
                drawFrame(
                        graphics,
                        -size,
                        -size,
                        size,
                        size,
                        1,
                        QteAnimationUtil.withAlpha(theme, alpha));
                graphics.pose().popPose();
            }
        }

        int stableSize = 27 + Math.round(pressure * 4.0F)
                + (reduced
                        ? 0
                        : Math.round(Mth.sin(time * 0.16F) * 1.5F));
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, centerY, 0.0F);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                reduced ? 45.0F : 45.0F + time * 0.18F));
        drawFrame(
                graphics,
                -stableSize,
                -stableSize,
                stableSize,
                stableSize,
                1,
                QteAnimationUtil.withAlpha(
                        theme,
                        entry * (0.24F + pressure * 0.24F)));
        graphics.pose().popPose();
    }

    private static void renderDepartingHypnosisKey(
            ForgeGui gui,
            GuiGraphics graphics,
            int x,
            int y,
            KeyReference key,
            float ticksUnpressed,
            int theme,
            float entry) {
        float progress = QteAnimationUtil.clamp(ticksUnpressed / 12.0F);
        float eased = QteAnimationUtil.easeOutCubic(progress);
        int travel = QteAnimationUtil.reducedMotion()
                ? 0
                : Math.round(16.0F * eased);
        int movedX = x + QteAnimationUtil.directionX(key) * travel;
        int movedY = y + QteAnimationUtil.directionY(key) * travel;
        float alpha = 1.0F - QteAnimationUtil.smooth(
                (progress - 0.12F) / 0.88F);
        float scale = QteAnimationUtil.reducedMotion()
                ? 1.0F
                : progress < 0.2F
                        ? Mth.lerp(progress / 0.2F, 1.0F, 0.86F)
                        : Mth.lerp(
                                (progress - 0.2F) / 0.8F,
                                0.86F,
                                1.10F);
        renderKeyScaled(
                gui,
                graphics,
                movedX,
                movedY,
                key,
                scale,
                alpha * entry,
                theme);

        int ripple = 10 + Math.round(17.0F * eased);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8, y + 8, 0.0F);
        graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45.0F));
        drawFrame(
                graphics,
                -ripple,
                -ripple,
                ripple,
                ripple,
                1,
                QteAnimationUtil.withAlpha(
                        theme,
                        alpha * entry * 0.42F));
        graphics.pose().popPose();
    }

    private static void renderArrivingHypnosisKey(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int x,
            int y,
            KeyReference key,
            int theme,
            float controlStrength,
            float entry) {
        float keyProgress = HypnosisQteAnimationState.keyProgress();
        float settled = QteAnimationUtil.clamp(keyProgress);
        float ghost = 1.0F - settled;
        if (!QteAnimationUtil.reducedMotion() && ghost > 0.02F) {
            int distance = Math.round(18.0F * ghost);
            int[][] offsets = {
                {-distance, 0},
                {distance, 0},
                {0, -distance},
                {0, distance}
            };
            for (int[] offset : offsets) {
                renderEscapeKeyAt(
                        gui,
                        graphics,
                        x + offset[0],
                        y + offset[1],
                        key,
                        ghost * entry * 0.22F,
                        theme);
            }
        }

        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.level == null
                ? 0.0F
                : minecraft.level.getGameTime()) + partialTick;
        float pressure = Mth.clamp(controlStrength, 0.0F, 1.0F);
        float pulse = QteAnimationUtil.reducedMotion()
                ? 1.0F
                : 1.0F + Mth.sin(time * 0.22F)
                        * (0.010F + pressure * 0.018F);
        float scale = (0.82F + 0.18F * keyProgress) * pulse;
        renderKeyScaled(
                gui,
                graphics,
                x,
                y,
                key,
                scale,
                settled * entry,
                theme);
    }

    private static void renderResult(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            int state) {
        Component title = switch (state) {
            case HypnosisQteSyncPacket.SUCCESS ->
                    Component.translatable("overlay.changed_synergy.hypnosis.success");
            case HypnosisQteSyncPacket.FAILED ->
                    Component.translatable("overlay.changed_synergy.hypnosis.failed");
            case HypnosisQteSyncPacket.INTERRUPTED ->
                    Component.translatable("overlay.changed_synergy.hypnosis.interrupted");
            default -> Component.empty();
        };
        int color = withAlpha(
                brightenRgb(HypnosisQteClientState.themeColor(), 0.48F), 255);
        if (!QteAnimationUtil.enabled()) {
            graphics.drawCenteredString(
                    gui.getFont(),
                    title,
                    screenWidth / 2,
                    screenHeight / 2 + 16,
                    color);
            return;
        }

        float progress = HypnosisQteAnimationState.resultProgress();
        float eased = QteAnimationUtil.easeOutCubic(progress);
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2 + 16;
        int theme = HypnosisQteClientState.themeColor();
        boolean reduced = QteAnimationUtil.reducedMotion();

        if (state == HypnosisQteSyncPacket.SUCCESS) {
            int radius = 12 + (reduced ? 0 : Math.round(54.0F * eased));
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(45.0F));
            drawFrame(
                    graphics,
                    -radius,
                    -radius,
                    radius,
                    radius,
                    1,
                    QteAnimationUtil.withAlpha(
                            brightenRgb(theme, 0.55F),
                            (1.0F - progress) * 0.72F));
            graphics.pose().popPose();
            drawCenteredScaledText(
                    gui,
                    graphics,
                    title,
                    centerX,
                    centerY,
                    0.84F + 0.16F * QteAnimationUtil.easeOutBack(progress),
                    withAlpha(
                            brightenRgb(theme, 0.62F),
                            Math.round(QteAnimationUtil.smooth(
                                    progress / 0.30F) * 255.0F)));
            return;
        }

        if (state == HypnosisQteSyncPacket.FAILED) {
            int radius = 58 - (reduced ? 0 : Math.round(44.0F * eased));
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                    reduced ? 45.0F : 45.0F - eased * 26.0F));
            drawFrame(
                    graphics,
                    -radius,
                    -radius,
                    radius,
                    radius,
                    2,
                    QteAnimationUtil.withAlpha(
                            theme,
                            0.58F - progress * 0.25F));
            graphics.pose().popPose();
            int sink = reduced ? 0 : Math.round(6.0F * eased);
            int ghostAlpha = Math.round(progress * 70.0F);
            if (!reduced && ghostAlpha > 3) {
                graphics.drawCenteredString(
                        gui.getFont(),
                        title,
                        centerX - 2,
                        centerY + sink,
                        withAlpha(theme, ghostAlpha));
                graphics.drawCenteredString(
                        gui.getFont(),
                        title,
                        centerX + 2,
                        centerY + sink,
                        withAlpha(theme, ghostAlpha));
            }
            drawCenteredScaledText(
                    gui,
                    graphics,
                    title,
                    centerX,
                    centerY + sink,
                    reduced ? 1.0F : 1.0F - 0.05F * eased,
                    withAlpha(
                            brightenRgb(theme, 0.45F),
                            Math.round(QteAnimationUtil.smooth(
                                    progress / 0.28F) * 255.0F)));
            return;
        }

        int offset = reduced ? 0 : Math.round(18.0F * eased);
        int frameAlpha = Math.round((1.0F - progress) * 130.0F);
        drawFrame(
                graphics,
                centerX - 30 - offset,
                centerY - 13,
                centerX - 4 - offset,
                centerY + 13,
                1,
                withAlpha(theme, frameAlpha));
        drawFrame(
                graphics,
                centerX + 4 + offset,
                centerY - 13,
                centerX + 30 + offset,
                centerY + 13,
                1,
                withAlpha(theme, frameAlpha));
        drawCenteredScaledText(
                gui,
                graphics,
                title,
                centerX,
                centerY,
                1.0F,
                withAlpha(
                        brightenRgb(theme, 0.50F),
                        Math.round(QteAnimationUtil.smooth(
                                progress / 0.32F) * 255.0F)));
    }

    private static void renderHypnosisVision(
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        float intensity = HypnosisQteClientState.visualIntensity(partialTick);
        HypnosisQteSyncPacket state = HypnosisQteClientState.current();
        boolean animated = QteAnimationUtil.enabled();
        boolean reducedMotion = animated && QteAnimationUtil.reducedMotion();
        if (animated
                && state != null
                && state.state() == HypnosisQteSyncPacket.ACTIVE) {
            intensity *= HypnosisQteAnimationState.entryProgress();
        }
        if (intensity <= 0.01F) {
            return;
        }
        int theme = HypnosisQteClientState.themeColor();
        if (state != null && state.state() == HypnosisQteSyncPacket.FAILED) {
            renderMesmerizedVision(
                    graphics, partialTick, screenWidth, screenHeight, theme, intensity);
            return;
        }
        graphics.fill(0, 0, screenWidth, screenHeight,
                withAlpha(theme, Math.round(22.0F * intensity)));

        int edge = Math.max(18, Math.min(screenWidth, screenHeight) / 7);
        for (int layer = 0; layer < 6; layer++) {
            int inset = layer * edge / 6;
            int thickness = Math.max(2, edge / 6);
            int alpha = Math.round((62.0F - layer * 8.0F) * intensity);
            int dark = withAlpha(scaleRgb(theme, 0.08F), alpha);
            graphics.fill(inset, inset, screenWidth - inset, inset + thickness, dark);
            graphics.fill(inset, screenHeight - inset - thickness,
                    screenWidth - inset, screenHeight - inset, dark);
            graphics.fill(inset, inset + thickness, inset + thickness,
                    screenHeight - inset - thickness, dark);
            graphics.fill(screenWidth - inset - thickness, inset + thickness,
                    screenWidth - inset, screenHeight - inset - thickness, dark);
        }

        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.level == null ? 0.0F : minecraft.level.getGameTime()) + partialTick;
        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int maxSize = Math.max(72, Math.min(screenWidth, screenHeight) / 2);
        for (int index = 0; index < 5; index++) {
            float phase = reducedMotion
                    ? index * maxSize / 5.0F
                    : (time * 0.75F + index * maxSize / 5.0F) % maxSize;
            int size = 18 + Math.round(phase);
            float fade = 1.0F - phase / maxSize;
            int alpha = Math.round(70.0F * intensity * fade);
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                    reducedMotion ? 45.0F : 45.0F + time * 0.35F));
            drawFrame(graphics, -size, -size, size, size, 2, withAlpha(theme, alpha));
            graphics.pose().popPose();
        }

        int shimmerAlpha = Math.round(30.0F * intensity
                * (reducedMotion
                        ? 0.72F
                        : 0.5F + 0.5F * Mth.sin(time * 0.18F)));
        graphics.fill(0, centerY - 1, screenWidth, centerY + 1,
                withAlpha(theme, shimmerAlpha));
    }

    /** A slower, heavier pulse distinguishes Mesmerized from the active gaze. */
    private static void renderMesmerizedVision(
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            int theme,
            float intensity) {
        Minecraft minecraft = Minecraft.getInstance();
        float time = (minecraft.level == null ? 0.0F : minecraft.level.getGameTime())
                + partialTick;
        boolean reducedMotion = QteAnimationUtil.enabled()
                && QteAnimationUtil.reducedMotion();
        float pulse = reducedMotion
                ? 0.78F
                : 0.62F + 0.38F * Mth.sin(time * 0.13F);
        graphics.fill(0, 0, screenWidth, screenHeight,
                withAlpha(theme, Math.round((30.0F + 16.0F * pulse) * intensity)));

        int edge = Math.max(24, Math.min(screenWidth, screenHeight) / 5);
        for (int layer = 0; layer < 8; layer++) {
            int inset = layer * edge / 8;
            int thickness = Math.max(2, edge / 7);
            int alpha = Math.round((82.0F - layer * 8.0F) * intensity);
            int shadow = withAlpha(scaleRgb(theme, 0.045F), alpha);
            graphics.fill(inset, inset, screenWidth - inset, inset + thickness, shadow);
            graphics.fill(inset, screenHeight - inset - thickness,
                    screenWidth - inset, screenHeight - inset, shadow);
            graphics.fill(inset, inset + thickness, inset + thickness,
                    screenHeight - inset - thickness, shadow);
            graphics.fill(screenWidth - inset - thickness, inset + thickness,
                    screenWidth - inset, screenHeight - inset - thickness, shadow);
        }

        int driftSpan = Math.max(1, screenWidth + 180);
        for (int band = 0; band < 6; band++) {
            int x = Math.floorMod(
                    Math.round((reducedMotion ? 0.0F : time * 2.2F)
                            + band * driftSpan / 6.0F), driftSpan) - 90;
            int width = 24 + band * 5;
            int alpha = Math.round((8.0F + 5.0F * pulse) * intensity);
            graphics.fill(x, 0, x + width, screenHeight, withAlpha(theme, alpha));
        }

        int centerX = screenWidth / 2;
        int centerY = screenHeight / 2;
        int baseSize = Math.max(44, Math.min(screenWidth, screenHeight) / 7);
        for (int ring = 0; ring < 3; ring++) {
            int size = baseSize + ring * baseSize / 2
                    + (reducedMotion
                            ? 0
                            : Math.round(5.0F * Mth.sin(time * 0.11F + ring)));
            int alpha = Math.round((45.0F - ring * 10.0F) * intensity);
            graphics.pose().pushPose();
            graphics.pose().translate(centerX, centerY, 0.0F);
            graphics.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(
                    reducedMotion
                            ? 45.0F + ring * 30.0F
                            : -time * 0.22F + ring * 30.0F));
            drawFrame(graphics, -size, -size, size, size, 2,
                    withAlpha(brightenRgb(theme, 0.18F), alpha));
            graphics.pose().popPose();
        }
    }

    private static void renderKeyScaled(
            ForgeGui gui,
            GuiGraphics graphics,
            int x,
            int y,
            KeyReference key,
            float scale,
            float alpha,
            int theme) {
        if (alpha <= 0.02F) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8.0F, y + 8.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-(x + 8.0F), -(y + 8.0F), 0.0F);
        renderEscapeKeyAt(
                gui,
                graphics,
                x,
                y,
                key,
                QteAnimationUtil.clamp(alpha),
                theme);
        graphics.pose().popPose();
    }

    private static void drawCenteredScaledText(
            ForgeGui gui,
            GuiGraphics graphics,
            Component text,
            int centerX,
            int y,
            float scale,
            int color) {
        Font font = gui.getFont();
        int width = font.width(text);
        graphics.pose().pushPose();
        graphics.pose().translate(centerX, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, -width / 2, 0, color, true);
        graphics.pose().popPose();
    }

    private static void drawFrame(
            GuiGraphics graphics,
            int left,
            int top,
            int right,
            int bottom,
            int thickness,
            int color) {
        graphics.fill(left, top, right, top + thickness, color);
        graphics.fill(left, bottom - thickness, right, bottom, color);
        graphics.fill(left, top + thickness, left + thickness, bottom - thickness, color);
        graphics.fill(right - thickness, top + thickness, right, bottom - thickness, color);
    }

    private static void renderEscapeKeyAt(
            ForgeGui gui,
            GuiGraphics graphics,
            int x,
            int y,
            KeyReference key,
            float alpha,
            int theme) {
        if (alpha <= 0.05F) {
            return;
        }
        int keyX = switch (key) {
            case MOVE_FORWARD, MOVE_LEFT -> 0;
            case MOVE_RIGHT, MOVE_BACKWARD -> 16;
            default -> 0;
        };
        int keyY = switch (key) {
            case MOVE_BACKWARD, MOVE_LEFT -> 16;
            default -> 0;
        };
        graphics.setColor(
                channel(theme, 16), channel(theme, 8), channel(theme, 0), alpha);
        graphics.blit(GRAB_ESCAPE_KEYS, x, y, keyX, keyY, 16, 16, 32, 32);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        String keyName = key.getName(Minecraft.getInstance().level)
                .getString().toUpperCase(Locale.ROOT);
        Font font = gui.getFont();
        int keyWidth = font.width(keyName);
        graphics.drawString(
                font,
                keyName,
                x + 8 - keyWidth / 2,
                y + 5,
                withAlpha(brightenRgb(theme, 0.62F), Math.round(alpha * 255.0F)),
                false);
    }

    private static int animatePreviousKey(float ticksUnpressed) {
        float progress = Mth.clamp(ticksUnpressed / 15.0F, 0.0F, 1.0F);
        float remaining = 1.0F - progress;
        return Math.round(25.0F - 50.0F * remaining * remaining * remaining);
    }

    private static KeyReference key(int index) {
        return switch (index) {
            case 0 -> KeyReference.MOVE_FORWARD;
            case 1 -> KeyReference.MOVE_BACKWARD;
            case 2 -> KeyReference.MOVE_LEFT;
            case 3 -> KeyReference.MOVE_RIGHT;
            default -> null;
        };
    }

    private static int withAlpha(int rgb, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }

    private static int scaleRgb(int rgb, float scale) {
        int red = Math.round(((rgb >> 16) & 0xFF) * scale);
        int green = Math.round(((rgb >> 8) & 0xFF) * scale);
        int blue = Math.round((rgb & 0xFF) * scale);
        return red << 16 | green << 8 | blue;
    }

    private static int brightenRgb(int rgb, float amount) {
        int red = Mth.lerpInt(amount, (rgb >> 16) & 0xFF, 255);
        int green = Mth.lerpInt(amount, (rgb >> 8) & 0xFF, 255);
        int blue = Mth.lerpInt(amount, rgb & 0xFF, 255);
        return red << 16 | green << 8 | blue;
    }

    private static float channel(int rgb, int shift) {
        return ((rgb >> shift) & 0xFF) / 255.0F;
    }
}
