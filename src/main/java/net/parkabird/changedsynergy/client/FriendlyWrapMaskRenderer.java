package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * Texture-free friendly-wrap veil. A single continuous aperture surrounds the
 * view, so wide screens never expose the overlapping seams of four corner
 * sprites. Slow contour waves keep the enclosing layer visibly alive.
 */
public final class FriendlyWrapMaskRenderer {
    private static final int SEGMENTS = 128;

    private FriendlyWrapMaskRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            Color3 theme,
            float opacity,
            float animationTime) {
        if (screenWidth <= 0 || screenHeight <= 0 || opacity <= 0.0F) {
            return;
        }

        float alpha = Mth.clamp(opacity * 1.24F, 0.0F, 1.0F);
        Color3 soft = theme.add(0.10F).clamp();
        Color3 highlight = theme.add(0.30F).clamp();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        renderContinuousVeil(
                graphics.pose().last().pose(),
                screenWidth,
                screenHeight,
                soft,
                highlight,
                alpha,
                animationTime);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderContinuousVeil(
            Matrix4f matrix,
            int width,
            int height,
            Color3 soft,
            Color3 highlight,
            float opacity,
            float time) {
        float centerX = width * 0.5F;
        float centerY = height * 0.5F;
        float breath = Mth.sin(time * 0.048F) * 0.004F;
        // Keep the centre and the four screen edges clear.  The elliptical
        // opening now reaches beyond the viewport, leaving the wrap visible
        // only where the ellipse falls away at the four corners.
        float radiusX = width * (0.540F + breath);
        float radiusY = height * (0.535F + breath * 0.72F);
        float rimWidth = Math.max(2.0F, Math.min(width, height) * 0.011F);
        float farRadius = (float)Math.hypot(width, height) * 1.2F;

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int index = 0; index < SEGMENTS; index++) {
            float angleA = Mth.TWO_PI * index / SEGMENTS;
            float angleB = Mth.TWO_PI * (index + 1) / SEGMENTS;
            Point innerA = aperturePoint(
                    centerX, centerY, radiusX, radiusY, angleA, time);
            Point innerB = aperturePoint(
                    centerX, centerY, radiusX, radiusY, angleB, time);
            Point rimA = offsetOutward(innerA, angleA, rimWidth);
            Point rimB = offsetOutward(innerB, angleB, rimWidth);
            Point edgeA = edgePoint(
                    centerX, centerY, width, height, angleA, rimA);
            Point edgeB = edgePoint(
                    centerX, centerY, width, height, angleB, rimB);
            Point farA = radialPoint(centerX, centerY, farRadius, angleA);
            Point farB = radialPoint(centerX, centerY, farRadius, angleB);

            float flowA = flowAt(angleA, time);
            float flowB = flowAt(angleB, time);
            float innerAlphaA = opacity * (0.055F + flowA * 0.045F);
            float innerAlphaB = opacity * (0.055F + flowB * 0.045F);
            float rimAlphaA = opacity * (0.20F + flowA * 0.13F);
            float rimAlphaB = opacity * (0.20F + flowB * 0.13F);
            float edgeAlpha = opacity * 0.82F;

            quad(
                    builder, matrix,
                    innerA, innerB, rimB, rimA,
                    highlight, innerAlphaA, innerAlphaB,
                    soft, rimAlphaB, rimAlphaA);
            quad(
                    builder, matrix,
                    rimA, rimB, edgeB, edgeA,
                    soft, rimAlphaA, rimAlphaB,
                    Color3.BLACK, edgeAlpha, edgeAlpha);
            quad(
                    builder, matrix,
                    edgeA, edgeB, farB, farA,
                    Color3.BLACK, edgeAlpha, edgeAlpha,
                    Color3.BLACK, edgeAlpha, edgeAlpha);
        }
        tesselator.end();
    }

    private static Point aperturePoint(
            float centerX,
            float centerY,
            float radiusX,
            float radiusY,
            float angle,
            float time) {
        float ripple = 1.0F
                + Mth.sin(angle * 6.0F + time * 0.078F) * 0.017F
                + Mth.sin(angle * 13.0F - time * 0.057F) * 0.009F
                + Mth.sin(angle * 3.0F - time * 0.034F) * 0.005F;
        return new Point(
                centerX + Mth.cos(angle) * radiusX * ripple,
                centerY + Mth.sin(angle) * radiusY * ripple);
    }

    private static float flowAt(float angle, float time) {
        float broad = Mth.sin(angle * 4.0F - time * 0.105F);
        float detail = Mth.sin(angle * 9.0F + time * 0.068F);
        return Mth.clamp(0.5F + broad * 0.34F + detail * 0.16F, 0.0F, 1.0F);
    }

    private static Point offsetOutward(Point point, float angle, float distance) {
        return new Point(
                point.x + Mth.cos(angle) * distance,
                point.y + Mth.sin(angle) * distance);
    }

    private static Point radialPoint(
            float centerX,
            float centerY,
            float radius,
            float angle) {
        return new Point(
                centerX + Mth.cos(angle) * radius,
                centerY + Mth.sin(angle) * radius);
    }

    private static Point edgePoint(
            float centerX,
            float centerY,
            int width,
            int height,
            float angle,
            Point inner) {
        float directionX = Mth.cos(angle);
        float directionY = Mth.sin(angle);
        float horizontal = Math.abs(directionX) < 0.0001F
                ? Float.POSITIVE_INFINITY
                : width * 0.5F / Math.abs(directionX);
        float vertical = Math.abs(directionY) < 0.0001F
                ? Float.POSITIVE_INFINITY
                : height * 0.5F / Math.abs(directionY);
        float boundary = Math.min(horizontal, vertical) + 1.0F;
        float innerDistance = Mth.sqrt(
                Mth.square(inner.x - centerX)
                        + Mth.square(inner.y - centerY));
        return radialPoint(
                centerX,
                centerY,
                Math.max(boundary, innerDistance + 1.0F),
                angle);
    }

    private static void quad(
            BufferBuilder builder,
            Matrix4f matrix,
            Point first,
            Point second,
            Point third,
            Point fourth,
            Color3 innerColor,
            float firstAlpha,
            float secondAlpha,
            Color3 outerColor,
            float thirdAlpha,
            float fourthAlpha) {
        vertex(builder, matrix, first, innerColor, firstAlpha);
        vertex(builder, matrix, second, innerColor, secondAlpha);
        vertex(builder, matrix, third, outerColor, thirdAlpha);
        vertex(builder, matrix, fourth, outerColor, fourthAlpha);
    }

    private static void vertex(
            BufferBuilder builder,
            Matrix4f matrix,
            Point point,
            Color3 color,
            float alpha) {
        builder.vertex(matrix, point.x, point.y, -90.0F)
                .color(
                        color.red(),
                        color.green(),
                        color.blue(),
                        Mth.clamp(alpha, 0.0F, 1.0F))
                .endVertex();
    }

    private record Point(float x, float y) {
    }
}
