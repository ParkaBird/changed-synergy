package net.parkabird.changedsynergy.mixin;

import net.minecraftforge.network.simple.SimpleChannel;
import net.parkabird.changedsynergy.client.TakeoverClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Narrow send guard also covers abilities activated from an already-open radial screen. */
@Mixin(value = SimpleChannel.class, remap = false)
public abstract class TakeoverAbilityPacketMixin {
    @Inject(method = "sendToServer", at = @At("HEAD"), cancellable = true, remap = false)
    private void takeover$ability(Object message, CallbackInfo ci) {
        if (!TakeoverClientState.active() || message == null) return;
        String name = message.getClass().getName();
        if (name.equals("net.ltxprogrammer.changed.network.VariantAbilityActivate")
                || name.equals("net.ltxprogrammer.changed.network.packet.AbilityPayloadPacket")
                || name.equals("net.ltxprogrammer.changed.network.packet.SyncVariantAbilityPacket")
                || name.equals("net.ltxprogrammer.changed.network.packet.GrabEntityPacket")
                || name.startsWith("net.ltxprogrammer.changed.network.packet.GrabEntityPacket$")
                || (TakeoverClientState.movementLocked()
                    && name.equals("net.ltxprogrammer.changed.network.ExtraJumpKeybind"))) ci.cancel();
    }
}
