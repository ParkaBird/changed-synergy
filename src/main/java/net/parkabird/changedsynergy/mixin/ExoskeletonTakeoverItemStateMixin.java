package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.robot.Exoskeleton;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ai.ExoskeletonTakeoverAdapter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps forced equipment's mechanical damage and fractional charge on later normal placement. */
@Mixin(value = Exoskeleton.class, remap = false)
public abstract class ExoskeletonTakeoverItemStateMixin {
    @Inject(method = "loadFromItemStack", at = @At("TAIL"), require = 1)
    private void changedSynergy$restoreMechanicalState(ItemStack stack, CallbackInfo ci) {
        ExoskeletonTakeoverAdapter.restoreMechanicalState((Exoskeleton)(Object)this, stack);
    }
}
