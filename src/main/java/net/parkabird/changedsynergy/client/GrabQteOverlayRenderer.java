package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.ability.AbstractAbility;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance.KeyReference;
import net.ltxprogrammer.changed.ability.GrabEntityAbilityInstance;
import net.ltxprogrammer.changed.client.gui.GrabOverlay;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;

/** Animated presentation for the hostile player-side grab escape QTE. */
public final class GrabQteOverlayRenderer {
    private static final ResourceLocation PROGRESS_BAR =
            ResourceLocation.fromNamespaceAndPath(
                    "changed", "textures/gui/grab_progress_bar_player.png");
    private static final long ENTRY_MILLIS = 300L;
    private static final long KEY_ENTRY_MILLIS = 170L;
    private static final long EXIT_MILLIS = 380L;

    private static GrabEntityAbilityInstance activeAbility;
    private static int activeGrabberId = -1;
    private static int activeTargetId = -1;
    private static long sessionStartedAt;
    private static long keyStartedAt;
    private static long lastFrameAt;
    private static KeyReference trackedKey;
    private static float displayedStrength;
    private static float trailingStrength;
    private static int themeRgb = 0xF0F0F0;

    private static long exitStartedAt;
    private static boolean escapedExit;
    private static float exitStrength;
    private static int exitTheme = 0xF0F0F0;

    private GrabQteOverlayRenderer() {
    }

    /** Called once from Changed's root grab overlay before it draws either side. */
    public static void beginFrame(float partialTick) {
        if (!QteAnimationUtil.enabled()) {
            clearImmediately();
            return;
        }

        long now = QteAnimationUtil.now();
        GrabContext context = findContext();
        if (context == null) {
            if (activeAbility != null) {
                exitStartedAt = now;
                escapedExit = displayedStrength <= 0.08F;
                exitStrength = displayedStrength;
                exitTheme = themeRgb;
            }
            activeAbility = null;
            activeGrabberId = -1;
            activeTargetId = -1;
            trackedKey = null;
            return;
        }

        GrabEntityAbilityInstance ability = context.ability();
        int grabberId = context.grabber().getId();
        int targetId = context.target().getId();
        boolean newSession = activeAbility != ability
                || activeGrabberId != grabberId
                || activeTargetId != targetId;
        if (newSession) {
            activeAbility = ability;
            activeGrabberId = grabberId;
            activeTargetId = targetId;
            sessionStartedAt = now;
            keyStartedAt = now;
            trackedKey = ability.currentEscapeKey;
            displayedStrength = Mth.clamp(
                    ability.getGrabStrength(partialTick), 0.0F, 1.0F);
            trailingStrength = displayedStrength;
            lastFrameAt = now;
            themeRgb = resolveTheme(context.grabber());
            exitStartedAt = 0L;
            return;
        }

        if (trackedKey != ability.currentEscapeKey) {
            trackedKey = ability.currentEscapeKey;
            keyStartedAt = now;
        }
        float targetStrength = Mth.clamp(
                ability.getGrabStrength(partialTick), 0.0F, 1.0F);
        float delta = Mth.clamp((now - lastFrameAt) / 100.0F, 0.0F, 1.0F);
        displayedStrength = Mth.lerp(
                1.0F - (float)Math.pow(0.18F, delta),
                displayedStrength,
                targetStrength);
        trailingStrength = Mth.lerp(
                1.0F - (float)Math.pow(0.52F, delta),
                trailingStrength,
                targetStrength);
        lastFrameAt = now;
        activeAbility = ability;
        themeRgb = resolveTheme(context.grabber());
        exitStartedAt = 0L;
    }

    public static boolean renderProgress(
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        if (!QteAnimationUtil.enabled() || activeAbility == null) {
            return false;
        }

        int centerX = screenWidth / 2;
        int barX = centerX - 100;
        int barY = screenHeight / 2 + 35;
        float entry = QteAnimationUtil.smooth(
                QteAnimationUtil.progress(sessionStartedAt, ENTRY_MILLIS));
        float reveal = QteAnimationUtil.reducedMotion() ? 1.0F : entry;
        int halfReveal = Math.max(1, Math.round(100.0F * reveal));
        Color3 foreground = Color3.fromInt(themeRgb);
        Color3 background = Color3.fromInt(
                QteAnimationUtil.scaleRgb(themeRgb, 0.26F));

        graphics.enableScissor(
                centerX - halfReveal,
                barY,
                centerX + halfReveal,
                barY + 32);
        GrabOverlay.renderBackground(
                graphics, PROGRESS_BAR, barX, barY, 200, 32, background);
        GrabOverlay.renderForeground(
                graphics,
                PROGRESS_BAR,
                barX,
                barY,
                200,
                32,
                displayedStrength,
                foreground);
        GrabOverlay.renderSuit(
                graphics,
                PROGRESS_BAR,
                barX,
                barY,
                200,
                32,
                activeAbility.getSuitTransitionProgress(partialTick),
                foreground);
        graphics.disableScissor();

        int trailX = barX + 2 + Math.round(196.0F * trailingStrength);
        graphics.fill(
                trailX - 1,
                barY + 29,
                trailX + 1,
                barY + 32,
                QteAnimationUtil.withAlpha(
                        QteAnimationUtil.brightenRgb(themeRgb, 0.65F),
                        0.58F * entry));
        renderTensionBrackets(
                graphics,
                barX,
                barY,
                entry,
                displayedStrength,
                themeRgb,
                1.0F);
        return true;
    }

    public static boolean renderKeys(
            Gui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight,
            GrabEntityAbilityInstance ability) {
        if (!QteAnimationUtil.enabled()
                || activeAbility == null
                || ability != activeAbility) {
            return false;
        }

        int centerX = screenWidth / 2;
        int keyX = centerX - 8;
        int keyY = screenHeight / 2 - 5;
        float entry = QteAnimationUtil.smooth(
                QteAnimationUtil.progress(sessionStartedAt, ENTRY_MILLIS));
        float pressure = Mth.clamp(displayedStrength, 0.0F, 1.0F);
        float ticks = ability.ticksUnpressed + partialTick;

        KeyReference previous = ability.lastEscapeKey;
        if (previous != null && ticks < 13.0F) {
            float progress = QteAnimationUtil.clamp(ticks / 12.0F);
            float eased = QteAnimationUtil.easeOutCubic(progress);
            int travel = QteAnimationUtil.reducedMotion()
                    ? 0
                    : Math.round(18.0F * eased);
            int previousX = keyX
                    + QteAnimationUtil.directionX(previous) * travel;
            int previousY = keyY
                    + QteAnimationUtil.directionY(previous) * travel;
            float scale = QteAnimationUtil.reducedMotion()
                    ? 1.0F
                    : progress < 0.22F
                            ? Mth.lerp(progress / 0.22F, 1.0F, 0.82F)
                            : Mth.lerp(
                                    (progress - 0.22F) / 0.78F,
                                    0.82F,
                                    1.14F);
            float alpha = 1.0F - QteAnimationUtil.smooth(
                    (progress - 0.18F) / 0.82F);
            renderKeyScaled(
                    gui,
                    graphics,
                    previousX,
                    previousY,
                    previous,
                    scale,
                    alpha * entry);
            int ripple = 11 + Math.round(13.0F * eased);
            drawFrame(
                    graphics,
                    centerX - ripple,
                    keyY + 8 - ripple,
                    centerX + ripple,
                    keyY + 8 + ripple,
                    1,
                    QteAnimationUtil.withAlpha(
                            themeRgb,
                            alpha * 0.45F * entry));
        }

        KeyReference expected = ability.currentEscapeKey;
        if (expected != null) {
            float keyEntry = QteAnimationUtil.easeOutBack(
                    QteAnimationUtil.progress(
                            keyStartedAt, KEY_ENTRY_MILLIS));
            float time = (QteAnimationUtil.now() % 10_000L) / 1_000.0F;
            float pulse = QteAnimationUtil.reducedMotion()
                    ? 1.0F
                    : 1.0F + Mth.sin(time * 5.2F) * (0.012F + 0.018F * pressure);
            float scale = (0.78F + 0.22F * keyEntry) * pulse;
            int halo = 3 + Math.round(2.0F * pressure);
            drawFrame(
                    graphics,
                    keyX - halo,
                    keyY - halo,
                    keyX + 16 + halo,
                    keyY + 16 + halo,
                    1,
                    QteAnimationUtil.withAlpha(
                            themeRgb,
                            (0.30F + pressure * 0.30F) * entry));
            renderKeyScaled(
                    gui,
                    graphics,
                    keyX,
                    keyY,
                    expected,
                    scale,
                    entry * QteAnimationUtil.clamp(keyEntry));
        }
        return true;
    }

    /** Draws the short release response after Changed stops rendering the QTE. */
    public static void renderExit(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight) {
        if (!QteAnimationUtil.enabled()
                || activeAbility != null
                || exitStartedAt <= 0L) {
            return;
        }
        float raw = QteAnimationUtil.progress(exitStartedAt, EXIT_MILLIS);
        if (raw >= 1.0F) {
            exitStartedAt = 0L;
            return;
        }
        float eased = QteAnimationUtil.easeOutCubic(raw);
        float alpha = 1.0F - QteAnimationUtil.smooth(raw);
        int barX = screenWidth / 2 - 100;
        int barY = screenHeight / 2 + 35;
        int movement = QteAnimationUtil.reducedMotion()
                ? 0
                : Math.round((escapedExit ? 28.0F : -24.0F) * eased);
        renderExitBrackets(
                graphics,
                barX,
                barY,
                movement,
                exitTheme,
                alpha);

        int centerX = screenWidth / 2;
        int radius = 9 + Math.round(escapedExit
                ? 34.0F * eased
                : 12.0F * (1.0F - eased));
        drawFrame(
                graphics,
                centerX - radius,
                barY + 16 - radius / 3,
                centerX + radius,
                barY + 16 + radius / 3,
                1,
                QteAnimationUtil.withAlpha(
                        escapedExit
                                ? QteAnimationUtil.brightenRgb(exitTheme, 0.65F)
                                : QteAnimationUtil.scaleRgb(exitTheme, 0.55F),
                        alpha * 0.65F));
    }

    private static GrabContext findContext() {
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof LivingEntityDataExtension extension)) {
            return null;
        }
        LivingEntity grabber = extension.getGrabbedBy();
        if (grabber == null
                || FriendlySocialHugState.isActive(
                        grabber.getId(), minecraft.player.getId())) {
            return null;
        }
        GrabEntityAbilityInstance ability = AbstractAbility.getAbilityInstance(
                grabber, ChangedAbilities.GRAB_ENTITY_ABILITY.get());
        if (ability == null
                || ability.grabbedEntity != minecraft.player
                || ability.grabbedHasControl) {
            return null;
        }
        return new GrabContext(grabber, minecraft.player, ability);
    }

    /** Prevents a canceled friendly-hug overlay from leaving a hostile QTE tail behind. */
    public static void clearForFriendlyHold() {
        clearImmediately();
    }

    private static void renderTensionBrackets(
            GuiGraphics graphics,
            int barX,
            int barY,
            float entry,
            float pressure,
            int color,
            float alpha) {
        float settle = QteAnimationUtil.reducedMotion()
                ? 1.0F
                : QteAnimationUtil.easeOutBack(entry);
        int approach = Math.round((1.0F - settle) * 24.0F);
        int shake = QteAnimationUtil.reducedMotion()
                ? 0
                : Math.round(Mth.sin(QteAnimationUtil.now() * 0.034F)
                        * Math.max(0.0F, pressure - 0.72F) * 3.0F);
        int argb = QteAnimationUtil.withAlpha(color, alpha * entry);
        drawBracket(graphics, barX - 9 - approach + shake, barY + 2, true, argb);
        drawBracket(graphics, barX + 207 + approach - shake, barY + 2, false, argb);
    }

    private static void renderExitBrackets(
            GuiGraphics graphics,
            int barX,
            int barY,
            int movement,
            int color,
            float alpha) {
        int argb = QteAnimationUtil.withAlpha(color, alpha);
        drawBracket(graphics, barX - 9 - movement, barY + 2, true, argb);
        drawBracket(graphics, barX + 207 + movement, barY + 2, false, argb);
        int lineColor = QteAnimationUtil.withAlpha(color, alpha * 0.35F);
        int width = Math.round(196.0F * Mth.clamp(exitStrength, 0.0F, 1.0F));
        graphics.fill(barX + 2, barY + 29, barX + 2 + width, barY + 31, lineColor);
    }

    private static void drawBracket(
            GuiGraphics graphics,
            int x,
            int y,
            boolean left,
            int color) {
        graphics.fill(x, y, x + 2, y + 28, color);
        if (left) {
            graphics.fill(x, y, x + 8, y + 2, color);
            graphics.fill(x, y + 26, x + 8, y + 28, color);
        } else {
            graphics.fill(x - 6, y, x + 2, y + 2, color);
            graphics.fill(x - 6, y + 26, x + 2, y + 28, color);
        }
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

    private static void renderKeyScaled(
            Gui gui,
            GuiGraphics graphics,
            int x,
            int y,
            KeyReference key,
            float scale,
            float alpha) {
        if (alpha <= 0.02F) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(x + 8.0F, y + 8.0F, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.pose().translate(-(x + 8.0F), -(y + 8.0F), 0.0F);
        GrabOverlay.renderEscapeKeyAt(
                gui, graphics, x, y, key, QteAnimationUtil.clamp(alpha));
        graphics.pose().popPose();
    }

    private static int resolveTheme(LivingEntity grabber) {
        var colors = ChangedEntities.getEntityColor(grabber);
        int primary = colors.getFirst().toInt() & 0x00FFFFFF;
        int secondary = colors.getSecond().toInt() & 0x00FFFFFF;
        int selected = secondary == 0xF0F0F0 ? primary : secondary;
        int brightness = (selected >> 16 & 0xFF)
                + (selected >> 8 & 0xFF)
                + (selected & 0xFF);
        return brightness < 150
                ? QteAnimationUtil.brightenRgb(selected, 0.38F)
                : selected;
    }

    private static void clearImmediately() {
        activeAbility = null;
        activeGrabberId = -1;
        activeTargetId = -1;
        trackedKey = null;
        exitStartedAt = 0L;
    }

    private record GrabContext(
            LivingEntity grabber,
            LivingEntity target,
            GrabEntityAbilityInstance ability) {
    }
}
