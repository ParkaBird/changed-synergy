package net.parkabird.changedsynergy.mixin;

import java.util.Map;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(AccessorySlots.class)
public abstract class NativeClothingStorageMixin {
    @Shadow(remap=false) @Final public LivingEntity owner;
    @Shadow(remap=false) @Final @Mutable private Map<AccessorySlotType,ItemStack> items;
    @Inject(method="<init>",at=@At("RETURN")) private void changedSynergy$curiosStorage(CallbackInfo ci) {
        items=CuriosCompat.wrapInventory(owner,items);
    }
    @Inject(method="initialize",at=@At("RETURN"),remap=false) private void changedSynergy$updateShape(CallbackInfoReturnable<Boolean> ci) {
        CuriosCompat.reconcileInventory(items);
    }
    @Inject(method="save",at=@At("RETURN"),remap=false) private void changedSynergy$save(CallbackInfoReturnable<CompoundTag> ci) {
        CuriosCompat.markSaveInventory(items,ci.getReturnValue());
    }
    @Inject(method="save",at=@At("HEAD"),remap=false) private void changedSynergy$flush(CallbackInfoReturnable<CompoundTag> ci) {
        CuriosCompat.reconcileInventory(items);
    }
    @Inject(method="load",at=@At("HEAD"),cancellable=true,remap=false) private void changedSynergy$load(CompoundTag tag,CallbackInfo ci) {
        if(CuriosCompat.loadInventory(items,tag))ci.cancel();
        else {tag.remove("ChangedSynergyCuriosBacked");tag.remove("ChangedSynergyPendingClothing");}
    }
    @Inject(method="tick",at=@At("HEAD"),remap=false) private void changedSynergy$migrate(CallbackInfo ci) {
        CuriosCompat.reconcileInventory(items);
    }
}
