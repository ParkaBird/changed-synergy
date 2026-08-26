package net.parkabird.changedsynergy.client;

import net.minecraft.nbt.CompoundTag;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Switches the bonded function and social wheels with either scroll direction. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class BondedWheelSwitchClientEvents {
    private static long nextSwitchNanos;

    private BondedWheelSwitchClientEvents() {
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        if (event.getScrollDelta() == 0.0D) {
            return;
        }

        Runnable switchWheel;
        if (event.getScreen() instanceof BondedLatexScreen screen) {
            switchWheel = () -> send(screen.menu, "open_social_wheel");
        } else if (event.getScreen() instanceof SocialInteractionScreen screen
                && screen.menu.isBondedMode()) {
            switchWheel = () -> send(screen.menu, "open_function_wheel");
        } else {
            return;
        }

        event.setCanceled(true);
        if (System.nanoTime() < nextSwitchNanos) {
            return;
        }
        nextSwitchNanos = System.nanoTime() + 250_000_000L;
        RadialWheelAnimations.requestWheelSwitch(
                event.getScreen(), switchWheel);
    }

    private static void send(
            net.ltxprogrammer.changed.world.inventory.UpdateableMenu menu,
            String command) {
        CompoundTag payload = new CompoundTag();
        payload.putString("command", command);
        menu.setDirty(payload);
    }
}
