package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.ltxprogrammer.changed.Changed;
import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedTags;
import com.mojang.blaze3d.systems.RenderSystem;

/** Shared by Changed's primary HUD and Addon's merged secondary HUD. Cooldown fills stay native. */
public final class AbilityHudMotion {
    private static final UiSpring[] VISIBLE = {new UiSpring(0, 12, 0.9), new UiSpring(0, 12, 0.9)};
    private static final UiSpring[] HIGHLIGHT = {new UiSpring(0, 30, 1), new UiSpring(0, 30, 1)};
    private static final AbstractAbilityInstance[] HIGHLIGHT_FOR = new AbstractAbilityInstance[2];
    private static final int[] LAST_ACTIVE_HEIGHT = new int[2];
    private static final long[] LAST_DRAWN_AT = new long[2];
    private static final ResourceLocation BACKGROUND = Changed.modResource("textures/gui/ability_backgrounds.png");
    private static Object variant, level;
    private AbilityHudMotion() {}
    public static void beginFrame() {
        var mc = Minecraft.getInstance();
        var current = mc.player == null ? null : ProcessTransfur.getPlayerTransfurVariant(mc.player);
        if (current != variant || level != mc.level) {
            variant = current; level = mc.level;
            for (var spring : VISIBLE) spring.snap(0, System.nanoTime());
            for (int i = 0; i < HIGHLIGHT.length; i++) {
                HIGHLIGHT[i].snap(0, System.nanoTime()); HIGHLIGHT_FOR[i] = null; LAST_ACTIVE_HEIGHT[i] = 0;
            }
        }
    }
    public static float slide(float nativeProgress, boolean secondary) {
        var spring = VISIBLE[secondary ? 1 : 0];
        long now = System.nanoTime();
        double visible = nativeProgress <= 0.0001F ? 1 : 0;
        spring.target(visible, now);
        if (UiMotion.reduced()) spring.snap(visible, now);
        return 1 - UiSpring.clamp(spring.value(now));
    }

    /** Keep native cooldown/charge geometry; only the activation layer's opacity eases in and out. */
    public static void renderBackground(int x, int y, GuiGraphics graphics, AbstractRadialScreen.ColorScheme scheme,
            Player player, TransfurVariantInstance<?> variant, AbstractAbilityInstance selected, boolean secondary) {
        int slot = secondary ? 1 : 0;
        long now = System.nanoTime();
        var controller = selected.getController();
        int cool = selected.canUse() ? (int)(controller.coolDownPercent() * 32) : 0;
        int active = cool >= 32 ? (int)(controller.getProgressActive() * 32) : 0;
        float brightness=((net.parkabird.changedsynergy.ability.HypnosisToggle)controller).changedSynergy$hypnosisBrightness();
        if (HIGHLIGHT_FOR[slot] != selected || active == 0 && now - LAST_DRAWN_AT[slot] > 1_000_000_000L) {
            HIGHLIGHT_FOR[slot] = selected; HIGHLIGHT[slot].snap(0, now); LAST_ACTIVE_HEIGHT[slot] = 0;
        }
        LAST_DRAWN_AT[slot] = now;
        if (active > 0) LAST_ACTIVE_HEIGHT[slot] = active;
        HIGHLIGHT[slot].target(active > 0 ? 1 : 0, now);
        if (UiMotion.reduced()) HIGHLIGHT[slot].snap(active > 0 ? 1 : 0, now);
        float alpha = UiSpring.clamp(HIGHLIGHT[slot].displayValue(now, 0.001));
        int height = active > 0 ? active : LAST_ACTIVE_HEIGHT[slot];
        int u = variant.getParent().getEntityType().is(ChangedTags.EntityTypes.LATEX) ? 0 : 32;
        var back = scheme.background(); var fore = scheme.foreground();
        RenderSystem.setShaderTexture(0, BACKGROUND); RenderSystem.enableDepthTest();
        RenderSystem.enableBlend(); RenderSystem.defaultBlendFunc();
        RenderSystem.blendFunc(com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        graphics.setColor(back.red(), back.green(), back.blue(), 1);
        graphics.blit(BACKGROUND, x, y, u, 0, 32, 32, 64, 96);
        if (cool > 0) graphics.blit(BACKGROUND, x, y + 32 - cool, u, 32 + 32 - cool, 32, cool, 64, 96);
        if (height > 0 && alpha > 0) {
            graphics.setColor(fore.red(), fore.green(), fore.blue(), alpha*brightness);
            graphics.blit(BACKGROUND, x, y + 32 - height, u, 64 + 32 - height, 32, height, 64, 96);
        }
        var nativeColor = active > 0 ? fore : back;
        graphics.setColor(nativeColor.red(), nativeColor.green(), nativeColor.blue(), 1);
    }
}
