package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public abstract class TakeoverMouseMixin {
    @Inject(method = {"turnPlayer", "m_91523_"}, at = @At("HEAD"),
            cancellable = true, remap = false)
    private void takeover$look(CallbackInfo ci) {
        if (TakeoverClientState.movementLocked()
                && Minecraft.getInstance().screen == null) ci.cancel();
    }

    @Inject(method = {"onPress", "m_91530_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$button(long window, int button, int action, int mods, CallbackInfo ci) {
        var mc = Minecraft.getInstance();
        if (window != mc.getWindow().getWindow()) return;
        if (action == GLFW.GLFW_PRESS) {
            // Forge's cancellable mouse-pre event owns opening the shared
            // wheel; allow this one input to reach that event first.
            if (TakeoverClientState.canOpen()
                    && TakeoverClientState.isSharedWheelInput(-1, -1, button)) {
                return;
            }
            TakeoverClientState.press(-1, -1, button);
        }
        if (!TakeoverClientState.active() || mc.screen != null) return;
        if (!TakeoverClientState.movementLocked()) {
            for (var key : TakeoverClientState.movementKeys()) if (key.matchesMouse(button)) return;
            if (mc.options.keyJump.matchesMouse(button) || mc.options.keyShift.matchesMouse(button)
                    || mc.options.keySprint.matchesMouse(button)) return;
            if (mc.options.keyUse.matchesMouse(button)
                    && TakeoverClientState.allowsCurrentBedUse()) return;
        }
        ci.cancel();
    }
}
