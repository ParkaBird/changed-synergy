package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.PatAnimationClientState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Adds the synchronized pat stroke to the rendered third-person player arm. */
@Mixin(PlayerModel.class)
public abstract class PlayerPatPoseMixin {
    @Inject(method = {
                "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
                "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"
            },
            at = @At("TAIL"))
    private void changedSynergy$patRightArm(LivingEntity player,
            float walkPosition, float walkSpeed, float age, float headYaw,
            float headPitch, CallbackInfo callback) {
        PlayerModel<?> model = (PlayerModel<?>)(Object)this;
        if (PatAnimationClientState.applyRightArmPose(
                player.getId(), age - player.tickCount, model.rightArm)) {
            model.rightSleeve.copyFrom(model.rightArm);
        }
    }
}
