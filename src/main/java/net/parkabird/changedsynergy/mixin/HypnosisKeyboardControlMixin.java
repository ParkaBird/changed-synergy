package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Stops keyboard-bound attack/use and TACZ actions before their input events fire. */
@Mixin(KeyboardHandler.class)
public abstract class HypnosisKeyboardControlMixin {
    @Inject(
            method = {"keyPress", "m_90893_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$blockHypnotizedActionKeys(
            long window,
            int keyCode,
            int scanCode,
            int action,
            int modifiers,
            CallbackInfo callback) {
        if (Minecraft.getInstance().screen == null
                && HypnosisQteClientState.shouldBlockKeyInput(keyCode, scanCode)) {
            callback.cancel();
        }
    }
}
