package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.MovementInputUpdateEvent;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.entity.MuntjacJumpService;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.MuntjacJumpInputPacket;

@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class MuntjacJumpClient {
    private static final ResourceLocation ICONS = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/icons.png");
    private MuntjacJumpClient() {}

    private static boolean controlsAllowed() {
        var mc = Minecraft.getInstance();
        return mc.player != null && mc.isWindowActive() && !mc.isPaused() && !TakeoverClientState.movementLocked()
                && (mc.screen == null || AbilityWheelInput.allowsMovement(mc.options.keyJump));
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void input(MovementInputUpdateEvent event) {
        var mc = Minecraft.getInstance();
        if (event.getEntity() != mc.player || !controlsAllowed() || !MuntjacJumpService.refresh(mc.player)) return;
        var state = MuntjacJumpService.state(mc.player);
        boolean down = mc.options.keyJump.isDown();
        if (down != state.pressed()) {
            ChangedSynergyNetwork.CHANNEL.sendToServer(new MuntjacJumpInputPacket(down
                    ? MuntjacJumpInputPacket.PRESS : MuntjacJumpInputPacket.RELEASE));
            if (MuntjacJumpService.input(mc.player, down) >= 0) mc.player.jumpFromGround();
        }
        // Vanilla's flight double-tap reads this edge too. The ground-jump mixin already holds the charge.
        if (state.holding() && !mc.player.getAbilities().mayfly) event.getInput().jumping = false;
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        var mc = Minecraft.getInstance();
        if (event.phase != TickEvent.Phase.END || mc.player == null || controlsAllowed()) return;
        var state = MuntjacJumpService.state(mc.player);
        if (!state.bound() || !state.pressed() && !state.charging()) return;
        ChangedSynergyNetwork.CHANNEL.sendToServer(new MuntjacJumpInputPacket(MuntjacJumpInputPacket.CANCEL));
        state.cancel(mc.options.keyJump.isDown());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void experience(RenderGuiOverlayEvent.Pre event) {
        var mc = Minecraft.getInstance();
        if (event.getOverlay() != VanillaGuiOverlay.EXPERIENCE_BAR.type() || mc.player == null
                || mc.options.hideGui || !MuntjacJumpService.charging(mc.player)) return;
        event.setCanceled(true);
        int left = event.getWindow().getGuiScaledWidth() / 2 - 91;
        int top = event.getWindow().getGuiScaledHeight() - 29;
        int fill = (int)(183 * MuntjacJumpService.state(mc.player).charge(mc.player.tickCount));
        RenderSystem.setShaderColor(1, 1, 1, 1);
        var graphics = event.getGuiGraphics();
        graphics.blit(ICONS, left, top, 0, 84, 182, 5);
        if (fill > 0) graphics.blit(ICONS, left, top, 0, 89, fill, 5);
    }
}
