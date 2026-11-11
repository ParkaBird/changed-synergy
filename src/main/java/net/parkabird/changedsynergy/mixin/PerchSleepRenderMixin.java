package net.parkabird.changedsynergy.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.ai.PerchSleepService;
import net.parkabird.changedsynergy.block.PerchBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class PerchSleepRenderMixin {
    // Vanilla's sleeping-eye offset would lower an upright bird into the beam.
    @ModifyExpressionValue(method={"render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V","m_7392_(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"},
            at={@At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;getBedOrientation()Lnet/minecraft/core/Direction;"),
                @At(value="INVOKE",target="Lnet/minecraft/world/entity/LivingEntity;m_21259_()Lnet/minecraft/core/Direction;")},remap=false,require=1)
    private net.minecraft.core.Direction changedSynergy$noBedEyeOffset(net.minecraft.core.Direction original,LivingEntity entity){
        return PerchSleepService.perch(entity)!=null?null:original;
    }
    @Inject(method={"setupRotations","m_7523_"},at=@At("HEAD"),cancellable=true,remap=false,require=1)
    private void changedSynergy$uprightBird(LivingEntity entity,PoseStack pose,float age,float yaw,float partial,CallbackInfo ci){
        var pos=PerchSleepService.perch(entity);if(pos==null)return;
        pose.mulPose(Axis.YP.rotationDegrees(180-entity.level().getBlockState(pos).getValue(PerchBlock.FACING).toYRot()));ci.cancel();
    }
}
