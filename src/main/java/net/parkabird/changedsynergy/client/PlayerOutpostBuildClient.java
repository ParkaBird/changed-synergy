package net.parkabird.changedsynergy.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.PlayerOutpostBuildService.Palette;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PlayerOutpostBuildActionPacket;

/** Client mirror of the camp build session plus placement / cycle / exit input. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class PlayerOutpostBuildClient {
    private static boolean active;
    private static int palette;

    private PlayerOutpostBuildClient() {
    }

    public static void apply(boolean buildActive, int paletteOrdinal) {
        active = buildActive;
        Palette[] values = Palette.values();
        palette = Math.max(0, Math.min(values.length - 1, paletteOrdinal));
    }

    public static boolean active() {
        return active;
    }

    public static Palette palette() {
        Palette[] values = Palette.values();
        return values[Math.max(0, Math.min(values.length - 1, palette))];
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!active || !event.getLevel().isClientSide() || event.getEntity() != Minecraft.getInstance().player) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.isShiftKeyDown()
                && minecraft.player.getMainHandItem().isEmpty()
                && minecraft.player.getOffhandItem().isEmpty()) {
            ChangedSynergyNetwork.CHANNEL.sendToServer(PlayerOutpostBuildActionPacket.exit());
            event.setCanceled(true);
            return;
        }
        ChangedSynergyNetwork.CHANNEL.sendToServer(PlayerOutpostBuildActionPacket.place(event.getHitVec()));
        event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickEmpty(PlayerInteractEvent.RightClickEmpty event) {
        if (!active || !event.getLevel().isClientSide()) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.isShiftKeyDown()) {
            ChangedSynergyNetwork.CHANNEL.sendToServer(PlayerOutpostBuildActionPacket.exit());
        }
    }

    @SubscribeEvent
    public static void onScroll(InputEvent.MouseScrollingEvent event) {
        if (!active || Minecraft.getInstance().screen != null) return;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || !minecraft.player.isShiftKeyDown()) return;
        double delta = event.getScrollDelta();
        if (delta == 0.0D) return;
        ChangedSynergyNetwork.CHANNEL.sendToServer(PlayerOutpostBuildActionPacket.cycle(delta < 0 ? -1 : 1));
        event.setCanceled(true);
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        active = false;
        palette = 0;
    }

}
