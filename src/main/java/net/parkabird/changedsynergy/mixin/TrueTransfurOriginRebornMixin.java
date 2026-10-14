package net.parkabird.changedsynergy.mixin;

import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.parkabird.changedsynergy.origin.NativeOriginPolicy;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** TrueTransfur 2.0's rebirth is subject to the same origin policy as its control API. */
@Pseudo
@Mixin(targets = "com.lugert.true_transfur.transfur.RebornHandler", remap = false)
public abstract class TrueTransfurOriginRebornMixin {
    @Shadow @Final private static Map<UUID, Integer> HEAL_LATER;
    @Shadow public static void clear(ServerPlayer player) { throw new AssertionError(); }

    @Inject(method = "interceptDeath", at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$nativeRebirth(LivingDeathEvent event, ServerPlayer player, CallbackInfoReturnable<Boolean> callback) {
        if (!NativeOriginPolicy.allows(player)) callback.setReturnValue(false);
    }

    @Inject(method = {"tick", "onLogin", "handlePress"}, at = @At("HEAD"), cancellable = true, remap = false)
    private static void changedSynergy$clearHumanRebirth(ServerPlayer player, CallbackInfo callback) {
        if (NativeOriginPolicy.allows(player)) return;
        if (player.getPersistentData().contains("TrueTransfurReborn")) clear(player);
        HEAL_LATER.remove(player.getUUID());
        callback.cancel();
    }
}
