package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.ltxprogrammer.changed.client.renderer.layers.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={CustomEyesLayer.class,AdditionalEyesLayer.class},remap=false)
public abstract class SleepingSynergyEyesMixin {
    @Inject(method="render(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/ltxprogrammer/changed/entity/ChangedEntity;FFFFFF)V",at=@At("HEAD"),cancellable=true)
    private void changedSynergy$closedEyes(PoseStack pose,MultiBufferSource buffers,int light,ChangedEntity entity,float swing,float amount,float partial,float age,float yaw,float pitch,CallbackInfo ci){
        var id=ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if(entity.isSleeping()&&id!=null&&id.getNamespace().equals("changed_synergy"))ci.cancel();
    }
}
