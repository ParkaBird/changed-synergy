package net.parkabird.changedsynergy.mixin;

import java.util.function.Supplier;
import net.ltxprogrammer.changed.network.ExtraJumpKeybind;
import net.minecraftforge.network.NetworkEvent;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The legacy jump packet does not implement ChangedPacket. */
@Mixin(value = ExtraJumpKeybind.class, remap = false)
public abstract class TakeoverExtraJumpPacketMixin {
    @Inject(method = "handler", at = @At("HEAD"), cancellable = true, require = 1)
    private static void synergy$validate(ExtraJumpKeybind message,
            Supplier<NetworkEvent.Context> supplier, CallbackInfo callback) {
        NetworkEvent.Context context = supplier.get();
        var player = context.getSender();
        if (player == null) return;
        if (!player.server.isSameThread()) {
            context.enqueueWork(() -> ExtraJumpKeybind.handler(message, () -> context));
            context.setPacketHandled(true);
            callback.cancel();
        } else if (TakeoverService.active(player)) {
            context.setPacketHandled(true);
            callback.cancel();
        }
    }
}
