package net.parkabird.changedsynergy.ai;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/** Worker view of automation storage; all transfers go through the capability. */
final class PlayerOutpostItemStorage implements Container {
    private final BlockEntity blockEntity;
    private final IItemHandler inventory;

    PlayerOutpostItemStorage(BlockEntity blockEntity, IItemHandler inventory) {
        this.blockEntity = blockEntity;
        this.inventory = inventory;
    }

    ItemStack insert(ItemStack stack) {
        return ItemHandlerHelper.insertItemStacked(inventory, stack.copy(), false);
    }
    boolean acceptsAll(ItemStack stack){return ItemHandlerHelper.insertItemStacked(inventory,stack.copy(),true).isEmpty();}
    boolean acceptsAny(ItemStack stack){return !stack.isEmpty() && ItemHandlerHelper.insertItemStacked(inventory,stack.copy(),true).getCount() < stack.getCount();}

    @Override public int getContainerSize() { return inventory.getSlots(); }
    @Override public boolean isEmpty() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }
    @Override public ItemStack getItem(int slot) {
        // Work planners must not reserve food or seeds that automation cannot extract.
        return inventory.extractItem(slot, 1, true).isEmpty() ? ItemStack.EMPTY : inventory.getStackInSlot(slot).copy();
    }
    @Override public ItemStack removeItem(int slot, int count) { return inventory.extractItem(slot, count, false); }
    @Override public ItemStack removeItemNoUpdate(int slot) { return removeItem(slot, Integer.MAX_VALUE); }
    @Override public boolean canPlaceItem(int slot, ItemStack stack) {
        return !stack.isEmpty() && inventory.insertItem(slot, stack.copyWithCount(1), true).isEmpty();
    }
    @Override public void setItem(int slot, ItemStack stack) {
        // Worker insertion uses insert(), never a raw overwrite that bypasses upgrades or filters.
        throw new UnsupportedOperationException("Use capability insertion/extraction for outpost storage");
    }
    @Override public void setChanged() { blockEntity.setChanged(); }
    @Override public boolean stillValid(Player player) {
        return !blockEntity.isRemoved() && player.level() == blockEntity.getLevel()
                && player.distanceToSqr(blockEntity.getBlockPos().getX() + .5,
                        blockEntity.getBlockPos().getY() + .5, blockEntity.getBlockPos().getZ() + .5) <= 64;
    }
    @Override public void clearContent() {
        for (int slot = 0; slot < inventory.getSlots(); slot++) removeItemNoUpdate(slot);
    }
}
