package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Prevents mouse movement from pulling the camera away during hypnosis. */
@Mixin(MouseHandler.class)
public abstract class HypnosisMouseControlMixin {
    @Inject(
            method = {"turnPlayer", "m_91523_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$lockHypnotizedView(CallbackInfo callback) {
        if (HypnosisQteClientState.isControlLocked()) {
            callback.cancel();
        }
    }

    /**
     * TACZ owns separate mouse key mappings, so blocking Minecraft's attack/use
     * methods alone does not stop a shot. Reject gameplay button events before
     * either vanilla or TACZ can queue them, while still allowing screen clicks.
     */
    @Inject(
            method = {"onPress", "m_91530_"},
            at = @At("HEAD"),
            cancellable = true,
            require = 0,
            remap = false)
    private void changedSynergy$blockHypnotizedMouseActions(
            long window,
            int button,
            int action,
            int modifiers,
            CallbackInfo callback) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen == null
                && (button == 0 || button == 1)
                && HypnosisQteClientState.isControlLocked()) {
            callback.cancel();
        }
    }
}
