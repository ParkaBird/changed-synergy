package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.platform.InputConstants;
import java.lang.reflect.Field;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
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
    private static Field viewOffsetField;

    private PlayerRelationshipClientEvents() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (event.getAction() != InputConstants.PRESS
                || minecraft.screen != null
                || minecraft.player == null
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
        if (event.getScrollDelta() == 0.0D) {
            return;
        }

        Runnable switchWheel;
        if (event.getScreen() instanceof AbilityRadialScreen ability) {
            if (!atAbilityBoundary(ability, event.getScrollDelta())) {
                return;
            }
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
        Component hint = Component.translatable(
                "menu.changed_synergy.relationship.switch_to_relationships");
        GuiGraphics graphics = event.getGuiGraphics();
        int textWidth = minecraft.font.width(hint);
        int x = (screen.width - textWidth) / 2;
        int y = screen.height - 18;
        float alpha = RadialWheelAnimations.overallAlpha(screen);
        int backgroundAlpha = Math.max(0, Math.min(
                255, Math.round(0x78 * alpha)));
        int textAlpha = Math.max(0, Math.min(
                255, Math.round(0xF0 * alpha)));
        graphics.fill(x - 5, y - 3, x + textWidth + 5, y + 11,
                backgroundAlpha << 24);
        graphics.drawString(minecraft.font, hint, x, y,
                textAlpha << 24 | 0x00FFFFFF, true);
    }

    /** Preserve native paging; switch only after the user scrolls past an edge. */
    private static boolean atAbilityBoundary(
            AbilityRadialScreen screen,
            double delta) {
        int offset = viewOffset(screen);
        if (delta < 0.0D) {
            return offset + 8 >= screen.getCount();
        }
        return offset <= 0;
    }

    private static int viewOffset(AbstractRadialScreen<?> screen) {
        try {
            if (viewOffsetField == null) {
                viewOffsetField = AbstractRadialScreen.class
                        .getDeclaredField("viewOffset");
                viewOffsetField.setAccessible(true);
            }
            return viewOffsetField.getInt(screen);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 0;
        }
    }
}
