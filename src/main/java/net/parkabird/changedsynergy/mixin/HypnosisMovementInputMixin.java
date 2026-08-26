package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.parkabird.changedsynergy.client.HypnosisQteClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Consumes movement keys for the QTE without allowing them to move or jump. */
@Mixin(KeyboardInput.class)
public abstract class HypnosisMovementInputMixin {
    @Inject(
            method = {"tick", "m_214106_"},
            at = @At("TAIL"),
            require = 0,
            remap = false)
    private void changedSynergy$consumeMovementInput(
            boolean slowDown,
            float sneakSpeedMultiplier,
            CallbackInfo callback) {
        if (!HypnosisQteClientState.isControlLocked()) {
            return;
        }
        Input input = (Input)(Object)this;
        input.leftImpulse = 0.0F;
        input.forwardImpulse = 0.0F;
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
        input.jumping = false;
    }
}
