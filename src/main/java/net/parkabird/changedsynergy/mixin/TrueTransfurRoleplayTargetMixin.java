package net.parkabird.changedsynergy.mixin;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.InstinctConfinementService;
import net.parkabird.changedsynergy.ai.NativeRoleplayService;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Pseudo
@Mixin(targets="com.lugert.true_transfur.transfur.InstinctRuntime",remap=false)
public abstract class TrueTransfurRoleplayTargetMixin {
    @Inject(method="canAcceptTarget",at=@At("HEAD"),cancellable=true,require=0,remap=false)
    private static void changedSynergy$nativeHumanDisposition(ChangedEntity body,LivingEntity target,CallbackInfoReturnable<Boolean> callback) {
        ServerPlayer owner=body==null?null:TrueTransfurCompat.bodyOwner(body);
        if(owner!=null&&NativeRoleplayService.active(owner)&&target instanceof ServerPlayer human)
            callback.setReturnValue(InstinctConfinementService.canTarget(body,human));
    }
}
