package net.parkabird.changedsynergy.mixin;

import java.util.Map;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import net.parkabird.changedsynergy.compat.curios.NativeClothingInventory;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AccessorySlots.class)
public abstract class NativeClothingStorageMixin {
    @Shadow(remap=false) @Final public LivingEntity owner;
    @Shadow(remap=false) @Final @Mutable private Map<AccessorySlotType,ItemStack> items;
    @Inject(method="<init>",at=@At("RETURN")) private void changedSynergy$curiosStorage(CallbackInfo ci) {
        if(owner!=null&&CuriosCompat.isLoaded())items=new NativeClothingInventory(owner,items);
    }
    @Inject(method="initialize",at=@At("RETURN"),remap=false) private void changedSynergy$updateShape(CallbackInfoReturnable<Boolean> ci) {
        if(items instanceof NativeClothingInventory inventory)inventory.reconcile();
    }
    @Inject(method="save",at=@At("RETURN"),remap=false) private void changedSynergy$save(CallbackInfoReturnable<CompoundTag> ci) {
        if(items instanceof NativeClothingInventory inventory)inventory.markSave(ci.getReturnValue());
    }
    @Inject(method="save",at=@At("HEAD"),remap=false) private void changedSynergy$flush(CallbackInfoReturnable<CompoundTag> ci) {
        if(items instanceof NativeClothingInventory inventory)inventory.reconcile();
    }
    @Inject(method="load",at=@At("HEAD"),cancellable=true,remap=false) private void changedSynergy$load(CompoundTag tag,CallbackInfo ci) {
        if(items instanceof NativeClothingInventory inventory){inventory.load(tag);ci.cancel();}
        else {tag.remove("ChangedSynergyCuriosBacked");tag.remove("ChangedSynergyPendingClothing");}
    }
    @Inject(method="tick",at=@At("HEAD"),remap=false) private void changedSynergy$migrate(CallbackInfo ci) {
        if(items instanceof NativeClothingInventory inventory)inventory.reconcile();
    }
}
