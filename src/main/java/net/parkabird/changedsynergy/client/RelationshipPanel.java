package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Textured relationship cards retain their artwork and a softly rounded theme-colored halo. */
public final class RelationshipPanel {
    private static final int SHADOW_RADIUS = 4;
    public static final int CARD_WIDTH = 214;
    public static final int GEL_SOURCE_HEIGHT = 76;
    public static final int GEL_TOP = 34;
    public static final int GEL_BOTTOM = 20;
    public static final int GEL_TEXT_PADDING = GEL_TOP + GEL_BOTTOM + 2;
    private static final ResourceLocation ICONS = texture("panel_icons");
    private static final ResourceLocation GOO_GLOW = texture("glow/goo");
    private static final ResourceLocation ORGANIC_CARD = texture("vertical/organic/card");
    private RelationshipPanel() {}

    record InfoCard(Component title, List<Component> lines) {}

    record PreparedCard(Component title, List<FormattedCharSequence> lines, int height) {}

    static PreparedCard prepareCard(Font font, InfoCard card, int textWidth) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : card.lines()) {
            lines.addAll(font.split(line, textWidth));
        }
        return new PreparedCard(card.title(), List.copyOf(lines),
                GEL_TEXT_PADDING + Math.max(1, lines.size()) * 9);
    }

    public enum CardStyle {
        STATUS("status", 0), CONTACTS("contacts", 12), LOCATION("location", 24);

        private final ResourceLocation gooTexture;
        private final int iconU;
        CardStyle(String name, int iconU) {
            this.gooTexture = RelationshipPanel.texture("vertical/goo/" + name);
            this.iconU = iconU;
        }
    }

    /** The supplied body is #A2A2A2 before the native inventory tint is applied. */
    public static int bodyTextColor(int tint) {
        double r = linear((tint >> 16 & 255) * 162 / 65025.0);
        double g = linear((tint >> 8 & 255) * 162 / 65025.0);
        double b = linear((tint & 255) * 162 / 65025.0);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.179 ? 0x000000 : 0xFFFFFF;
    }

    private static double linear(double value) {
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static ResourceLocation texture(String name) {
        return ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID,
                "textures/gui/relationship/" + name + ".png");
    }

    /** Only the plain middle stretches vertically; both textured caps retain every pixel. */
    public static void drawCard(GuiGraphics graphics, int x, int y, CardStyle style,
            boolean organic, int cardHeight, int background, int foreground, float opacity) {
        float alpha = UiSpring.clamp(opacity);
        if (alpha <= 0.02F) return;
        int width = CARD_WIDTH, sourceHeight = GEL_SOURCE_HEIGHT;
        int height = cardHeight;
        ResourceLocation texture = organic ? ORGANIC_CARD : style.gooTexture;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        try {
            drawCardGlow(graphics, GOO_GLOW, x, y, width, height, sourceHeight, background, alpha);
            int tint = organic ? 0xFFFFFF : background;
            graphics.setColor((tint >> 16 & 255) / 255F,
                    (tint >> 8 & 255) / 255F, (tint & 255) / 255F, alpha);
            blitCard(graphics, texture, x, y, width, height, sourceHeight, 0);
            graphics.setColor((foreground >> 16 & 255) / 255F,
                    (foreground >> 8 & 255) / 255F, (foreground & 255) / 255F, alpha);
            // The location pin is one pixel taller; centre the visible glyphs on the title row.
            graphics.blit(ICONS, x + 8, y + (style == CardStyle.LOCATION ? 5 : 6), style.iconU, 0, 12, 12, 36, 12);
        } finally {
            graphics.setColor(1, 1, 1, 1);
            RenderSystem.disableBlend();
        }
    }

    public static void draw(GuiGraphics graphics, int x, int y, int width, int height,
            int background, int accent, float opacity) {
        drawShadow(graphics, x, y, width, height, accent, opacity);
        int alpha = Math.round((background >>> 24) * UiSpring.clamp(opacity));
        graphics.fill(x + 1, y + 1, x + width - 1, y + height - 1, alpha << 24 | background & 0xFFFFFF);
        ring(graphics, x, y, width, height, Math.round(alpha * 0.72F) << 24 | background & 0xFFFFFF);
    }

    /** Reuse the social artwork at dialogue widths without stretching its corners. */
    public static void drawDialogue(GuiGraphics graphics,int x,int y,int width,int height,int tint) {
        drawDialogue(graphics, x, y, width, height, tint, false);
    }

    public static void drawDialogue(GuiGraphics graphics,int x,int y,int width,int height,int tint,boolean organic) {
        drawShadow(graphics,x,y,width,height,organic?0xC6C6C6:tint,1);
        RenderSystem.enableBlend();RenderSystem.defaultBlendFunc();
        int blitTint = organic ? 0xFFFFFF : tint;
        graphics.setColor((blitTint>>16&255)/255F,(blitTint>>8&255)/255F,(blitTint&255)/255F,1);
        ResourceLocation texture = organic ? ORGANIC_CARD : CardStyle.CONTACTS.gooTexture;
        int[] sourceX={0,16,CARD_WIDTH-16},sourceY={0,GEL_TOP,GEL_SOURCE_HEIGHT-GEL_BOTTOM};
        int[] sourceW={16,CARD_WIDTH-32,16},sourceH={GEL_TOP,GEL_SOURCE_HEIGHT-GEL_TOP-GEL_BOTTOM,GEL_BOTTOM};
        int[] destW={16,width-32,16},destH={GEL_TOP,height-GEL_TOP-GEL_BOTTOM,GEL_BOTTOM};
        try {
            int yy=y;
            for(int row=0;row<3;row++) {
                int xx=x;
                for(int col=0;col<3;col++) {
                    graphics.blit(texture,xx,yy,destW[col],destH[row],sourceX[col],sourceY[row],sourceW[col],sourceH[row],CARD_WIDTH,GEL_SOURCE_HEIGHT);
                    xx+=destW[col];
                }
                yy+=destH[row];
            }
        } finally {graphics.setColor(1,1,1,1);RenderSystem.disableBlend();}
    }

    public static int readableText(int color,int surface) {
        double bg=luminance(surface);int target=bg>.179?0:0xFFFFFF;
        for(int step=0;step<10;step++) {
            double fg=luminance(color);
            if((Math.max(bg,fg)+.05)/(Math.min(bg,fg)+.05)>=4.5)return color;
            int mixed=0;
            for(int shift:new int[]{16,8,0})mixed|=Math.round((color>>shift&255)*.7F+(target>>shift&255)*.3F)<<shift;
            color=mixed;
        }
        return target;
    }

    private static double luminance(int rgb) {
        return .2126*linear((rgb>>16&255)/255D)+.7152*linear((rgb>>8&255)/255D)+.0722*linear((rgb&255)/255D);
    }

    private static void drawShadow(GuiGraphics graphics, int x, int y, int width, int height,
            int accent, float opacity) {
        float glow = opacity * breathing(System.nanoTime());
        for (int spread = SHADOW_RADIUS; spread >= 0; spread--) {
            int alpha = shadowAlpha(spread, glow);
            if (alpha > 0) ring(graphics, x - spread, y - spread,
                    width + spread * 2, height + spread * 2, alpha << 24 | accent & 0xFFFFFF);
        }
    }

    private static void drawCardGlow(GuiGraphics graphics, ResourceLocation texture,
            int x, int y, int width, int height, int sourceHeight, int color, float opacity) {
        graphics.setColor((color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F,
                (color & 255) / 255F, opacity * breathing(System.nanoTime()));
        // The exterior-only mask comes from the artwork's alpha, including its exact corner pixels.
        blitCard(graphics, texture, x - SHADOW_RADIUS, y - SHADOW_RADIUS,
                width + SHADOW_RADIUS * 2, height + SHADOW_RADIUS * 2,
                sourceHeight + SHADOW_RADIUS * 2, SHADOW_RADIUS);
    }

    private static void blitCard(GuiGraphics graphics, ResourceLocation texture,
            int x, int y, int width, int height, int sourceHeight, int padding) {
        if (height == sourceHeight) {
            graphics.blit(texture, x, y, 0, 0, width, height, width, sourceHeight);
            return;
        }
        int top = GEL_TOP + padding, bottom = GEL_BOTTOM + padding;
        graphics.blit(texture, x, y, 0, 0, width, top, width, sourceHeight);
        graphics.blit(texture, x, y + top, width, height - top - bottom,
                0, top, width, sourceHeight - top - bottom, width, sourceHeight);
        graphics.blit(texture, x, y + height - bottom, 0, sourceHeight - bottom,
                width, bottom, width, sourceHeight);
    }

    static float breathing(long now) {
        return UiMotion.reduced() ? 1 : 0.85F + 0.15F * (float)Math.cos((now % 4_000_000_000L) * (Math.PI * 2 / 4_000_000_000.0));
    }

    static int shadowAlpha(int distance, float opacity) {
        float falloff = Math.max(0, 1 - distance / (float)(SHADOW_RADIUS + 1));
        return Math.round(0x2A * falloff * falloff * UiSpring.clamp(opacity));
    }

    private static void ring(GuiGraphics graphics, int x, int y, int width, int height, int color) {
        graphics.fill(x, y, x + width, y + 1, color);
        graphics.fill(x, y + height - 1, x + width, y + height, color);
        graphics.fill(x, y + 1, x + 1, y + height - 1, color);
        graphics.fill(x + width - 1, y + 1, x + width, y + height - 1, color);
    }
}
