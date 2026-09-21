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
        float phase = PatAnimationClientState.phase(player.getId(), age - player.tickCount);
        if (phase < 0.0F) {
            return;
        }
        PlayerModel<?> model = (PlayerModel<?>)(Object)this;
        float sweep = (float)Math.sin(phase * Math.PI * 2.0D);
        model.rightArm.xRot = -1.52F + 0.07F * sweep;
        model.rightArm.yRot = -0.14F + 0.10F * sweep;
        model.rightArm.zRot = 0.08F * sweep;
        model.rightSleeve.copyFrom(model.rightArm);
    }
}
