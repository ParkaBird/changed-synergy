package net.parkabird.changedsynergy.world.inventory;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** Persistent two-row chest pack carried by a social taur. */
public final class CentaurMountCargo implements Container {
    public static final int SIZE = 18;
    private static final String DATA_KEY = "ChangedSynergyCentaurCargo";

    private final ChangedEntity centaur;
    private final NonNullList<ItemStack> items =
            NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public CentaurMountCargo(ChangedEntity centaur) {
        this.centaur = centaur;
        CompoundTag saved = centaur.getPersistentData().getCompound(DATA_KEY);
        ContainerHelper.loadAllItems(saved, items);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty);
    }

    @Override
    public ItemStack getItem(int index) {
        return index >= 0 && index < SIZE ? items.get(index) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack removed = ContainerHelper.removeItem(items, index, count);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack removed = ContainerHelper.takeItem(items, index);
        if (!removed.isEmpty()) {
            setChanged();
        }
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index < 0 || index >= SIZE) {
            return;
        }
        items.set(index, stack);
        if (stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public void setChanged() {
        CompoundTag saved = new CompoundTag();
        ContainerHelper.saveAllItems(saved, items);
        centaur.getPersistentData().put(DATA_KEY, saved);
    }

    @Override
    public boolean stillValid(Player player) {
        return CentaurMountService.canAccessCargo(player, centaur);
    }

    @Override
    public void clearContent() {
        items.clear();
        setChanged();
    }

    public void dropContents() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                centaur.spawnAtLocation(stack.copy());
            }
        }
        clearContent();
    }
}
