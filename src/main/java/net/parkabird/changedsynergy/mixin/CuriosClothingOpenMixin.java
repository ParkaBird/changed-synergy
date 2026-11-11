package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.world.inventory.AccessoryAccessMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AccessoryAccessMenu.class)
public abstract class CuriosClothingOpenMixin {
    @Inject(method="openForPlayer",at=@At("HEAD"),cancellable=true,remap=false)
    private static void changedSynergy$openCombined(ServerPlayer player,ItemStack request,CallbackInfo ci){
        if(CuriosCompat.isLoaded() && CuriosCompat.openClothing(player,request))ci.cancel();
    }
}
