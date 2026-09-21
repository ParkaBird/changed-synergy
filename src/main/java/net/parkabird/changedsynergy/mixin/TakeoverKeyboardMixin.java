package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public abstract class TakeoverKeyboardMixin {
    @Inject(method = {"keyPress", "m_90893_"}, at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$key(long window, int key, int scan, int action, int mods, CallbackInfo ci) {
        if (window != Minecraft.getInstance().getWindow().getWindow()) return;
        if (action == GLFW.GLFW_PRESS) {
            // Let Forge publish the shared radial-wheel key through its normal
            // input event. Canceling here prevented the event subscriber from
            // ever seeing the key on some production clients.
            if (TakeoverClientState.canOpen()
                    && TakeoverClientState.isSharedWheelInput(key, scan, -1)) {
                return;
            }
            TakeoverClientState.press(key, scan, -1);
        }
        if (TakeoverClientState.blockKey(key, scan)) ci.cancel();
    }
}
