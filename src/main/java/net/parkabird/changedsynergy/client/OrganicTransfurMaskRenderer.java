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

/** Texture-free organic assimilation mask with a closing field of view. */
public final class OrganicTransfurMaskRenderer {
    private static final int APERTURE_SEGMENTS = 72;

    private OrganicTransfurMaskRenderer() {
    }

    public static void render(
            GuiGraphics graphics,
            int screenWidth,
            int screenHeight,
            Color3 theme,
            float progress,
            float opacity,
            float animationTime) {
        if (screenWidth <= 0 || screenHeight <= 0 || progress <= 0.0F || opacity <= 0.0F) {
            return;
        }

        float clampedProgress = Mth.clamp(progress, 0.0F, 1.0F);
        float clampedOpacity = Mth.clamp(opacity, 0.0F, 1.0F);
        Color3 rim = theme.add(0.14F).clamp();

        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        Matrix4f matrix = graphics.pose().last().pose();
        renderClosingAperture(
                matrix,
                screenWidth,
                screenHeight,
                rim,
                clampedProgress,
                clampedOpacity,
                animationTime);

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static void renderClosingAperture(
            Matrix4f matrix,
            int screenWidth,
            int screenHeight,
            Color3 rim,
            float progress,
            float opacity,
            float animationTime) {
        float eased = progress * progress * (3.0F - 2.0F * progress);
        float centerX = screenWidth * 0.5F;
        float centerY = screenHeight * 0.5F;
        float radiusX = screenWidth * Mth.lerp(eased, 0.72F, 0.18F);
        float radiusY = screenHeight * Mth.lerp(eased, 0.76F, 0.16F);
        float outerRadius = (float)Math.hypot(screenWidth, screenHeight) * 1.15F;
        float innerAlpha = opacity * Mth.lerp(eased, 0.04F, 0.36F);
        float outerAlpha = opacity * Mth.lerp(eased, 0.12F, 0.98F);

        Tesselator tesselator = Tesselator.getInstance();
        BufferBuilder builder = tesselator.getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int index = 0; index < APERTURE_SEGMENTS; index++) {
            float angleA = Mth.TWO_PI * index / APERTURE_SEGMENTS;
            float angleB = Mth.TWO_PI * (index + 1) / APERTURE_SEGMENTS;
            Point innerA = aperturePoint(
                    centerX, centerY, radiusX, radiusY, angleA, animationTime);
            Point innerB = aperturePoint(
                    centerX, centerY, radiusX, radiusY, angleB, animationTime);
            Point edgeA = edgePoint(
                    centerX, centerY, screenWidth, screenHeight, angleA, innerA);
            Point edgeB = edgePoint(
                    centerX, centerY, screenWidth, screenHeight, angleB, innerB);
            Point farA = new Point(
                    centerX + Mth.cos(angleA) * outerRadius,
                    centerY + Mth.sin(angleA) * outerRadius);
            Point farB = new Point(
                    centerX + Mth.cos(angleB) * outerRadius,
                    centerY + Mth.sin(angleB) * outerRadius);

            vertex(builder, matrix, innerA, rim.red(), rim.green(), rim.blue(), innerAlpha);
            vertex(builder, matrix, edgeA, 0.0F, 0.0F, 0.0F, outerAlpha);
            vertex(builder, matrix, edgeB, 0.0F, 0.0F, 0.0F, outerAlpha);
            vertex(builder, matrix, innerB, rim.red(), rim.green(), rim.blue(), innerAlpha);

            vertex(builder, matrix, edgeA, 0.0F, 0.0F, 0.0F, outerAlpha);
            vertex(builder, matrix, farA, 0.0F, 0.0F, 0.0F, outerAlpha);
            vertex(builder, matrix, farB, 0.0F, 0.0F, 0.0F, outerAlpha);
            vertex(builder, matrix, edgeB, 0.0F, 0.0F, 0.0F, outerAlpha);
        }
        tesselator.end();
    }

    private static Point aperturePoint(
            float centerX,
            float centerY,
            float radiusX,
            float radiusY,
            float angle,
            float animationTime) {
        float ripple = 1.0F
                + Mth.sin(angle * 5.0F + animationTime * 0.45F) * 0.032F
                + Mth.sin(angle * 13.0F - animationTime * 0.23F) * 0.018F;
        return new Point(
                centerX + Mth.cos(angle) * radiusX * ripple,
                centerY + Mth.sin(angle) * radiusY * ripple);
    }

    private static Point edgePoint(
            float centerX,
            float centerY,
            int screenWidth,
            int screenHeight,
            float angle,
            Point inner) {
        float directionX = Mth.cos(angle);
        float directionY = Mth.sin(angle);
        float horizontalDistance = Math.abs(directionX) < 0.0001F
                ? Float.POSITIVE_INFINITY
                : screenWidth * 0.5F / Math.abs(directionX);
        float verticalDistance = Math.abs(directionY) < 0.0001F
                ? Float.POSITIVE_INFINITY
                : screenHeight * 0.5F / Math.abs(directionY);
        float boundaryDistance = Math.min(horizontalDistance, verticalDistance) + 1.0F;
        float innerDistance = Mth.sqrt(
                Mth.square(inner.x - centerX) + Mth.square(inner.y - centerY));
        float distance = Math.max(boundaryDistance, innerDistance + 1.0F);
        return new Point(
                centerX + directionX * distance,
                centerY + directionY * distance);
    }

    private static void vertex(
            BufferBuilder builder,
            Matrix4f matrix,
            Point point,
            float red,
            float green,
            float blue,
            float alpha) {
        builder.vertex(matrix, point.x, point.y, -90.0F)
                .color(red, green, blue, alpha)
                .endVertex();
    }

    private record Point(float x, float y) {
    }
}
