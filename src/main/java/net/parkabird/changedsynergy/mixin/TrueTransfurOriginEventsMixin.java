package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "com.lugert.true_transfur.transfur.TransfurEvents", remap = false)
public abstract class TrueTransfurOriginEventsMixin {
    // The supplied 2.0 runtime checks the form inside its player-tick lambda.
    // Broaden only control initialization, leaving neutrality, death and rebirth on the native policy.
    @Redirect(method = {"lambda$playerTick$0", "setMode"}, at = @At(value = "INVOKE",
            target = "Lcom/lugert/true_transfur/transfur/TransfurController;isChangedTransfurred(Lnet/minecraft/server/level/ServerPlayer;)Z"), require = 0, remap = false)
    private static boolean changedSynergy$controlForm(ServerPlayer player) {
        return NativeOriginPolicy.allowsInstinct(player) && ProcessTransfur.getPlayerTransfurVariantSafe(player)
                .map(form -> NativeOriginPolicy.allows(player) || !form.isTemporaryFromSuit()).orElse(false);
    }
    @Redirect(method = "setMode", at = @At(value = "INVOKE",
            target = "Lcom/lugert/true_transfur/transfur/SynergyCompat;isNativeAllowed(Lnet/minecraft/world/entity/player/Player;)Z"), require = 0, remap = false)
    private static boolean changedSynergy$controlCommand(Player player) {
        return NativeOriginPolicy.allowsInstinct(player);
    }
    @Inject(method = "nameFormat", at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$keepHumanName(PlayerEvent.NameFormat event, CallbackInfo callback) {
        if (!NativeOriginPolicy.allows(event.getEntity())) callback.cancel();
    }
}
