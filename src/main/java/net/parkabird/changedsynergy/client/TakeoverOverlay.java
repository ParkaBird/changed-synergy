package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.ltxprogrammer.changed.item.ExoskeletonItem;
import net.ltxprogrammer.changed.util.EntityUtil;
import net.ltxprogrammer.changed.entity.beast.LatexBenignWolf;
import net.ltxprogrammer.changed.entity.beast.LatexBenignOrca;
import net.parkabird.changedsynergy.network.TakeoverStatePacket;
import net.parkabird.changedsynergy.network.FriendlySocialHugState;
import net.ltxprogrammer.changed.entity.LivingEntityDataExtension;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TakeoverOverlay {
    private static final ResourceLocation RELEASE_BAR = net.ltxprogrammer.changed.Changed.modResource(
            "textures/gui/grab_progress_bar_latex.png");

    private TakeoverOverlay() {}
    @SubscribeEvent public static void register(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("takeover", (gui, g, partial, w, h) -> render(g, partial, w, h));
    }

    @Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
    public static final class OverlayGuard {
        @SubscribeEvent public static void before(RenderGuiOverlayEvent.Pre event) {
            var player = Minecraft.getInstance().player;
            if (event.getOverlay().id().toString().equals("changed:grabbed")
                    && player instanceof LivingEntityDataExtension extension
                    && extension.getGrabbedBy() != null
                    && FriendlySocialHugState.isLocked(
                            extension.getGrabbedBy().getId(), player.getId())) {
                event.setCanceled(true);
                return;
            }
            if (!TakeoverClientState.active()) return;
            String id = event.getOverlay().id().toString();
            if (id.equals("changed:grabbed") || id.equals("changed:ability")
                    || id.equals("changed_synergy:hypnosis_qte")
                    || id.equals("minecraft:experience_bar")
                    || (!TakeoverClientState.normal() && id.equals("changed:variant_blindness"))) {
                event.setCanceled(true);
            }
        }
    }

    private static void render(GuiGraphics g, float partial, int w, int h) {
        var s = TakeoverClientState.current();
        if (s == null) {
            GrabQteOverlayRenderer.endTakeover();
            float wakeFade = TakeoverClientState.fade(partial);
            if (manualExoskeletonVisual()) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                HypnosisQteOverlay.renderTakeoverVision(g, partial, w, h, 0.72F);
                RenderSystem.setShaderColor(1, 1, 1, 1);
            }
            if (wakeFade > 0.0F) blackout(g, w, h, wakeFade,
                    TakeoverClientState.wakeTint());
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float fade = TakeoverClientState.fade(partial);
        if (!TakeoverClientState.normal()
                && ChangedSynergyClientConfig.CLIENT.exoskeletonHypnosisVisual.get())
            rings(g, partial, w, h, 1 - fade);
        if (fade > 0) {
            boolean mergedSleep = TakeoverClientState.normal() && s.outcome() == 1
                    && (s.phase() == TakeoverStatePacket.SLEEPING
                            || s.phase() == TakeoverStatePacket.RELEASING);
            blackout(g, w, h, fade,
                    mergedSleep ? TakeoverClientState.wakeTint() : 0);
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        if (Minecraft.getInstance().options.hideGui) return;
        var font = Minecraft.getInstance().font;
        if (TakeoverClientState.normal()
                && (s.phase() == TakeoverStatePacket.CONTROLLED
                    || s.phase() == TakeoverStatePacket.BORROWED
                    || s.phase() == TakeoverStatePacket.STRUGGLE)) {
            releaseCountdown(g, w, h, s);
        }
        if (s.kind() == TakeoverStatePacket.EXOSKELETON
                && ChangedSynergyClientConfig.CLIENT.exoskeletonHypnosisVisual.get()) {
            exoskeletonLog(g, w, h, s);
        }
        // A recovery hint is useful only after the blackout, not throughout falling asleep.
        if (s.phase() == TakeoverStatePacket.RELEASING && s.outcome() != 2) {
            g.drawCenteredString(font, TakeoverClientState.text("recovering"), w / 2, 38, 0xFFFFFF);
        }
        if (TakeoverClientState.normal() && s.phase() == TakeoverStatePacket.STRUGGLE) {
            GrabQteOverlayRenderer.renderTakeover(g, w, h, s.sessionId(), s.expectedKey(),
                    Math.max(0, s.progress()), Math.max(0, s.qteLength()), s.carrierId());
        } else {
            GrabQteOverlayRenderer.endTakeover();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    private static void releaseCountdown(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            TakeoverStatePacket state) {
        int remaining = TakeoverClientState.releaseRemainingTicks();
        int total = Math.max(1, TakeoverClientState.releaseTotalTicks());
        float progress = Mth.clamp(remaining / (float) total, 0.0F, 1.0F);
        int left = screenWidth / 2 - 91, top = 17;
        var color = net.ltxprogrammer.changed.util.Color3.fromInt(TakeoverScreen.foregroundColor(state));
        var level = Minecraft.getInstance().level;
        if (level != null && level.getEntity(state.carrierId()) instanceof ChangedEntity carrier) {
            var pair = net.ltxprogrammer.changed.init.ChangedEntities.getEntityColor(carrier);
            color = new net.ltxprogrammer.changed.client.gui.AbstractRadialScreen.ColorScheme(
                    pair.getFirst(), pair.getSecond()).setForegroundToBright().foreground();
        }
        // Reuse only Changed's release-bar appearance. The takeover countdown
        // retains its original top-centre position and contextual time label.
        net.ltxprogrammer.changed.client.gui.GrabOverlay.renderBackground(graphics, RELEASE_BAR,
                left, top, 182, 10, net.ltxprogrammer.changed.util.Color3.WHITE);
        net.ltxprogrammer.changed.client.gui.GrabOverlay.renderForeground(graphics, RELEASE_BAR,
                left, top, 182, 10, progress, color);
        net.ltxprogrammer.changed.client.gui.GrabOverlay.renderSuit(graphics, RELEASE_BAR,
                left, top, 182, 10, 1, color);
        graphics.setColor(1, 1, 1, 1);
        int seconds = (remaining + 19) / 20;
        Component label = TakeoverClientState.text(state.outcome() == 2
                        ? "fatal_countdown" : state.outcome() == 1
                                ? "transfur_countdown" : "release_countdown",
                seconds / 60, String.format(java.util.Locale.ROOT, "%02d", seconds % 60));
        graphics.drawCenteredString(Minecraft.getInstance().font, label,
                screenWidth / 2, 6, 0xFFFFFF);
    }

    /** A compact, timed status feed beside the original hypnosis view. */
    private static void exoskeletonLog(GuiGraphics g, int w, int h,
            TakeoverStatePacket state) {
        if (w < 290) return;
        int age = TakeoverClientState.age();
        int lines = Math.min(6, 1 + age / 32);
        int right = w - 9;
        int top = Math.max(42, h / 2 - 52);
        int left = right - 157;
        var font = Minecraft.getInstance().font;
        for (int index = 0; index < lines; index++) {
            int line = Math.max(0, age / 32 - 5) + index;
            String key = switch (line % 10) {
                case 0 -> "log.signal";
                case 1 -> "log.power";
                case 2 -> "log.motor";
                case 3 -> "log.balance";
                case 4 -> "log.input";
                case 5 -> "log.route";
                case 6 -> "log.terrain";
                case 7 -> "log.stride";
                case 8 -> "log.observation";
                default -> "log.control";
            };
            Component message = TakeoverClientState.text("exoskeleton." + key);
            int color = index == lines - 1 ? 0xD2FFF6 : 0x8ABFC4;
            g.drawString(font, font.plainSubstrByWidth(message.getString(), 156),
                    left, top + index * 13, color, true);
        }
        if (state.phase() == TakeoverStatePacket.SLEEPING
                || state.phase() == TakeoverStatePacket.RELEASING) {
            String key = state.phase() == TakeoverStatePacket.SLEEPING
                    ? "exoskeleton.log.sleep" : "exoskeleton.log.release";
            g.drawString(font, TakeoverClientState.text(key),
                    left, top + lines * 13 + 2, 0xD2FFF6, true);
        }
    }

    private static void blackout(GuiGraphics g, int w, int h, float progress, int primaryColor) {
        float p = Mth.clamp(progress, 0, 1);
        // Keep the screen dark while retaining the carrier species' main hue.
        int red = Math.round(((primaryColor >>> 16) & 0xFF) * 0.42F);
        int green = Math.round(((primaryColor >>> 8) & 0xFF) * 0.42F);
        int blue = Math.round((primaryColor & 0xFF) * 0.42F);
        if (p >= 1) {
            g.fill(0, 0, w, h, 0xFF000000 | red << 16 | green << 8 | blue);
            return;
        }
        // Normalized elliptical radius: corners are sqrt(2), center is zero.
        // Move a soft edge inward rather than fading the whole image uniformly.
        float edge = 1.415F - 1.635F * (p * p * (3 - 2 * p));
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        RenderSystem.disableCull();
        RenderSystem.disableDepthTest();
        var matrix = g.pose().last().pose();
        BufferBuilder b = Tesselator.getInstance().getBuilder();
        b.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int band = 0; band < 64; band++) {
            float r0 = band * 1.42F / 64, r1 = (band + 1) * 1.42F / 64;
            float t0 = Mth.clamp((r0 - edge) / 0.22F, 0, 1);
            float t1 = Mth.clamp((r1 - edge) / 0.22F, 0, 1);
            int a0 = Math.round(255 * t0 * t0 * (3 - 2 * t0));
            int a1 = Math.round(255 * t1 * t1 * (3 - 2 * t1));
            if (a1 == 0) continue;
            for (int segment = 0; segment < 96; segment++) {
                double angle0 = segment * Math.PI * 2 / 96;
                double angle1 = (segment + 1) * Math.PI * 2 / 96;
                float x0 = (float) Math.cos(angle0) * w / 2, y0 = (float) Math.sin(angle0) * h / 2;
                float x1 = (float) Math.cos(angle1) * w / 2, y1 = (float) Math.sin(angle1) * h / 2;
                b.vertex(matrix, w / 2F + x0 * r0, h / 2F + y0 * r0, 0).color(red, green, blue, a0).endVertex();
                b.vertex(matrix, w / 2F + x0 * r1, h / 2F + y0 * r1, 0).color(red, green, blue, a1).endVertex();
                b.vertex(matrix, w / 2F + x1 * r1, h / 2F + y1 * r1, 0).color(red, green, blue, a1).endVertex();
                b.vertex(matrix, w / 2F + x1 * r0, h / 2F + y1 * r0, 0).color(red, green, blue, a0).endVertex();
            }
        }
        BufferUploader.drawWithShader(b.end());
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();
    }

    private static void rings(GuiGraphics g, float partial, int w, int h, float visibility) {
        float entry = Mth.clamp((TakeoverClientState.age() + partial) / 30, 0, 1);
        HypnosisQteOverlay.renderTakeoverVision(g, partial, w, h, visibility * entry);
    }

    private static boolean manualExoskeletonVisual() {
        if (!ChangedSynergyClientConfig.CLIENT.exoskeletonHypnosisVisual.get()) return false;
        var player = Minecraft.getInstance().player;
        if (player == null || !(EntityUtil.maybeGetOverlaying(player) instanceof LatexBenignWolf
                || EntityUtil.maybeGetOverlaying(player) instanceof LatexBenignOrca)) return false;
        return AccessorySlots.getForEntity(player)
                .flatMap(slots -> slots.getItem(ChangedAccessorySlots.FULL_BODY.get()))
                .map(stack -> stack.getItem() instanceof ExoskeletonItem<?>)
                .orElse(false);
    }
}
