package net.parkabird.changedsynergy.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** CJK danmaku and an English-specific, collision-aware speech popup layout. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class TelepathyDanmakuOverlay {
    private static final float ENGLISH_SCALE = 0.86F;
    private static final float DANMAKU_SCALE = 0.94F;
    private static final Map<Long, PopupMotion> POPUP_MOTION = new HashMap<>();

    private TelepathyDanmakuOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("telepathy_danmaku", TelepathyDanmakuOverlay::render);
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!ChangedSynergyClientConfig.CLIENT.telepathicDanmaku.get()
                || minecraft.options.hideGui
                || minecraft.player == null) {
            POPUP_MOTION.clear();
            return;
        }

        Font font = minecraft.font;
        long now = Util.getMillis();
        List<TelepathyDanmakuState.Line> active =
                TelepathyDanmakuState.activeLines();
        int laneSpacing = Math.max(
                font.lineHeight,
                Math.round((font.lineHeight + 6) * DANMAKU_SCALE));
        int baseY = Math.max(32, (int)(screenHeight * 0.14F));
        int upperLimit = Math.max(
                baseY + font.lineHeight,
                (int)(screenHeight * 0.35F) - font.lineHeight);
        int visibleLanes = Math.max(1, (upperLimit - baseY) / laneSpacing + 1);
        List<TelepathyDanmakuState.Line> english = new ArrayList<>();

        for (TelepathyDanmakuState.Line line : active) {
            if (line.englishPopup()) {
                english.add(line);
                continue;
            }
            Component name = Component.literal(
                    line.speaker().getString() + ": ");
            int nameWidth = font.width(name);
            int logicalWidth = nameWidth + font.width(line.dialogue());
            int width = Math.round(logicalWidth * DANMAKU_SCALE);
            double ageSeconds = (now - line.createdAtMillis()) / 1000.0D;
            int x = (int)Math.round(
                    screenWidth + 12.0D - ageSeconds * line.pixelsPerSecond());
            if (x + width < -12) {
                continue;
            }
            int y = baseY + line.lane() % visibleLanes * laneSpacing;
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0.0F);
            graphics.pose().scale(DANMAKU_SCALE, DANMAKU_SCALE, 1.0F);
            graphics.drawString(
                    font,
                    name,
                    0,
                    0,
                    withAlpha(line.accentColor(), 238),
                    true);
            graphics.drawString(
                    font,
                    line.dialogue(),
                    nameWidth,
                    0,
                    withAlpha(0xF1F3F5, 220),
                    true);
            graphics.pose().popPose();
        }
        renderEnglishPopups(
                graphics, font, english, now, screenWidth, screenHeight);
    }

    private static void renderEnglishPopups(
            GuiGraphics graphics,
            Font font,
            List<TelepathyDanmakuState.Line> lines,
            long now,
            int screenWidth,
            int screenHeight) {
        if (lines.isEmpty()) {
            POPUP_MOTION.clear();
            return;
        }

        int wrapWidth = Mth.clamp(
                Math.round(screenWidth * 0.43F / ENGLISH_SCALE),
                280,
                520);
        List<PopupLayout> layouts = new ArrayList<>();
        Set<Long> activeIds = new HashSet<>();
        for (int index = lines.size() - 1; index >= 0; index--) {
            TelepathyDanmakuState.Line line = lines.get(index);
            List<FormattedCharSequence> all = font.split(line.dialogue(), wrapWidth);
            int count = Math.min(4, all.size());
            if (count == 0) {
                continue;
            }
            List<FormattedCharSequence> wrapped = List.copyOf(all.subList(0, count));
            Component name = Component.literal(line.speaker().getString() + ":");
            int logicalWidth = font.width(name);
            for (FormattedCharSequence bodyLine : wrapped) {
                logicalWidth = Math.max(logicalWidth, font.width(bodyLine));
            }
            float width = logicalWidth * ENGLISH_SCALE + 4.0F;
            float height = ((count + 1) * font.lineHeight + 3) * ENGLISH_SCALE;
            PopupMotion motion = POPUP_MOTION.computeIfAbsent(
                    line.id(),
                    id -> createMotion(
                            id, width, height, screenWidth, screenHeight, now));
            layouts.add(new PopupLayout(
                    line, name, wrapped, width, height, motion));
            activeIds.add(line.id());
        }
        POPUP_MOTION.keySet().removeIf(id -> !activeIds.contains(id));

        // Newest is placed first. Only older entries yield when rectangles meet.
        List<Rect> occupied = new ArrayList<>();
        for (PopupLayout layout : layouts) {
            PopupMotion motion = layout.motion();
            Position target = nearestFreePosition(
                    motion.anchorX,
                    motion.anchorY,
                    layout.width(),
                    layout.height(),
                    occupied,
                    screenWidth,
                    screenHeight);
            motion.targetX = target.x();
            motion.targetY = target.y();
            occupied.add(new Rect(
                    target.x(), target.y(), layout.width(), layout.height()));
        }

        for (int index = layouts.size() - 1; index >= 0; index--) {
            PopupLayout layout = layouts.get(index);
            updateMotion(layout.motion(), now);
            renderPopup(graphics, font, layout, now);
        }
    }

    private static PopupMotion createMotion(
            long id,
            float width,
            float height,
            int screenWidth,
            int screenHeight,
            long now) {
        long first = mix(id * 0x9E3779B97F4A7C15L);
        long second = mix(first + 0x632BE59BD9B4E019L);
        float minX = 22.0F;
        float maxX = Math.max(minX, screenWidth - width - 22.0F);
        float minY = Math.max(20.0F, screenHeight * 0.025F);
        float maxY = Math.max(minY, screenHeight * 0.36F - height);
        float anchorX = Mth.lerp(unit(first), minX, maxX);
        float anchorY = Mth.lerp(unit(second), minY, maxY);
        if (anchorX + width > screenWidth * 0.82F
                && anchorY < screenHeight * 0.24F) {
            anchorY = Math.min(maxY, anchorY + screenHeight * 0.10F);
        }
        return new PopupMotion(
                anchorX,
                anchorY,
                anchorX,
                Math.min(maxY, anchorY + 7.0F),
                now);
    }

    private static Position nearestFreePosition(
            float anchorX,
            float anchorY,
            float width,
            float height,
            List<Rect> occupied,
            int screenWidth,
            int screenHeight) {
        float minX = 18.0F;
        float maxX = Math.max(minX, screenWidth - width - 18.0F);
        float minY = Math.max(18.0F, screenHeight * 0.02F);
        float maxY = Math.max(minY, screenHeight * 0.37F - height);
        Position best = new Position(
                Mth.clamp(anchorX, minX, maxX),
                Mth.clamp(anchorY, minY, maxY));
        double bestPenalty = penalty(
                best.x(), best.y(), width, height,
                anchorX, anchorY, occupied);

        for (int ring = 1; ring <= 6; ring++) {
            for (int step = 0; step < 12; step++) {
                double angle = step * Math.PI * 2.0D / 12.0D;
                float x = Mth.clamp(
                        anchorX + (float)Math.cos(angle) * ring * 34.0F,
                        minX,
                        maxX);
                float y = Mth.clamp(
                        anchorY + (float)Math.sin(angle)
                                * ring * (height * 0.45F + 8.0F),
                        minY,
                        maxY);
                double candidatePenalty = penalty(
                        x, y, width, height,
                        anchorX, anchorY, occupied);
                if (candidatePenalty < bestPenalty) {
                    bestPenalty = candidatePenalty;
                    best = new Position(x, y);
                }
            }
        }
        return best;
    }

    private static double penalty(
            float x,
            float y,
            float width,
            float height,
            float anchorX,
            float anchorY,
            List<Rect> occupied) {
        double penalty = Math.pow(x - anchorX, 2.0D)
                + Math.pow(y - anchorY, 2.0D);
        Rect candidate = new Rect(x, y, width, height);
        for (Rect other : occupied) {
            float overlapX = Math.max(0.0F,
                    Math.min(candidate.right(), other.right())
                            - Math.max(candidate.x(), other.x()) + 8.0F);
            float overlapY = Math.max(0.0F,
                    Math.min(candidate.bottom(), other.bottom())
                            - Math.max(candidate.y(), other.y()) + 5.0F);
            penalty += overlapX * overlapY * 2_500.0D;
        }
        return penalty;
    }

    private static void updateMotion(PopupMotion motion, long now) {
        long elapsed = Math.max(0L, Math.min(100L, now - motion.lastUpdate));
        float blend = 1.0F - (float)Math.exp(-elapsed / 190.0D);
        motion.x = Mth.lerp(blend, motion.x, motion.targetX);
        motion.y = Mth.lerp(blend, motion.y, motion.targetY);
        motion.lastUpdate = now;
    }

    private static void renderPopup(
            GuiGraphics graphics,
            Font font,
            PopupLayout layout,
            long now) {
        TelepathyDanmakuState.Line line = layout.line();
        long age = now - line.createdAtMillis();
        long lifetime = line.lifetimeMillis();
        if (age < 0L || age >= lifetime) {
            return;
        }
        float enter = smooth(Mth.clamp(age / 240.0F, 0.0F, 1.0F));
        float exit = smooth(Mth.clamp(
                (age - (lifetime - 720L)) / 720.0F,
                0.0F,
                1.0F));
        float opacity = enter * (1.0F - exit);
        if (opacity <= 0.01F) {
            return;
        }
        int nameAlpha = Mth.clamp(Math.round(opacity * 238.0F), 0, 255);
        int bodyAlpha = Mth.clamp(Math.round(opacity * 210.0F), 0, 255);
        // Font treats an all-zero alpha channel as an unspecified opaque color.
        // Stop drawing before rounding can cross that boundary and flash once.
        if (nameAlpha < 8 || bodyAlpha < 8) {
            return;
        }
        float scale = ENGLISH_SCALE * (0.92F + 0.08F * enter);

        graphics.pose().pushPose();
        graphics.pose().translate(
                layout.motion().x,
                layout.motion().y - exit * 5.0F,
                0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(
                font,
                layout.name(),
                0,
                0,
                withAlpha(line.accentColor(), nameAlpha),
                true);
        int y = font.lineHeight + 2;
        for (FormattedCharSequence bodyLine : layout.body()) {
            graphics.drawString(
                    font,
                    bodyLine,
                    0,
                    y,
                    withAlpha(0xF1F3F5, bodyAlpha),
                    true);
            y += font.lineHeight;
        }
        graphics.pose().popPose();
    }

    private static long mix(long value) {
        value ^= value >>> 30;
        value *= 0xBF58476D1CE4E5B9L;
        value ^= value >>> 27;
        value *= 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private static float unit(long value) {
        return (value >>> 40 & 0xFFFFFFL) / (float)0x1000000L;
    }

    private static int withAlpha(int rgb, int alpha) {
        return Mth.clamp(alpha, 0, 255) << 24 | rgb & 0x00FFFFFF;
    }

    private static float smooth(float value) {
        return value * value * (3.0F - 2.0F * value);
    }

    private static final class PopupMotion {
        private final float anchorX;
        private final float anchorY;
        private float x;
        private float y;
        private float targetX;
        private float targetY;
        private long lastUpdate;

        private PopupMotion(
                float anchorX,
                float anchorY,
                float x,
                float y,
                long lastUpdate) {
            this.anchorX = anchorX;
            this.anchorY = anchorY;
            this.x = x;
            this.y = y;
            this.targetX = anchorX;
            this.targetY = anchorY;
            this.lastUpdate = lastUpdate;
        }
    }

    private record PopupLayout(
            TelepathyDanmakuState.Line line,
            Component name,
            List<FormattedCharSequence> body,
            float width,
            float height,
            PopupMotion motion) {
    }

    private record Position(float x, float y) {
    }

    private record Rect(float x, float y, float width, float height) {
        private float right() {
            return x + width;
        }

        private float bottom() {
            return y + height;
        }
    }
}
