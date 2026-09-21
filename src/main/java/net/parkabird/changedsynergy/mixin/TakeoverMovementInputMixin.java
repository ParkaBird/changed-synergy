package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public abstract class TakeoverMovementInputMixin {
    @Inject(method = {"tick", "m_214106_"}, at = @At("TAIL"), remap = false)
    private void takeover$movement(boolean slow, float multiplier, CallbackInfo ci) {
        if (!TakeoverClientState.movementLocked()) return;
        Input input = (Input)(Object)this;
        input.leftImpulse = input.forwardImpulse = 0;
        input.up = input.down = input.left = input.right = input.jumping = input.shiftKeyDown = false;
    }
}
