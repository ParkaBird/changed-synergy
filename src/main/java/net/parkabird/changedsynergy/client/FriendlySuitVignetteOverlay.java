package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** A custom theme-colored veil that distinguishes friendly wrapping from transfur. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class FriendlySuitVignetteOverlay {
    private FriendlySuitVignetteOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll("friendly_suit_vignette", FriendlySuitVignetteOverlay::render);
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        var player = Minecraft.getInstance().player;
        if (!ChangedSynergyClientConfig.CLIENT.friendlySuitVignette.get()
                || player == null
                || !FriendlySuitClientState.isOwnerSuited(player.getId())) {
            return;
        }

        gui.setupOverlayRenderState(true, false);
        float opacity = ChangedSynergyClientConfig.CLIENT
                .friendlySuitVignetteOpacity.get().floatValue();
        var creature = FriendlySuitClientState.getSuitingCreature(player.getId());
        Color3 theme = creature == null || creature.getSelfVariant() == null
                ? Color3.GRAY
                : creature.getSelfVariant().getColors().getFirst();
        float time = player.tickCount + partialTick;
        FriendlyWrapMaskRenderer.render(
                graphics,
                screenWidth,
                screenHeight,
                theme,
                opacity,
                time);
    }
}
