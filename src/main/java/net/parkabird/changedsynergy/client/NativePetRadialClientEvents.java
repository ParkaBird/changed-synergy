package net.parkabird.changedsynergy.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import net.ltxprogrammer.changed.client.gui.AbstractRadialScreen;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.network.ChangedSynergyNetwork;
import net.parkabird.changedsynergy.network.NativePetReassimilatePacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Adds a right-click action without hard-loading Addon's optional screen classes. */
@Mod.EventBusSubscriber(
        modid = ChangedSynergyMod.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.FORGE,
        value = Dist.CLIENT)
public final class NativePetRadialClientEvents {
    private static final String CHANGED_SCREEN =
            "net.ltxprogrammer.changed.client.gui.TamedDarkLatexScreen";
    private static final String ADDON_SCREEN =
            "net.foxyas.changedaddon.client.gui.TamedLatexScreen";

    private NativePetRadialClientEvents() {
    }

    @SubscribeEvent
    public static void onNativePetWheelRightClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1
                || !(event.getScreen() instanceof AbstractRadialScreen<?> radial)) {
            return;
        }
        String screenName = event.getScreen().getClass().getName();
        if (!CHANGED_SCREEN.equals(screenName) && !ADDON_SCREEN.equals(screenName)) {
            return;
        }

        var player = Minecraft.getInstance().player;
        if (player == null || ProcessTransfur.isPlayerTransfurred(player)) {
            return;
        }
        var section = radial.getSectionAt((int)event.getMouseX(), (int)event.getMouseY());
        if (section.isEmpty()
                || !"favor_suit_owner".equals(interactionCommand(event.getScreen(), section.get()))) {
            return;
        }
        ChangedEntity pet = petFromMenu(radial.menu);
        if (pet == null) {
            return;
        }

        Minecraft.getInstance().getSoundManager().play(
                SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        ChangedSynergyNetwork.CHANNEL.sendToServer(
                new NativePetReassimilatePacket(pet.getId()));
        Runnable close = player::closeContainer;
        if (!RadialWheelAnimations.requestClose(radial, close)) {
            close.run();
        }
        event.setCanceled(true);
    }

    private static String interactionCommand(Object screen, int section) {
        try {
            Field field = findField(screen.getClass(), "availableInteractions");
            Object value = field.get(screen);
            if (!(value instanceof List<?> interactions)
                    || section < 0 || section >= interactions.size()) {
                return "";
            }
            Object interaction = interactions.get(section);
            Method command = interaction.getClass().getMethod("command");
            Object result = command.invoke(interaction);
            return result instanceof String string ? string : "";
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return "";
        }
    }

    private static ChangedEntity petFromMenu(Object menu) {
        for (String name : List.of("tamedDarkLatex", "tamedLatex")) {
            try {
                Field field = findField(menu.getClass(), name);
                Object value = field.get(menu);
                if (value instanceof ChangedEntity pet) {
                    return pet;
                }
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // The other native menu uses the other field name.
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                // Continue through transformed screen superclasses.
            }
        }
        throw new NoSuchFieldException(name);
    }
}
