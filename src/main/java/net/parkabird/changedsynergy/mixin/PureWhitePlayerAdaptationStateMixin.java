package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = TransfurVariantInstance.class, remap = false)
public abstract class PureWhitePlayerAdaptationStateMixin
        implements PureWhiteWolfAdaptation.PlayerState {
    @Unique
    private CompoundTag changedSynergy$pureWhiteAdaptation = new CompoundTag();

    @Override
    public CompoundTag changedSynergy$getPureWhiteAdaptation() {
        return changedSynergy$pureWhiteAdaptation.copy();
    }

    @Override
    public void changedSynergy$setPureWhiteAdaptation(CompoundTag data) {
        changedSynergy$pureWhiteAdaptation = data.copy();
    }

    @Inject(method = "save", at = @At("RETURN"))
    private void changedSynergy$saveAdaptation(
            CallbackInfoReturnable<CompoundTag> callback) {
        if (!changedSynergy$pureWhiteAdaptation.isEmpty()) {
            callback.getReturnValue().put(
                    PureWhiteWolfAdaptation.PLAYER_DATA_KEY,
                    changedSynergy$pureWhiteAdaptation.copy());
        }
    }

    @Inject(method = "load", at = @At("TAIL"))
    private void changedSynergy$loadAdaptation(
            CompoundTag tag, CallbackInfo callback) {
        changedSynergy$pureWhiteAdaptation = tag.contains(
                        PureWhiteWolfAdaptation.PLAYER_DATA_KEY, Tag.TAG_COMPOUND)
                ? tag.getCompound(PureWhiteWolfAdaptation.PLAYER_DATA_KEY).copy()
                : new CompoundTag();
    }
}
