package net.parkabird.changedsynergy.mixin;

import java.util.function.Consumer;
import net.ltxprogrammer.changed.entity.TransfurContext;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.parkabird.changedsynergy.ai.InvoluntaryTransfurNegotiation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Observe actual outcomes, after other mods have had a chance to reject a reversal. */
@Mixin(value = ProcessTransfur.class, remap = false)
public abstract class NegotiationFormLifecycleMixin {
    @Inject(method = "setPlayerTransfurVariant(Lnet/minecraft/world/entity/player/Player;Lnet/ltxprogrammer/changed/entity/variant/TransfurVariant;Lnet/ltxprogrammer/changed/entity/TransfurContext;FZLjava/util/function/Consumer;)Lnet/ltxprogrammer/changed/entity/variant/TransfurVariantInstance;",
            at = @At("RETURN"), remap = false)
    private static void changedSynergy$validateNegotiation(Player player, TransfurVariant<?> form,
            TransfurContext context, float progress, boolean temporary,
            Consumer<TransfurVariantInstance<?>> before,
            CallbackInfoReturnable<TransfurVariantInstance<?>> callback) {
        if (player instanceof ServerPlayer server) {
            InvoluntaryTransfurNegotiation.onFormAssigned(server);
        }
    }
}
