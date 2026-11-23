package net.parkabird.changedsynergy.mixin;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.parkabird.changedsynergy.compat.TrueTransfurCompat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Syncs TrueTransfur Instinct owner UUIDs so player-skinned forms keep their host skin on clients. */
@Mixin(value = ChangedEntity.class, remap = false)
public abstract class InstinctSkinOwnerMixin implements TrueTransfurCompat.InstinctOwnerState {
    @Unique
    private static final EntityDataAccessor<Optional<UUID>> changedSynergy$instinctOwner =
            SynchedEntityData.defineId(ChangedEntity.class, EntityDataSerializers.OPTIONAL_UUID);

    @Inject(method = {"defineSynchedData()V", "m_8097_()V"}, at = @At("TAIL"))
    private void changedSynergy$defineInstinctOwner(CallbackInfo callback) {
        ((ChangedEntity) (Object) this).getEntityData().define(changedSynergy$instinctOwner, Optional.empty());
    }

    @Inject(method = {"tick()V", "m_8119_()V"}, at = @At("TAIL"))
    private void changedSynergy$syncInstinctOwner(CallbackInfo callback) {
        ChangedEntity self = (ChangedEntity) (Object) this;
        if (self.level().isClientSide()) return;
        Optional<UUID> target = self.getUnderlyingPlayer() == null && TrueTransfurCompat.isInstinctBody(self)
                ? Optional.of(self.getPersistentData().getUUID("TrueTransfurOwner"))
                : Optional.empty();
        if (!Objects.equals(self.getEntityData().get(changedSynergy$instinctOwner), target)) {
            self.getEntityData().set(changedSynergy$instinctOwner, target);
        }
    }

    @Override
    public @Nullable UUID changedSynergy$getInstinctOwner() {
        return ((ChangedEntity) (Object) this).getEntityData().get(changedSynergy$instinctOwner).orElse(null);
    }

    @Override
    public void changedSynergy$setInstinctOwner(@Nullable UUID owner) {
        ((ChangedEntity) (Object) this).getEntityData().set(
                changedSynergy$instinctOwner, Optional.ofNullable(owner));
    }
}
