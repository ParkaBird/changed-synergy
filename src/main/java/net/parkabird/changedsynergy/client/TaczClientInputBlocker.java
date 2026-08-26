package net.parkabird.changedsynergy.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Releases TACZ's independent key mappings without taking a hard dependency on TACZ. */
@OnlyIn(Dist.CLIENT)
public final class TaczClientInputBlocker {
    private static final String TACZ = "tacz";
    private static final Binding[] BINDINGS = {
        new Binding("ShootKey", "SHOOT_KEY", "shootControllerTick"),
        new Binding("AimKey", "AIM_KEY", "onAimControllerPress"),
        new Binding("ReloadKey", "RELOAD_KEY", "onReloadControllerPress"),
        new Binding("MeleeKey", "MELEE_KEY", "onMeleeControllerPress"),
        new Binding("FireSelectKey", "FIRE_SELECT_KEY", "onFireSelectControllerPress"),
        new Binding("InteractKey", "INTERACT_KEY", "onInteractControllerPress"),
        new Binding("InspectKey", "INSPECT_KEY", "onInspectControllerPress"),
        new Binding("ZoomKey", "ZOOM_KEY", "onZoomControllerPress"),
        new Binding("CrawlKey", "CRAWL_KEY", "onCrawlControllerPress"),
        new Binding("RefitKey", "REFIT_KEY", null)
    };
    private static final List<KeyMapping> KEYS = new ArrayList<>();
    private static final List<Method> CONTROLLER_RELEASES = new ArrayList<>();
    private static boolean initialized;
    private static boolean available;

    private TaczClientInputBlocker() {
    }

    public static void suppress() {
        initialize();
        if (!available) {
            return;
        }
        for (KeyMapping key : KEYS) {
            key.setDown(false);
            while (key.consumeClick()) {
                // Drain input queued before hypnosis took control.
            }
        }
        for (int index = CONTROLLER_RELEASES.size() - 1; index >= 0; index--) {
            Method method = CONTROLLER_RELEASES.get(index);
            try {
                method.invoke(null, false);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                CONTROLLER_RELEASES.remove(index);
                ChangedSynergyMod.LOGGER.debug("Could not release a TACZ controller input", exception);
            }
        }
    }

    public static boolean matches(int keyCode, int scanCode) {
        initialize();
        if (!available) {
            return false;
        }
        for (KeyMapping key : KEYS) {
            if (key.matches(keyCode, scanCode)) {
                return true;
            }
        }
        return false;
    }

    private static void initialize() {
        if (initialized) {
            return;
        }
        initialized = true;
        if (!ModList.get().isLoaded(TACZ)) {
            return;
        }
        ClassLoader loader = TaczClientInputBlocker.class.getClassLoader();
        for (Binding binding : BINDINGS) {
            try {
                Class<?> type = Class.forName(
                        "com.tacz.guns.client.input." + binding.className(),
                        false,
                        loader);
                Field keyField = type.getField(binding.keyField());
                if (keyField.get(null) instanceof KeyMapping key) {
                    KEYS.add(key);
                }
                if (binding.controllerMethod() != null) {
                    CONTROLLER_RELEASES.add(type.getMethod(
                            binding.controllerMethod(),
                            boolean.class));
                }
            } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
                ChangedSynergyMod.LOGGER.debug(
                        "TACZ input binding {} is unavailable",
                        binding.className(),
                        exception);
            }
        }
        available = !KEYS.isEmpty();
    }

    private record Binding(String className, String keyField, String controllerMethod) {
    }
}
