package net.parkabird.changedsynergy.client.archive;

import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static net.parkabird.changedsynergy.client.archive.ArchiveGeometry.*;

/** Folder stock and bound paper from the Godot reference, using pixel-art textures in Minecraft's GUI pipeline. */
public final class ArchivePaperRenderer implements AutoCloseable {
    public static final double PAPER_W = ArchiveGeometry.PAPER_W, PAPER_H = ArchiveGeometry.PAPER_H;
    public static final ResourceLocation PAPER = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/paper_surface.png");
    public static final ResourceLocation PAPER_BACK = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/paper_back.png");
    public static final ResourceLocation FOLDER_FRONT = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/folder_front.png");
    public static final ResourceLocation FOLDER_FRONT_HOVER = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/folder_front_hover.png");
    public static final ResourceLocation FOLDER_INNER = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/folder_inner.png");
    public static final ResourceLocation FOLDER_BACK = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/folder_back.png");
    public static final ResourceLocation BINDING = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/binding.png");
    public static final ResourceLocation PAGE_STACK = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/page_stack.png");
    public static final ResourceLocation PAGE_SHADOW = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/page_shadow.png");
    private static final ResourceLocation[] DIVIDERS = {
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_priority.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_white.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_dark.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_variant.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_organic.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_aquatic.png")
    };
    private static final ResourceLocation[] DIVIDERS_HOVER = {
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_priority_hover.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_white_hover.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_dark_hover.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_variant_hover.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_organic_hover.png"),
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/gui/origin_archive/divider_aquatic_hover.png")
    };
    private static final int FOLDER = 0xFFDAC189, PAPER_EDGE = 0xFFAAA28C;
    private static final int CAPTURE_WIDTH = 774, CAPTURE_HEIGHT = 1095;
    private static boolean capturing;
    private TextureTarget snapshot;
    private TextureTarget reverseSnapshot;
    private boolean reverseCaptured;
    private int fromCategory = -1, toCategory = -1;
    private boolean turnCaptured;
    private double turnProgress;
    private int hoveredPage = -1;
    private int hoveredTab = -1;
    private boolean folderHovered;
    private record FanLayer(Point[] paper, int tint, boolean emphasized, boolean leftSide) {}
    private final List<FanLayer> liftedPaper = new ArrayList<>();

    public static boolean isCapturing() { return capturing; }
    public static int captureTargetWidth() { return CAPTURE_WIDTH; }
    public static int captureTargetHeight() { return CAPTURE_HEIGHT; }

    public void transition(int from, int to) {
        fromCategory = from; toCategory = to; turnCaptured = false; turnProgress = 0;
    }
    public void finishTransition() { fromCategory = toCategory = -1; turnCaptured = false; turnProgress = 0; }
    public void progress(double progress) { turnProgress = Math.max(0, Math.min(1, progress)); }
    public void hoverPage(int page) { hoveredPage = page; }
    public void hoverTab(int tab) { hoveredTab = tab; }
    public void hoverFolder(boolean hovered) { folderHovered = hovered; }

    /** Tab hit rectangles and divider ears share this geometry, including the priority tab. */
    public static double[] tab(int index) {
        return new double[]{PAPER_W, index == 0 ? 82 : 300 + (index - 1) * 142,
                index == 0 ? 58 : 54, index == 0 ? 146 : 120};
    }

    /** Call with a paper-space pose: x=0 is the stationary spine, y=0 the upper paper edge. */
    public void shell(GuiGraphics graphics, double openProgress, int category, int[] colors,
                      int page, int pageCount, boolean detail, int leftAvailable, double fanLeft, double fanRight) {
        graphics.flush();
        liftedPaper.clear();
        // Pixel-art drop shadow and rear cover board (with spine-side top ear).
        spriteQuad(graphics, -16, -18, PAPER_W + 76, PAPER_H + 108, 0, 0, 0.5, 1.0, 0x88FFFFFF, textureId(PAGE_SHADOW));
        spriteQuad(graphics, -24, -46, PAPER_W + 68, PAPER_H + 100, 0, 0, 1.0, 1.0, 0xFFFFFFFF, textureId(FOLDER_BACK));
        double leafScale = Math.cos(Math.PI * smooth((openProgress - .65) / .35));
        for (int index = colors.length - 1; index >= category; index--) {
            if (!movingDivider(index)) divider(graphics, index, colors[index], 1,
                    dividerOffset(index, category));
        }
        for (int index = 0; index < category; index++) {
            if (!movingDivider(index)) divider(graphics, index, colors[index], leafScale,
                    dividerOffset(index, category));
        }
        if (category > 0) {
            int rightAvailable = detail ? 0 : Math.max(0, pageCount - page - 1);
            stack(graphics, -1, Math.min(3, leftAvailable), leafScale, fanLeft, page, detail);
            stack(graphics, 1, Math.min(3, rightAvailable), 1, fanRight, page, detail);
            if (leftAvailable > 0) {
                graphics.pose().pushPose();
                graphics.pose().scale((float) leafScale, 1, 1);
                paperBack(graphics);
                graphics.pose().popPose();
            }
        }
        paper(graphics);
    }

    public void paper(GuiGraphics graphics) {
        spriteQuad(graphics, 0, 0, PAPER_W, PAPER_H, 0, 0, 1.0, 1.0, 0xFFFFFFFF, paperTexture());
    }

    public void paperBack(GuiGraphics graphics) {
        spriteQuad(graphics, 0, 0, PAPER_W, PAPER_H, 0, 0, 1.0, 1.0, 0xFFFFFFFF, paperBackTexture());
    }

    public void binding(GuiGraphics graphics, double openProgress) {
        if (openProgress <= .02) return;
        int bindTex = textureId(BINDING);
        spriteQuad(graphics, -6, 12, 6, PAPER_H - 12, 0, 0, 24.0 / 96.0, 1.0, 0xFFFFFFFF, bindTex);
        if (openProgress < .70) return;
        double opening = -Math.min(0, Math.cos(Math.PI * smooth((openProgress - .65) / .35)));
        boolean ringOpen = openProgress < .96 || turnCaptured;
        double v0 = ringOpen ? 36.0 / 96.0 : 0.0;
        double v1 = ringOpen ? 72.0 / 96.0 : 36.0 / 96.0;
        for (double y : new double[]{130, PAPER_H * .5, PAPER_H - 130}) {
            spriteQuad(graphics, -22 * opening, y - 12, 22, y + 12, 24.0 / 96.0, v0, 1.0, v1, 0xFFFFFFFF, bindTex);
        }
        if (openProgress > .98) {
            // The bound sheets lift above the seam when fanned. Their inner margin contains no ink.
            for (FanLayer layer : liftedPaper) {
                Point[] fragment = clipX(clipX(layer.paper, -22, true), 22, false);
                if (fragment.length < 3) continue;
                texturedPolygon(graphics, fragment, layer.tint, layer.leftSide ? paperBackTexture() : paperTexture(), false);
                if (layer.emphasized) polygon(graphics, fragment, 0x99D8D1C1);
            }
        }
    }

    /** Behind the paper after its hinge passes 90 degrees; in front otherwise. */
    public static boolean coverInFront(double openProgress) {
        return Math.cos(Math.toRadians(173) * smooth(openProgress / .70)) >= 0;
    }

    public void cover(GuiGraphics graphics, double openProgress, Consumer<GuiGraphics> insideCover) {
        double angle = Math.toRadians(173) * smooth(openProgress / .70);
        double scaleX = Math.cos(angle), shear = Math.sin(angle) * .022;
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, -26, 0);
        Matrix4f transform = new Matrix4f().identity();
        transform.m00((float) scaleX).m01((float) shear);
        graphics.pose().mulPoseMatrix(transform);
        ResourceLocation coverResource = scaleX < 0 ? FOLDER_INNER : (folderHovered && openProgress <= .02 ? FOLDER_FRONT_HOVER : FOLDER_FRONT);
        int coverTint = shade(0xFFFFFFFF, .84 + .16 * Math.abs(scaleX));
        spriteQuad(graphics, 0, -20, PAPER_W + 68, PAPER_H + 126, 0, 0, 1.0, 1.0, coverTint, textureId(coverResource));
        if (scaleX < -.08) {
            graphics.pose().pushPose();
            graphics.pose().scale(-1, 1, 1);
            graphics.pose().translate(0, 26, 0);
            insideCover.accept(graphics);
            graphics.pose().popPose();
        }
        if (scaleX > .08) {
            lettering(graphics, "CHANGED: SYNERGY", 111, 197, 3.2F, 0xFF4A361E);
            coverLabel(graphics, "cover_title", 111, 262, 7.4F, 630, 0xFF292014);
            coverLabel(graphics, "cover_subtitle", 111, 371, 2.8F, 630, 0xFF4A361E);
            coverLabel(graphics, "cover_register", 111, PAPER_H - 2, 2.3F, 630, 0xFF4A361E);
        }
        graphics.pose().popPose();
    }

    /** Snapshot once per turn. GUI clipping helpers can recognize this paper-sized framebuffer. */
    public void capture(Minecraft minecraft, Consumer<GuiGraphics> content) {
        capture(minecraft, content, null);
    }

    public void capture(Minecraft minecraft, Consumer<GuiGraphics> content, Consumer<GuiGraphics> reverseContent) {
        if (capturing) throw new IllegalStateException("Nested archive capture");
        turnCaptured = false;
        reverseCaptured = false;
        RenderSystem.assertOnRenderThread();
        Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting sorting = RenderSystem.getVertexSorting();
        PoseStack modelView = RenderSystem.getModelViewStack();
        ShaderInstance shader = RenderSystem.getShader();
        float[] shaderColor = RenderSystem.getShaderColor().clone();
        int[] shaderTextures = new int[12];
        for (int i = 0; i < shaderTextures.length; i++) shaderTextures[i] = RenderSystem.getShaderTexture(i);
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        boolean scissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND), depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        int depthFunction = GL11.glGetInteger(GL11.GL_DEPTH_FUNC);
        int sourceRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB), destinationRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
        int sourceAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA), destinationAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
        int blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
        int blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4), scissorBox = stack.mallocInt(4);
            ByteBuffer colorMask = stack.malloc(4);
            FloatBuffer clearColor = stack.mallocFloat(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, scissorBox);
            GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, colorMask);
            GL11.glGetFloatv(GL11.GL_COLOR_CLEAR_VALUE, clearColor);
            modelView.pushPose();
            try {
                if (snapshot == null) snapshot = new TextureTarget(CAPTURE_WIDTH, CAPTURE_HEIGHT, true, Minecraft.ON_OSX);
                if (reverseContent != null && reverseSnapshot == null)
                    reverseSnapshot = new TextureTarget(CAPTURE_WIDTH, CAPTURE_HEIGHT, true, Minecraft.ON_OSX);
                for (int face = 0; face < (reverseContent == null ? 1 : 2); face++) {
                    TextureTarget target = face == 0 ? snapshot : reverseSnapshot;
                    RenderSystem.disableScissor();
                    RenderSystem.depthMask(true);
                    RenderSystem.colorMask(true, true, true, true);
                    target.setClearColor(0, 0, 0, 0);
                    target.clear(Minecraft.ON_OSX);
                    target.bindWrite(true);
                    RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0, CAPTURE_WIDTH,
                            CAPTURE_HEIGHT, 0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
                    modelView.setIdentity();
                    modelView.translate(0, 0, -11000);
                    RenderSystem.applyModelViewMatrix();
                    RenderSystem.setShaderColor(1, 1, 1, 1);
                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    capturing = true;
                    GuiGraphics captureGraphics = new GuiGraphics(minecraft,
                            MultiBufferSource.immediate(new BufferBuilder(65536)));
                    paper(captureGraphics);
                    (face == 0 ? content : reverseContent).accept(captureGraphics);
                    captureGraphics.flush();
                }
                reverseCaptured = reverseContent != null;
                turnCaptured = true;
            } finally {
                capturing = false;
                modelView.popPose();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(projection, sorting);
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
                RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                // The disabled box is still GL state and must survive nested preview captures.
                RenderSystem.enableScissor(scissorBox.get(0), scissorBox.get(1), scissorBox.get(2), scissorBox.get(3));
                if (!scissor) RenderSystem.disableScissor();
                if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
                if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
                if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
                RenderSystem.blendFuncSeparate(sourceRgb, destinationRgb, sourceAlpha, destinationAlpha);
                GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
                RenderSystem.depthFunc(depthFunction);
                RenderSystem.depthMask(depthMask);
                RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0,
                        colorMask.get(2) != 0, colorMask.get(3) != 0);
                RenderSystem.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
                RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
                for (int i = 0; i < shaderTextures.length; i++) RenderSystem.setShaderTexture(i, shaderTextures[i]);
                if (shader != null) RenderSystem.setShader(() -> shader);
            }
        }
    }

    /** Front content and the blank reverse remain on opposite sides of the same curling mesh. */
    public void curl(GuiGraphics graphics, double easedProgress, int direction, int from, int to,
                     int[] colors, int backColor) {
        if (!turnCaptured || snapshot == null || easedProgress >= 1) return;
        graphics.flush();
        double progress = Math.max(0, Math.min(1, easedProgress));
        progress(progress);
        double meshWidth = PAPER_W + (from == to ? 0 : 58);
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            mesh(graphics, progress, direction, meshWidth, 0, 0xFFFFFFFF, true);
            int reverseTex = reverseCaptured ? reverseSnapshot.getColorTextureId() : paperBackTexture();
            int reverseTint = reverseCaptured ? 0xFFFFFFFF : backColor;
            if (direction > 0) {
                mesh(graphics, progress, direction, meshWidth, snapshot.getColorTextureId(), 0xFFFFFFFF, false);
                mesh(graphics, progress, direction, meshWidth, reverseTex, reverseTint, false);
            } else {
                mesh(graphics, progress, direction, meshWidth, reverseTex, reverseTint, false);
                mesh(graphics, progress, direction, meshWidth, snapshot.getColorTextureId(), 0xFFFFFFFF, false);
            }
            if (from != to) {
                for (int index = Math.min(from, to); index < Math.max(from, to); index++) {
                    if (index >= 0 && index < colors.length) curlEar(graphics, index, colors[index],
                            from, to, progress, direction, meshWidth);
                }
            }
        } finally {
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            RenderSystem.setShaderColor(1, 1, 1, 1);
        }
    }

    private boolean movingDivider(int index) {
        return turnCaptured && fromCategory != toCategory && fromCategory >= 0 && toCategory >= 0
                && index >= Math.min(fromCategory, toCategory) && index < Math.max(fromCategory, toCategory);
    }

    private double dividerOffset(int index, int category) {
        double destination = Math.max(0, index < category ? category - index - 1 : index - category) * 1.25;
        if (fromCategory < 0 || toCategory < 0 || !turnCaptured) return destination;
        double source = Math.max(0, index < fromCategory ? fromCategory - index - 1 : index - fromCategory) * 1.25;
        return source + (destination - source) * turnProgress;
    }

    private void divider(GuiGraphics graphics, int index, int color, double side, double offset) {
        graphics.pose().pushPose();
        graphics.pose().translate(0, offset, 0);
        graphics.pose().scale((float) side, 1, 1);
        int clamped = Math.max(0, Math.min(DIVIDERS.length - 1, index));
        ResourceLocation dividerTex = (hoveredTab == clamped && !turnCaptured) ? DIVIDERS_HOVER[clamped] : DIVIDERS[clamped];
        spriteQuad(graphics, 0, 0, 834, PAPER_H, 0, 0, 1.0, 1.0, 0xFFFFFFFF, textureId(dividerTex));
        graphics.pose().popPose();
    }

    private void stack(GuiGraphics graphics, int side, int available, double leafScale, double fan,
                       int page, boolean detail) {
        for (int depth = available; depth >= 1; depth--) {
            int target = detail ? page : page + side * depth;
            Point[] corners = stackLeaf(side, depth, fan);
            for (int i = 0; i < corners.length; i++) {
                Point p = corners[i];
                corners[i] = new Point(side < 0 ? -p.x() * leafScale : p.x(), p.y());
            }
            int tint = shade(0xFFFFFFFF, 1 - depth * .009);
            boolean emphasized = fan > .35 && hoveredPage == target;
            texturedPolygon(graphics, corners, tint, side < 0 ? paperBackTexture() : paperTexture(), false);
            if (emphasized) polygon(graphics, corners, 0x66D8C89E);
            if (fan > .001) liftedPaper.add(new FanLayer(corners, tint, emphasized, side < 0));
            outline(graphics, corners, 1.0, emphasized ? 0xFF5C4E38 : 0xFF8F8775);
            if (fan > .05) {
                graphics.pose().pushPose();
                graphics.pose().translate(0, PAPER_H / 2 + depth * 1.5, 0);
                graphics.pose().mulPose(new Quaternionf().rotateZ((float) Math.toRadians(depth * 2 * fan) * side));
                graphics.pose().scale((float) (side < 0 ? -leafScale : 1), 1, 1);
                graphics.pose().translate(0, -PAPER_H / 2, 0);
                double stripX0 = side < 0 ? -PAPER_W + 76 : PAPER_W - 124;
                double v0 = emphasized ? 0.5 : 0.0;
                spriteQuad(graphics, stripX0, PAPER_H - 28, stripX0 + 44, PAPER_H - 4, 0, v0, 1.0, v0 + 0.5, 0xAAFFFFFF, textureId(PAGE_STACK));
                lettering(graphics, String.format(java.util.Locale.ROOT, "%02d", target + 1),
                        side < 0 ? -PAPER_W + 94 : PAPER_W - 110, PAPER_H - 22,
                        1.55F, hoveredPage == target ? 0xFF29241A : 0xFF59523F);
                graphics.pose().popPose();
            }
        }
    }

    private void mesh(GuiGraphics graphics, double progress, int direction, double meshWidth,
                      int texture, int tint, boolean shadow) {
        boolean frontTexture = texture != 0 && texture == snapshot.getColorTextureId();
        boolean reverseTexture = reverseCaptured && texture == reverseSnapshot.getColorTextureId();
        RenderSystem.setShader(shadow ? GameRenderer::getPositionColorShader : GameRenderer::getPositionTexColorShader);
        if (!shadow) RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, shadow ? DefaultVertexFormat.POSITION_COLOR : DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        // Same 128 by 12 subdivisions as the supplied shader. Painter order follows the free edge.
        for (int column = 0; column < 128; column++) {
            double x0 = PAPER_W * column / 128, x1 = PAPER_W * (column + 1) / 128;
            for (int row = 0; row < 12; row++) {
                double y0 = row / 12.0, y1 = (row + 1) / 12.0;
                boolean front = normal((x0 + x1) * .5, (y0 + y1) * .5, meshWidth, progress, direction) >= 0;
                if (!shadow && frontTexture != (front && (x0 + x1) * .5 >= 22)) continue;
                int faceTint = front && !frontTexture ? 0xFFFFFFFF : tint;
                meshVertex(buffer, matrix, x0, y0, meshWidth, progress, direction, faceTint, shadow, frontTexture, reverseTexture);
                meshVertex(buffer, matrix, x0, y1, meshWidth, progress, direction, faceTint, shadow, frontTexture, reverseTexture);
                meshVertex(buffer, matrix, x1, y1, meshWidth, progress, direction, faceTint, shadow, frontTexture, reverseTexture);
                meshVertex(buffer, matrix, x1, y0, meshWidth, progress, direction, faceTint, shadow, frontTexture, reverseTexture);
            }
        }
        BufferUploader.drawWithShader(buffer.end());
    }

    private static void meshVertex(BufferBuilder buffer, Matrix4f matrix, double x, double y, double meshWidth,
                                   double progress, int direction, int tint, boolean shadow, boolean front, boolean reverse) {
        Point point = ArchiveGeometry.curl(x, y, meshWidth, progress, direction);
        int color = shadow ? (int) (46 * Math.sin(progress * Math.PI)) << 24
                : shade(tint, .8 + .2 * Math.abs(normal(x, y, meshWidth, progress, direction)));
        var vertex = buffer.vertex(matrix, (float) (point.x() + (shadow ? 3 : 0)),
                (float) (point.y() + (shadow ? 4 : 0)), 0);
        if (!shadow) vertex.uv((float) (reverse ? 1 - x / PAPER_W : x / PAPER_W), (float) (front || reverse ? 1 - y : y));
        vertex.color(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24).endVertex();
    }

    private void curlEar(GuiGraphics graphics, int index, int color, int from, int to, double progress,
                         int direction, double meshWidth) {
        double[] tab = tab(index);
        double fromOffset = Math.max(0, index < from ? from - index - 1 : index - from) * 1.25;
        double toOffset = Math.max(0, index < to ? to - index - 1 : index - to) * 1.25;
        tab[1] += fromOffset + (toOffset - fromOffset) * progress;
        Point[] contour = ear(tab);
        for (int i = 0; i < contour.length; i++) {
            Point p = contour[i];
            contour[i] = ArchiveGeometry.curl(p.x(), p.y() / PAPER_H, meshWidth, progress, direction);
        }
        texturedPolygon(graphics, contour, shade(color, .8 + .2 * Math.abs(normal(
                PAPER_W + tab[2] * .5, (tab[1] + tab[3] * .5) / PAPER_H, meshWidth, progress, direction))), paperTexture(), false);
        outline(graphics, contour, 1.0, shade(color, .52));
    }

    private static double normal(double x, double y, double width, double progress, int direction) {
        double sine = Math.sin(progress * Math.PI), radius = Math.max(.1, width * .075 * sine);
        double crease = width * (1 - progress) + width * .08 * sine * (.5 - y);
        return direction * Math.cos(Math.min(Math.max(0, x - crease) / radius, Math.PI));
    }

    private static Point[] ear(double[] tab) {
        double x = tab[0], y = tab[1], w = tab[2], h = tab[3];
        return new Point[]{new Point(x, y - 4), new Point(x + w - 8, y - 4),
                new Point(x + w - 3, y - 2), new Point(x + w, y + 3),
                new Point(x + w, y + h - 7), new Point(x + w - 2, y + h - 2),
                new Point(x + w - 8, y + h), new Point(x, y + h)};
    }

    public static int textureId(ResourceLocation location) {
        return Minecraft.getInstance().getTextureManager().getTexture(location).getId();
    }

    private static int paperTexture() {
        return textureId(PAPER);
    }

    private static int paperBackTexture() {
        return textureId(PAPER_BACK);
    }

    public static void spriteQuad(GuiGraphics graphics, double x0, double y0, double x1, double y1,
                                  double u0, double v0, double u1, double v1, int tint, int texture) {
        graphics.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        int r = tint >>> 16 & 255, g = tint >>> 8 & 255, b = tint & 255, a = tint >>> 24;
        buffer.vertex(matrix, (float) x0, (float) y0, 0).uv((float) u0, (float) v0).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x0, (float) y1, 0).uv((float) u0, (float) v1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x1, (float) y1, 0).uv((float) u1, (float) v1).color(r, g, b, a).endVertex();
        buffer.vertex(matrix, (float) x1, (float) y0, 0).uv((float) u1, (float) v0).color(r, g, b, a).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        if (depth) RenderSystem.enableDepthTest();
        if (cull) RenderSystem.enableCull();
    }

    private static void texture(GuiGraphics graphics, double x0, double y0, double x1, double y1, int tint) {
        texturedPolygon(graphics, new Point[]{new Point(x0, y0), new Point(x1, y0),
                new Point(x1, y1), new Point(x0, y1)}, tint, paperTexture(), false);
    }

    private static void texturedPolygon(GuiGraphics graphics, Point[] points, int tint, int texture, boolean flipY) {
        graphics.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, texture);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_TEX_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        for (Point point : points) buffer.vertex(matrix, (float) point.x(), (float) point.y(), 0)
                .uv((float) (point.x() / PAPER_W), (float) (flipY ? 1 - point.y() / PAPER_H : point.y() / PAPER_H))
                .color(tint >>> 16 & 255, tint >>> 8 & 255, tint & 255, tint >>> 24).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        if (depth) RenderSystem.enableDepthTest();
        if (cull) RenderSystem.enableCull();
    }

    public static void rectangle(GuiGraphics graphics, double x0, double y0, double x1, double y1, int color) {
        polygon(graphics, new Point[]{new Point(x0, y0), new Point(x1, y0),
                new Point(x1, y1), new Point(x0, y1)}, color);
    }

    public static void border(GuiGraphics graphics, double x0, double y0, double x1, double y1, double width, int color) {
        rectangle(graphics, x0, y0, x1, y0 + width, color);
        rectangle(graphics, x0, y1 - width, x1, y1, color);
        rectangle(graphics, x0, y0, x0 + width, y1, color);
        rectangle(graphics, x1 - width, y0, x1, y1, color);
    }

    public static void polygon(GuiGraphics graphics, Point[] points, int color) {
        graphics.flush();
        boolean depth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST), cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
        Matrix4f matrix = graphics.pose().last().pose();
        for (Point p : points) buffer.vertex(matrix, (float) p.x(), (float) p.y(), 0)
                .color(color >>> 16 & 255, color >>> 8 & 255, color & 255, color >>> 24).endVertex();
        BufferUploader.drawWithShader(buffer.end());
        if (depth) RenderSystem.enableDepthTest();
        if (cull) RenderSystem.enableCull();
    }

    private static void outline(GuiGraphics graphics, Point[] points, double width, int color) {
        for (int i = 0; i < points.length; i++) {
            Point a = points[i], b = points[(i + 1) % points.length];
            double dx = b.x() - a.x(), dy = b.y() - a.y(), length = Math.hypot(dx, dy);
            if (length < .001) continue;
            double nx = -dy / length * width * .5, ny = dx / length * width * .5;
            polygon(graphics, new Point[]{new Point(a.x() + nx, a.y() + ny), new Point(b.x() + nx, b.y() + ny),
                    new Point(b.x() - nx, b.y() - ny), new Point(a.x() - nx, a.y() - ny)}, color);
        }
    }

    private static void circle(GuiGraphics graphics, double x, double y, double radius, int color) {
        Point[] points = new Point[20];
        for (int i = 0; i < points.length; i++) points[i] = new Point(x + Math.cos(i * Math.PI * 2 / points.length) * radius,
                y + Math.sin(i * Math.PI * 2 / points.length) * radius);
        polygon(graphics, points, color);
    }

    private static void lettering(GuiGraphics graphics, String text, double x, double y, float scale, int color) {
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1);
        // All archive lettering is a 2D overlay; preview models must not punch depth holes in it.
        Minecraft.getInstance().font.drawInBatch(text, 0, 0, color, false, graphics.pose().last().pose(),
                graphics.bufferSource(), Font.DisplayMode.SEE_THROUGH, 0, 15728880);
        graphics.flush();
        graphics.pose().popPose();
    }

    private static void coverLabel(GuiGraphics graphics,String key,double x,double y,float scale,int width,int color) {
        String text=Component.translatable("origin.changed_synergy.archive."+key).getString();
        lettering(graphics,text,x,y,Math.min(scale,(float)width/Math.max(1,Minecraft.getInstance().font.width(text))),color);
    }

    private static int shade(int color, double shade) {
        return color & 0xFF000000 | (int) ((color >>> 16 & 255) * shade) << 16
                | (int) ((color >>> 8 & 255) * shade) << 8 | (int) ((color & 255) * shade);
    }

    private static Point[] clipX(Point[] polygon, double edge, boolean keepRight) {
        if (polygon.length == 0) return polygon;
        ArrayList<Point> clipped = new ArrayList<>(polygon.length + 2);
        Point last = polygon[polygon.length - 1];
        boolean lastInside = keepRight ? last.x() >= edge : last.x() <= edge;
        for (Point point : polygon) {
            boolean inside = keepRight ? point.x() >= edge : point.x() <= edge;
            if (inside != lastInside) {
                double t = (edge - last.x()) / (point.x() - last.x());
                clipped.add(new Point(edge, last.y() + (point.y() - last.y()) * t));
            }
            if (inside) clipped.add(point);
            last = point; lastInside = inside;
        }
        return clipped.toArray(Point[]::new);
    }

    @Override public void close() {
        if (snapshot != null) {
            int draw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            int read = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            try {
                snapshot.destroyBuffers();
                snapshot = null;
                if (reverseSnapshot != null) { reverseSnapshot.destroyBuffers(); reverseSnapshot = null; }
            } finally {
                GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, draw);
                GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, read);
            }
        }
        liftedPaper.clear();
        finishTransition();
    }
}
