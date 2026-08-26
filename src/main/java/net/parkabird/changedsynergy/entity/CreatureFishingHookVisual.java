package net.parkabird.changedsynergy.entity;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * A non-interactive fishing float used by creature routines.
 *
 * <p>Vanilla's {@code FishingHook} hard-casts its owner to a player and
 * discards itself otherwise. This visual-only entity lets Changed creatures
 * use the same float and line presentation without altering fishing loot or
 * collision.</p>
 */
public final class CreatureFishingHookVisual extends Entity {
    private static final EntityDataAccessor<Integer> FISHER_ID =
            SynchedEntityData.defineId(
                    CreatureFishingHookVisual.class,
                    EntityDataSerializers.INT);
    private static final int MAX_LIFETIME_TICKS = 20 * 30;

    public CreatureFishingHookVisual(
            EntityType<? extends CreatureFishingHookVisual> type,
            Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(FISHER_ID, 0);
    }

    public void setFisher(ChangedEntity fisher) {
        entityData.set(FISHER_ID, fisher.getId());
    }

    @Nullable
    public ChangedEntity getFisher() {
        Entity owner = level().getEntity(entityData.get(FISHER_ID));
        return owner instanceof ChangedEntity changed ? changed : null;
    }

    @Override
    public void tick() {
        super.tick();
        noPhysics = true;
        setDeltaMovement(0.0D, 0.0D, 0.0D);
        ChangedEntity fisher = getFisher();
        if (!level().isClientSide
                && (tickCount > MAX_LIFETIME_TICKS
                        || fisher == null
                        || !fisher.isAlive())) {
            discard();
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }
}
