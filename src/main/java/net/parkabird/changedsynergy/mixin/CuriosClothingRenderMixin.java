package net.parkabird.changedsynergy.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.ltxprogrammer.changed.client.renderer.layers.AccessoryLayer;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AccessoryLayer.class)
public abstract class CuriosClothingRenderMixin {
    @WrapOperation(method={"lambda$render$2","lambda$renderFirstPersonOnArms$6"},at=@At(value="INVOKE",target="Lnet/ltxprogrammer/changed/data/AccessorySlotType;canHoldItem(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/LivingEntity;)Z"),require=2,remap=false)
    private static boolean changedSynergy$renderToggle(AccessorySlotType type,ItemStack item,LivingEntity wearer,Operation<Boolean> original) {
        return original.call(type,item,wearer) && CuriosCompat.clothingVisible(wearer,type);
    }
}
