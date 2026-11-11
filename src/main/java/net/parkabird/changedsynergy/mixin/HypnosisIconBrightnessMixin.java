package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.ability.AbstractAbilityInstance;
import net.ltxprogrammer.changed.client.AbilityRenderer;
import net.parkabird.changedsynergy.ability.HypnosisToggle;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value=AbilityRenderer.class,remap=false)
public abstract class HypnosisIconBrightnessMixin {
    // The model renderer resets shader color; tint its vertices rather than the surrounding GUI.
    @WrapOperation(method="renderGuiAbility(Lnet/minecraft/client/gui/GuiGraphics;Lnet/ltxprogrammer/changed/ability/AbstractAbilityInstance;IIIFZLnet/minecraft/client/resources/model/BakedModel;)V",at=@At(value="INVOKE",target="Lnet/ltxprogrammer/changed/client/AbilityRenderer;render(Lnet/ltxprogrammer/changed/ability/AbstractAbilityInstance;Lnet/minecraft/world/item/ItemDisplayContext;ZLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;IILnet/minecraft/client/resources/model/BakedModel;FFFF)V"))
    private void changedSynergy$brightness(AbilityRenderer renderer, AbstractAbilityInstance ability, ItemDisplayContext context,
            boolean leftHand, PoseStack pose, MultiBufferSource buffer, int light, int overlay, BakedModel model,
            float red, float green, float blue, float alpha, Operation<Void> original){
        float brightness=((HypnosisToggle)ability.getController()).changedSynergy$hypnosisBrightness();
        original.call(renderer,ability,context,leftHand,pose,buffer,light,overlay,model,red*brightness,green*brightness,blue*brightness,alpha);
    }
}
