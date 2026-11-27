package net.parkabird.changedsynergy.block;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.parkabird.changedsynergy.init.ChangedSynergyBlockEntities;

/** Stores the exact placed {@code DarkLatexMask} item stack (variant, owner, revival NBT) for {@link DarkLatexMaskBlock}. */
public final class DarkLatexMaskBlockEntity extends BlockEntity {
    private static final String TAG_MASK = "Mask";

    private ItemStack maskStack = defaultMask();

    public DarkLatexMaskBlockEntity(BlockPos pos, BlockState state) {
        super(ChangedSynergyBlockEntities.DARK_LATEX_MASK.get(), pos, state);
    }

    public ItemStack getMaskStack() {
        return maskStack.isEmpty() ? defaultMask() : maskStack.copy();
    }

    public void setMaskStack(ItemStack stack) {
        maskStack = stack.isEmpty() ? defaultMask() : stack.copyWithCount(1);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_MASK, (maskStack.isEmpty() ? defaultMask() : maskStack).save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (tag.contains(TAG_MASK, Tag.TAG_COMPOUND)) {
            ItemStack loaded = ItemStack.of(tag.getCompound(TAG_MASK));
            maskStack = loaded.isEmpty() ? defaultMask() : loaded.copyWithCount(1);
        } else {
            maskStack = defaultMask();
        }
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private static ItemStack defaultMask() {
        return new ItemStack(ChangedItems.DARK_LATEX_MASK.get());
    }
}
