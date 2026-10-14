package net.parkabird.changedsynergy.mixin;

import java.util.function.Consumer;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.origin.NativeOriginService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Guard before TrueTransfur's lethal reversal hook, at the common setter. */
@Mixin(value = ProcessTransfur.class, remap = false, priority = 2000)
public abstract class NativeOriginReversalMixin {
    @Inject(method = "setPlayerTransfurVariant(Lnet/minecraft/world/entity/player/Player;Lnet/ltxprogrammer/changed/entity/variant/TransfurVariant;Lnet/ltxprogrammer/changed/entity/TransfurContext;FZLjava/util/function/Consumer;)Lnet/ltxprogrammer/changed/entity/variant/TransfurVariantInstance;",
            at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$keepNativeBody(Player player, TransfurVariant<?> form,
            TransfurContext context, float progress, boolean temporary,
            Consumer<TransfurVariantInstance<?>> before,
            CallbackInfoReturnable<TransfurVariantInstance<?>> callback) {
        if (form == null && player instanceof ServerPlayer server && server.isAlive()
                && NativeOriginService.isNative(server) && NativeOriginService.enabled(server)) {
            callback.setReturnValue(ProcessTransfur.getPlayerTransfurVariant(player));
        }
    }
}
