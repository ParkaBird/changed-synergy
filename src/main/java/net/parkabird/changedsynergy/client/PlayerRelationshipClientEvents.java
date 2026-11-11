package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.init.ChangedKeyMappings;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.PlayerRelationshipOpenPacket;

/** Reuses Changed's ability key and provides the paired radial-wheel handoff. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class PlayerRelationshipClientEvents {
    private static long nextSwitchNanos;

    private PlayerRelationshipClientEvents() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() != InputConstants.PRESS
                || minecraft.screen != null
                || minecraft.player == null) {
            return;
        }
        // Body control is a peer of Changed's ability wheel and Synergy's
        // relationship wheel. The shared wheel key routes here only for an
        // ordinary latex takeover; forced exoskeleton control has no wheel.
        if (TakeoverClientState.tryOpenSharedWheel(
                event.getKey(), event.getScanCode(), -1)) {
            return;
        }
        if (TakeoverClientState.active()
                || minecraft.player.isSpectator()
                || ProcessTransfur.getPlayerTransfurVariant(minecraft.player) != null
                || !ChangedKeyMappings.SELECT_ABILITY.isActiveAndMatches(
                        InputConstants.getKey(event.getKey(), event.getScanCode()))) {
            return;
        }
        ChangedSynergyNetwork.CHANNEL.sendToServer(
                new PlayerRelationshipOpenPacket());
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        var player = Minecraft.getInstance().player;
        // The server forbids relationship menus in spectator mode. Do not close
        // the native ability wheel for a handoff that it cannot acknowledge.
        if (event.getScrollDelta() == 0.0D || player == null || player.isSpectator()) {
            return;
        }

        Runnable switchWheel;
        if (event.getScreen() instanceof AbilityRadialScreen) {
            switchWheel = () -> ChangedSynergyNetwork.CHANNEL.sendToServer(
                    new PlayerRelationshipOpenPacket());
        } else if (event.getScreen() instanceof PlayerRelationshipScreen screen
                && screen.menu.isTransfurred()) {
            switchWheel = () -> {
                CompoundTag payload = new CompoundTag();
                payload.putString("command", "open_ability_wheel");
                screen.menu.setDirty(payload);
            };
        } else {
            return;
        }

        event.setCanceled(true);
        if (System.nanoTime() < nextSwitchNanos) {
            return;
        }
        nextSwitchNanos = System.nanoTime() + 260_000_000L;
        RadialWheelAnimations.requestWheelSwitch(
                event.getScreen(), switchWheel);
    }

    @SubscribeEvent
    public static void onScreenRendered(ScreenEvent.Render.Post event) {
        if (!(event.getScreen() instanceof AbilityRadialScreen screen)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.player.isSpectator()) return;
        Component hint=RadialWheelAnimations.scrollHint(screen,"menu.changed_synergy.relationship.switch_to_relationships");
        GuiGraphics graphics = event.getGuiGraphics();
        var lines = minecraft.font.split(hint, Math.max(80, screen.width - 24));
        int y = screen.height - 8 - lines.size() * 10;
        float alpha = RadialWheelAnimations.overallAlpha(screen);
        int textAlpha = Math.max(0, Math.min(
                255, Math.round(0xF0 * alpha)));
        // Minecraft's font treats very small alpha values as opaque.
        if (textAlpha < 4) return;
        for (var line : lines) {
            graphics.drawString(minecraft.font, line, (screen.width - minecraft.font.width(line)) / 2, y,
                    textAlpha << 24 | 0x00FFFFFF, true);
            y += 10;
        }
    }

}
