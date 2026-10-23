package net.parkabird.changedsynergy.ai;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;

/** Presents a modded IItemHandler (Sophisticated Storage, backpacks, etc.) as a vanilla Container. */
public final class HandlerContainer implements Container {
    private final IItemHandler handler;

    public HandlerContainer(IItemHandler handler) { this.handler = handler; }

    /** The real inventory behind this wrapper, for callers that need handler semantics. */
    public IItemHandler handler() { return handler; }

    @Override public int getContainerSize() { return handler.getSlots(); }

    @Override public boolean isEmpty() {
        for (int i = 0; i < handler.getSlots(); i++)
            if (!handler.getStackInSlot(i).isEmpty()) return false;
        return true;
    }

    @Override public ItemStack getItem(int slot) { return handler.getStackInSlot(slot).copy(); }

    @Override public ItemStack removeItem(int slot, int amount) { return handler.extractItem(slot, amount, false); }

    @Override public ItemStack removeItemNoUpdate(int slot) {
        ItemStack s = handler.getStackInSlot(slot);
        return s.isEmpty() ? ItemStack.EMPTY : handler.extractItem(slot, s.getCount(), false);
    }

    @Override public void setItem(int slot, ItemStack stack) {
        ItemStack existing = handler.getStackInSlot(slot);
        if (!existing.isEmpty()) handler.extractItem(slot, existing.getCount(), false);
        if (!stack.isEmpty()) handler.insertItem(slot, stack, false);
    }

    @Override public boolean canPlaceItem(int slot, ItemStack stack) { return handler.isItemValid(slot, stack); }
    @Override public void setChanged() {}
    @Override public boolean stillValid(Player player) { return true; }

    @Override public void clearContent() {
        for (int i = 0; i < handler.getSlots(); i++) removeItemNoUpdate(i);
    }
}
