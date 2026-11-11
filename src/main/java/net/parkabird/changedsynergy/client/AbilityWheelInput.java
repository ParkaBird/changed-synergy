package net.parkabird.changedsynergy.client;

import java.util.Optional;
import com.mojang.blaze3d.platform.InputConstants;
import net.ltxprogrammer.changed.client.gui.AbilityRadialScreen;
import net.ltxprogrammer.changed.util.SingleRunnable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/** One physical press owns at most one ability choice; relationship and child screens stay click-driven. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class AbilityWheelInput {
    private static final long HOLD_NANOS = 150_000_000L;
    private static long pressedAt, ignoreLateUntil;
    private static Object level;
    private static AbilityRadialScreen wheel;
    private static AbilityRadialScreen closingWheel;
    private static boolean down, switching, mustMove;
    private static double anchorX, anchorY;
    private static final boolean[] movementDown = new boolean[7];
    private AbilityWheelInput() {}

    private static void input(int key, int scan, int button, int action) {
        if (!TakeoverClientState.isSharedWheelInput(key, scan, button) || action == GLFW.GLFW_REPEAT) return;
        var mc = Minecraft.getInstance();
        if (action == GLFW.GLFW_PRESS) {
            if (down || closingWheel != null || mc.player == null || mc.level == null || mc.screen != null && !playerWheel(mc.screen)) return;
            RadialWheelAnimations.clearCancelledArrival();
            down = true; switching = mc.screen instanceof PlayerRelationshipScreen; pressedAt = System.nanoTime(); level = mc.level; ignoreLateUntil = 0;
            var keys = movementKeys();
            for (int i = 0; i < keys.length; i++) {
                var binding = keys[i].getKey();
                movementDown[i] = binding.getType() == InputConstants.Type.MOUSE
                        ? GLFW.glfwGetMouseButton(mc.getWindow().getWindow(), binding.getValue()) == GLFW.GLFW_PRESS
                        : binding.getType() == InputConstants.Type.KEYSYM && binding.getValue() >= 0
                            && InputConstants.isKeyDown(mc.getWindow().getWindow(), binding.getValue());
            }
            if (mc.screen instanceof AbilityRadialScreen screen) attach(screen);
        } else if (action == GLFW.GLFW_RELEASE && down) release();
    }
    private static void attach(AbilityRadialScreen screen) {
        wheel = screen; mustMove = false;
        var mc = Minecraft.getInstance();
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), mc.getWindow().getScreenWidth() / 2.0, mc.getWindow().getScreenHeight() / 2.0);
    }
    public static boolean holding(AbilityRadialScreen screen) {
        return down && !switching && wheel == screen && System.nanoTime() - pressedAt >= HOLD_NANOS;
    }
    /** Present the gesture immediately, retaining the same hint through its closing animation. */
    public static boolean showHoldHint(AbilityRadialScreen screen) {
        return down && !switching && wheel == screen || closingWheel == screen;
    }
    public static Optional<Integer> selection(AbilityRadialScreen screen, int mouseX, int mouseY) {
        if (!holding(screen)) return Optional.empty();
        if (mustMove) {
            if (Math.hypot(mouseX - anchorX, mouseY - anchorY) < 3.0) return Optional.empty();
            mustMove = false;
        }
        int slot = direction(mouseX - screen.width * 0.5, mouseY - screen.height * 0.5);
        int section = slot + RadialWheelAnimations.viewOffset(screen);
        return slot < 0 || section >= screen.getCount() ? Optional.empty() : Optional.of(section);
    }
    public static int direction(double x, double y) {
        if (Math.hypot(x, y) < 22.0) return -1;
        double angle = Math.atan2(x, -y);
        if (angle < 0) angle += Math.PI * 2;
        return Math.min(7, (int)(angle / (Math.PI / 4)));
    }
    public static void pageChanged(AbilityRadialScreen screen) {
        if (wheel != screen || !down) return;
        var mc = Minecraft.getInstance();
        anchorX = mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth();
        anchorY = mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight();
        mustMove = true;
    }
    public static void switchStarted() { if (down) switching = true; }
    private static boolean playerWheel(Screen screen) {
        return screen instanceof AbilityRadialScreen || screen instanceof PlayerRelationshipScreen;
    }
    /** Mouse recapture polls all mappings between server menus; that is not another press. */
    public static boolean ownsPress() { return down; }
    public static boolean allowsMovement(KeyMapping key) {
        var mc = Minecraft.getInstance();
        if ((!down && closingWheel != mc.screen) || !playerWheel(mc.screen)
                || mc.level != level || mc.player == null || !mc.player.isAlive() || !mc.isWindowActive()) return false;
        return isMovementKey(key);
    }
    public static boolean isMovementKey(KeyMapping key) {
        var o = Minecraft.getInstance().options;
        return key == o.keyUp || key == o.keyDown || key == o.keyLeft || key == o.keyRight
                || key == o.keyJump || key == o.keyShift || key == o.keySprint;
    }
    private static KeyMapping[] movementKeys() {
        var o = Minecraft.getInstance().options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint};
    }
    private static boolean movementInput(int key, int scan, int button, boolean pressed) {
        var screen = Minecraft.getInstance().screen;
        if ((!down && (closingWheel == null || closingWheel != screen)) || screen != null && !playerWheel(screen)) return false;
        boolean matched = false;
        var keys = movementKeys();
        for (int i = 0; i < keys.length; i++) {
            if (!(button >= 0 ? keys[i].matchesMouse(button) : keys[i].matches(key, scan))) continue;
            if (screen != null && movementDown[i] != pressed) keys[i].setDown(pressed);
            movementDown[i] = pressed;
            matched = true;
        }
        return matched && screen != null;
    }
    private static void clear() { down = false; switching = false; wheel = closingWheel = null; level = null; mustMove = false; }
    private static void cancel() {
        boolean pending = down && wheel == null && !switching;
        clear();
        if (pending) ignoreLateUntil = System.nanoTime() + 3_000_000_000L;
    }
    public static void closing(AbilityRadialScreen screen) {
        if (wheel != screen) return;
        // Consume selection now; gameplay input belongs to this screen until it actually leaves.
        if (down && !switching && Minecraft.getInstance().screen == screen) closingWheel = screen;
        down = switching = mustMove = false;
        wheel = null;
        if (closingWheel == null) level = null;
    }
    private static void escape() {
        if (wheel != null && Minecraft.getInstance().screen == wheel && !switching) closing(wheel);
        else if (closingWheel == null) cancel();
    }
    private static void release() {
        var mc = Minecraft.getInstance();
        boolean held = System.nanoTime() - pressedAt >= HOLD_NANOS;
        var screen = wheel;
        Optional<Integer> chosen = held && !switching && mc.screen == screen && screen != null
                && (!(screen instanceof RadialWheelAnimationAccess motion) || motion.changedSynergy$canConfirm())
                ? selection(screen, (int)(mc.mouseHandler.xpos() * screen.width / mc.getWindow().getScreenWidth()),
                        (int)(mc.mouseHandler.ypos() * screen.height / mc.getWindow().getScreenHeight())) : Optional.empty();
        boolean waiting = screen == null && mc.screen == null;
        boolean clickMode = switching;
        if (held && !clickMode && mc.player != null && mc.screen == screen && screen != null) closing(screen);
        else clear();
        if (!held || mc.player == null) return;
        if (waiting) { ignoreLateUntil = System.nanoTime() + 3_000_000_000L; return; }
        if (clickMode) return;
        if (mc.screen != screen || screen == null) return;
        if (chosen.isEmpty()) { RadialWheelAnimations.requestClose(screen, mc.player::closeContainer); return; }
        // Consume the gesture before an ability opens a nested menu.
        // AbilityRadialScreenCloseMixin already animates this native callback.
        SingleRunnable close = new SingleRunnable(mc.player::closeContainer);
        if (screen.handleClicked(chosen.get(), close)) close.run();
    }
    @SubscribeEvent public static void key(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_REPEAT) movementInput(event.getKey(), event.getScanCode(), -1, event.getAction() != GLFW.GLFW_RELEASE);
        if (event.getKey() == GLFW.GLFW_KEY_ESCAPE && event.getAction() == GLFW.GLFW_PRESS) escape();
        else input(event.getKey(), event.getScanCode(), -1, event.getAction());
    }
    @SubscribeEvent public static void mouse(InputEvent.MouseButton.Pre event) {
        if (Minecraft.getInstance().screen == null) movementInput(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton(), event.getAction() != GLFW.GLFW_RELEASE);
        input(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton(), event.getAction());
    }
    @SubscribeEvent public static void screenKey(ScreenEvent.KeyPressed.Pre event) {
        if (event.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) { escape(); return; }
        input(event.getKeyCode(), event.getScanCode(), -1, GLFW.GLFW_PRESS);
        if (playerWheel(event.getScreen()) && (TakeoverClientState.isSharedWheelInput(event.getKeyCode(), event.getScanCode(), -1)
                || movementInput(event.getKeyCode(), event.getScanCode(), -1, true))) event.setCanceled(true);
    }
    @SubscribeEvent public static void screenKeyUp(ScreenEvent.KeyReleased.Pre event) {
        movementInput(event.getKeyCode(), event.getScanCode(), -1, false);
        input(event.getKeyCode(), event.getScanCode(), -1, GLFW.GLFW_RELEASE);
    }
    @SubscribeEvent public static void screenMouse(ScreenEvent.MouseButtonPressed.Pre event) {
        boolean binding = TakeoverClientState.isSharedWheelInput(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton());
        if (binding && playerWheel(event.getScreen())) { input(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton(), GLFW.GLFW_PRESS); event.setCanceled(true); }
        else if (movementInput(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton(), true)) event.setCanceled(true);
        else if (down) cancel();
    }
    @SubscribeEvent public static void screenMouseUp(ScreenEvent.MouseButtonReleased.Pre event) {
        movementInput(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton(), false);
        input(GLFW.GLFW_KEY_UNKNOWN, 0, event.getButton(), GLFW.GLFW_RELEASE);
    }
    @SubscribeEvent public static void opening(ScreenEvent.Opening event) {
        var mc = Minecraft.getInstance();
        Screen next = event.getNewScreen();
        if (closingWheel != null && next != closingWheel) clear();
        if (RadialWheelAnimations.rejectCancelledArrival(next)) {
            event.setCanceled(true);
            mc.execute(() -> {
                if (mc.player != null && next instanceof net.ltxprogrammer.changed.client.gui.AbstractRadialScreen<?> radial
                        && mc.player.containerMenu == radial.menu) mc.player.closeContainer();
            });
            return;
        }
        if (next instanceof AbilityRadialScreen ability) {
            if (System.nanoTime() < ignoreLateUntil) {
                event.setCanceled(true);
                mc.execute(() -> { if (mc.player != null && mc.player.containerMenu == ability.menu) mc.player.closeContainer(); });
            } else if (down && !switching) attach(ability);
        } else if (down && next instanceof PlayerRelationshipScreen) {
            switching = true;
        } else if (down && switching && next == null) {
            // Server container handoffs briefly close the old screen before opening its peer.
        } else if (next != null || wheel != null) cancel();
    }
    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!down && closingWheel == null) return;
        var mc = Minecraft.getInstance();
        if (!mc.isWindowActive() || mc.player == null || !mc.player.isAlive() || mc.level != level) { cancel(); return; }
        if (closingWheel != null && mc.screen != closingWheel) { clear(); return; }
        if (down && mc.screen instanceof AbilityRadialScreen ability && wheel == null && !switching) attach(ability);
        else if (mc.screen != null && !playerWheel(mc.screen)) cancel();
        if ((down || closingWheel != null) && event.phase == TickEvent.Phase.START && playerWheel(mc.screen)) {
            var keys = movementKeys();
            for (int i = 0; i < keys.length; i++) {
                boolean toggle = keys[i] == mc.options.keyShift && mc.options.toggleCrouch().get()
                        || keys[i] == mc.options.keySprint && mc.options.toggleSprint().get();
                if (!toggle) keys[i].setDown(movementDown[i]);
            }
        }
        if (down && wheel == null && !switching && System.nanoTime() - pressedAt > 3_000_000_000L) cancel();
    }
}
