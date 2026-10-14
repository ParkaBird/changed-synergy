package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ai.AssimilationBehavior;
import net.ltxprogrammer.changed.entity.ai.LatexAssimilationDecision;
import net.minecraft.server.level.ServerPlayer;
import net.parkabird.changedsynergy.ai.TakeoverAssimilationBehavior;
import net.parkabird.changedsynergy.ai.TakeoverService;
import net.parkabird.changedsynergy.compat.SurvivalProtocolAssimilation;
import net.parkabird.changedsynergy.compat.SurvivalProtocolCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets="net.stonenibbler.changed_survive_protocol.common.event.CSPTransfurEvents",remap=false)
public abstract class SurvivalProtocolTakeoverMixin {
    @Inject(method="wrapLatexProgressBehavior",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void changedSynergy$preserveManagedProgress(ServerPlayer player, LatexAssimilationDecision<?> decision,
            AssimilationBehavior behavior, CallbackInfoReturnable<AssimilationBehavior> callback) {
        if (behavior instanceof TakeoverAssimilationBehavior takeover && takeover.managesNativeProgress())
            callback.setReturnValue(new SurvivalProtocolAssimilation(player,behavior));
    }
    @Inject(method="canRedirectTransfur",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void changedSynergy$scopedProgress(ServerPlayer player,CallbackInfoReturnable<Boolean> callback) {
        if (SurvivalProtocolCompat.nativeProgress(player)) callback.setReturnValue(false);
    }
    @Inject(method="forceUncontrolledTransfur",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void changedSynergy$keepTemporarySuit(ServerPlayer player,@org.spongepowered.asm.mixin.injection.Coerce Object data,CallbackInfo callback) {
        if (TakeoverService.active(player)) callback.cancel();
    }
    @Inject(method="onEntityVariantAssigned",at=@At("HEAD"),cancellable=true,remap=false,require=0)
    private static void changedSynergy$originAssignment(net.ltxprogrammer.changed.process.ProcessTransfur.EntityVariantAssigned event,CallbackInfo callback) {
        if (event.livingEntity instanceof ServerPlayer player && SurvivalProtocolCompat.originChange(player)) callback.cancel();
    }
}
