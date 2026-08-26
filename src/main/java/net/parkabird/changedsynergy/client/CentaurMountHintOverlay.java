package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.world.inventory.CentaurMountService;

/** Shows the tack shortcut while the crosshair is on a ride-capable taur. */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD,
        value = Dist.CLIENT)
public final class CentaurMountHintOverlay {
    private CentaurMountHintOverlay() {
    }

    @SubscribeEvent
    public static void registerOverlay(RegisterGuiOverlaysEvent event) {
        event.registerAboveAll(
                "centaur_mount_hint",
                CentaurMountHintOverlay::render);
    }

    private static void render(
            ForgeGui gui,
            GuiGraphics graphics,
            float partialTick,
            int screenWidth,
            int screenHeight) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.hideGui
                || minecraft.screen != null
                || minecraft.player == null
                || !minecraft.player.isAlive()
                || minecraft.player.isSpectator()
                || !(minecraft.hitResult instanceof EntityHitResult hit)
                || !(hit.getEntity() instanceof ChangedEntity centaur)
                || !centaur.isAlive()
                || !CentaurMountService.isCentaur(centaur)
                || minecraft.player.getVehicle() == centaur) {
            return;
        }

        Component hint = Component.translatable(
                "overlay.changed_synergy.centaur_mount.hint",
                minecraft.options.keyShift.getTranslatedKeyMessage(),
                minecraft.options.keyUse.getTranslatedKeyMessage());
        int width = minecraft.font.width(hint);
        int x = (screenWidth - width) / 2;
        int y = screenHeight - 58;
        graphics.fill(x - 5, y - 4, x + width + 5, y + 12, 0x9008080B);
        graphics.drawString(
                minecraft.font,
                hint,
                x,
                y,
                0xFFE8E3C5,
                true);
    }
}
