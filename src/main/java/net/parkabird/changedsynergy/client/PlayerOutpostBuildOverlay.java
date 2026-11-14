package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Lightweight HUD while camp build mode is active. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PlayerOutpostBuildOverlay {
    private PlayerOutpostBuildOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("player_outpost_build", PlayerOutpostBuildOverlay::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick,
                               int screenWidth, int screenHeight) {
        if (!PlayerOutpostBuildClient.active()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui || minecraft.screen != null || minecraft.player == null) return;

        Component title = Component.translatable("overlay.changed_synergy.player_outpost.build.title",
                Component.translatable("command.changed_synergy.outpost.facility."
                        + PlayerOutpostBuildClient.palette().key()));
        Component hint = Component.translatable("overlay.changed_synergy.player_outpost.build.hint",
                minecraft.options.keyShift.getTranslatedKeyMessage(),
                minecraft.options.keyUse.getTranslatedKeyMessage());
        int width = Math.max(minecraft.font.width(title), minecraft.font.width(hint));
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 78;
        graphics.fill(x - 6, y - 5, x + width + 6, y + 26, 0xA008080B);
        graphics.drawString(minecraft.font, title, x, y, 0xFFE8E3C5, true);
        graphics.drawString(minecraft.font, hint, x, y + 12, 0xFFC8C2A8, true);
    }
}
