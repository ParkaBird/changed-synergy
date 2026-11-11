package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import net.parkabird.changedsynergy.client.model.PerchSleepPose;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value=AdvancedHumanoidModel.class,remap=false)
public abstract class PerchSleepModelMixin {
    @Inject(method="setupAnim(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFF)V",at=@At("TAIL"))
    private void changedSynergy$foldForSleep(ChangedEntity entity,float swing,float amount,float age,float yaw,float pitch,CallbackInfo ci){
        if(PerchSleepService.bird(entity)&&PerchSleepService.perch(entity)!=null){
            var model=(AdvancedHumanoidModel)(Object)this;PerchSleepPose.apply(model);model.syncPropertyModel(entity);
        }
    }
}
