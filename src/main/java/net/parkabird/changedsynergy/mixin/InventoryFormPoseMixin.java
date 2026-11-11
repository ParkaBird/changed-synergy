package net.parkabird.changedsynergy.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.client.InventoryFormRenderState;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InventoryScreen.class)
public abstract class InventoryFormPoseMixin {
    @Inject(method={"renderEntityInInventory", "m_280432_"},at=@At("HEAD"),remap=false,require=1)
    private static void changedSynergy$beginPreview(GuiGraphics graphics,int x,int y,int scale,Quaternionf rotation,Quaternionf camera,LivingEntity entity,CallbackInfo ci){InventoryFormRenderState.entity=entity;}
    @Inject(method={"renderEntityInInventory", "m_280432_"},at=@At("RETURN"),remap=false,require=1)
    private static void changedSynergy$endPreview(CallbackInfo ci){InventoryFormRenderState.entity=null;}
}
