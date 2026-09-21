package net.parkabird.changedsynergy.mixin;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.parkabird.changedsynergy.ai.InheritedEyeAppearance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Carries Synergy's eye override through Changed's existing form save/sync payload. */
@Mixin(value = TransfurVariantInstance.class, remap = false)
public abstract class TransfurVariantInstanceInheritedEyesMixin
        implements InheritedEyeAppearance.Holder {
    @Unique
    @Nullable
    private CompoundTag changedSynergy$inheritedEyes;

    @Override
    @Nullable
    public CompoundTag changedSynergy$getInheritedEyes() {
        return changedSynergy$inheritedEyes;
    }

    @Override
    public void changedSynergy$setInheritedEyes(@Nullable CompoundTag appearance) {
        changedSynergy$inheritedEyes = appearance == null ? null : appearance.copy();
    }

    @Inject(method = "save", at = @At("RETURN"), require = 1)
    private void changedSynergy$saveInheritedEyes(
            CallbackInfoReturnable<CompoundTag> callback) {
        if (InheritedEyeAppearance.isValid(changedSynergy$inheritedEyes)) {
            callback.getReturnValue().put(
                    InheritedEyeAppearance.NBT_KEY,
                    changedSynergy$inheritedEyes.copy());
        }
    }

    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void changedSynergy$loadInheritedEyes(
            CompoundTag tag,
            CallbackInfo callback) {
        changedSynergy$inheritedEyes = tag.contains(
                        InheritedEyeAppearance.NBT_KEY, Tag.TAG_COMPOUND)
                && InheritedEyeAppearance.isValid(tag.getCompound(
                        InheritedEyeAppearance.NBT_KEY))
                ? tag.getCompound(InheritedEyeAppearance.NBT_KEY).copy()
                : null;
    }
}
