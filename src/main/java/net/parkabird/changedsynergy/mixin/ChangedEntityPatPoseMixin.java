package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.world.entity.HumanoidArm;
import net.parkabird.changedsynergy.client.PatAnimationClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Uses the same synchronized third-person pat stroke on Changed creature models. */
@Mixin(AdvancedHumanoidModel.class)
public abstract class ChangedEntityPatPoseMixin {
    @Inject(method = {
                "setupAnim(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFF)V",
                "m_6973_(Lnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFF)V"
            },
            at = @At("TAIL"))
    private void changedSynergy$patRightArm(ChangedEntity creature,
            float walkPosition, float walkSpeed, float age, float headYaw,
            float headPitch, CallbackInfo callback) {
        AdvancedHumanoidModel<?> model =
                (AdvancedHumanoidModel<?>)(Object)this;
        PatAnimationClientState.applyRightArmPose(
                creature.getId(), age - creature.tickCount,
                model.getArm(HumanoidArm.RIGHT));
    }
}
