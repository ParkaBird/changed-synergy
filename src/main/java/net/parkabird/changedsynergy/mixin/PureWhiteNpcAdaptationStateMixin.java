package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChangedEntity.class, remap = false)
public abstract class PureWhiteNpcAdaptationStateMixin implements PureWhiteWolfAdaptation.NpcState {
    @Unique private static final EntityDataAccessor<Boolean> changedSynergy$adaptedEyes =
            SynchedEntityData.defineId(ChangedEntity.class, EntityDataSerializers.BOOLEAN);

    @Inject(method = {"defineSynchedData()V", "m_8097_()V"}, at = @At("TAIL"))
    private void changedSynergy$defineEyes(CallbackInfo callback) {
        ((ChangedEntity)(Object)this).getEntityData().define(changedSynergy$adaptedEyes, false);
    }

    @Override public boolean changedSynergy$hasAdaptedEyes() {
        return ((ChangedEntity)(Object)this).getEntityData().get(changedSynergy$adaptedEyes);
    }

    @Override public void changedSynergy$setAdaptedEyes(boolean adapted) {
        ((ChangedEntity)(Object)this).getEntityData().set(changedSynergy$adaptedEyes, adapted);
    }
}
