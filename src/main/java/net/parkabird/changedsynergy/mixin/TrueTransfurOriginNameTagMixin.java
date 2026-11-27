package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.lugert.true_transfur.client.TransfurNameTagEvents", remap = false)
public abstract class TrueTransfurOriginNameTagMixin {
    @Inject(method = "onNameFormat", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private static void changedSynergy$ordinaryName(RenderNameTagEvent event, CallbackInfo callback) {
        if (net.parkabird.changedsynergy.client.NameVisibilityClientEvents.hidesNativePlayerName(event.getEntity())) {
            event.setResult(net.minecraftforge.eventbus.api.Event.Result.DENY);
            callback.cancel();
            return;
        }
        Player player = event.getEntity() instanceof Player direct ? direct
                : event.getEntity() instanceof ChangedEntity form ? form.getUnderlyingPlayer() : null;
        if (player != null && !NativeOriginPolicy.allows(player)) callback.cancel();
    }
}
