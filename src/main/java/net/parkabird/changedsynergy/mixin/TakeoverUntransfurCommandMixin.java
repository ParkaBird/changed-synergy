package net.parkabird.changedsynergy.mixin;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ai.TakeoverService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Lets Changed's two untransfur command aliases recover a takeover session. */
@Mixin(targets = "net.ltxprogrammer.changed.command.CommandTransfur", remap = false)
public abstract class TakeoverUntransfurCommandMixin {
    @Inject(
            method = "untransfurPlayer(Lnet/minecraft/commands/CommandSourceStack;"
                    + "Lnet/minecraft/server/level/ServerPlayer;)I",
            at = @At("HEAD"), cancellable = true, require = 1)
    private static void changedSynergy$releaseTakeover(
            CommandSourceStack source,
            ServerPlayer player,
            CallbackInfoReturnable<Integer> callback) {
        if (TakeoverService.active(player)) {
            callback.setReturnValue(
                    TakeoverService.forceReleaseByCommand(player) ? 1 : 0);
        }
    }
}
