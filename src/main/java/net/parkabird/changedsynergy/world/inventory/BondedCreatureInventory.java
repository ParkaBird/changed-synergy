package net.parkabird.changedsynergy.world.inventory;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Persistent inventory used by bonded Changed entities that do not implement
 * either Changed's or Changed Addon's native pet inventory API.
 */
public final class BondedCreatureInventory implements Container {
    private static final String DATA_KEY = "ChangedSynergyBondedInventory";
    private static final int STORAGE_SIZE = 24;
    private static final int ARMOR_START = 24;
    private static final int OFFHAND_SLOT = 28;
    private static final int INVENTORY_SIZE = 29;
    private static final EquipmentSlot[] ARMOR_BY_CONTAINER_INDEX = {
            EquipmentSlot.FEET,
            EquipmentSlot.LEGS,
            EquipmentSlot.CHEST,
            EquipmentSlot.HEAD
    };

    private final ChangedEntity pet;
    private final NonNullList<ItemStack> storage =
            NonNullList.withSize(STORAGE_SIZE, ItemStack.EMPTY);

    public BondedCreatureInventory(ChangedEntity pet) {
        this.pet = pet;
        CompoundTag saved = pet.getPersistentData().getCompound(DATA_KEY);
        ContainerHelper.loadAllItems(saved, storage);
    }

    @Override
    public int getContainerSize() {
        return INVENTORY_SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : storage) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        for (EquipmentSlot slot : ARMOR_BY_CONTAINER_INDEX) {
            if (!pet.getItemBySlot(slot).isEmpty()) {
                return false;
            }
        }
        return pet.getItemBySlot(EquipmentSlot.OFFHAND).isEmpty();
    }

    @Override
    public ItemStack getItem(int index) {
        if (index >= 0 && index < STORAGE_SIZE) {
            return storage.get(index);
        }
        EquipmentSlot equipmentSlot = equipmentSlot(index);
        return equipmentSlot == null ? ItemStack.EMPTY : pet.getItemBySlot(equipmentSlot);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        if (index >= 0 && index < STORAGE_SIZE) {
            ItemStack removed = ContainerHelper.removeItem(storage, index, count);
            if (!removed.isEmpty()) {
                setChanged();
            }
            return removed;
        }

        EquipmentSlot slot = equipmentSlot(index);
        if (slot == null || count <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack equipped = pet.getItemBySlot(slot);
        if (equipped.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = equipped.split(count);
        pet.setItemSlot(slot, equipped.isEmpty() ? ItemStack.EMPTY : equipped);
        return removed;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        if (index >= 0 && index < STORAGE_SIZE) {
            ItemStack removed = ContainerHelper.takeItem(storage, index);
            if (!removed.isEmpty()) {
                saveStorage();
            }
            return removed;
        }

        EquipmentSlot slot = equipmentSlot(index);
        if (slot == null) {
            return ItemStack.EMPTY;
        }
        ItemStack removed = pet.getItemBySlot(slot);
        pet.setItemSlot(slot, ItemStack.EMPTY);
        return removed;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        if (index >= 0 && index < STORAGE_SIZE) {
            storage.set(index, stack);
            if (stack.getCount() > getMaxStackSize()) {
                stack.setCount(getMaxStackSize());
            }
            setChanged();
            return;
        }

        EquipmentSlot slot = equipmentSlot(index);
        if (slot != null) {
            pet.setItemSlot(slot, stack);
        }
    }

    @Override
    public void setChanged() {
        saveStorage();
    }

    @Override
    public boolean stillValid(Player player) {
        return pet.isAlive() && !pet.isRemoved() && player.distanceToSqr(pet) <= 64.0D;
    }

    @Override
    public void clearContent() {
        storage.clear();
        for (EquipmentSlot slot : ARMOR_BY_CONTAINER_INDEX) {
            pet.setItemSlot(slot, ItemStack.EMPTY);
        }
        pet.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
        setChanged();
    }

    /** Returns the first real fishing rod stored by this companion. */
    public int findFishingRod() {
        for (int index = 0; index < STORAGE_SIZE; index++) {
            ItemStack stack = storage.get(index);
            if (!stack.isEmpty() && stack.getItem() instanceof FishingRodItem) {
                return index;
            }
        }
        return -1;
    }

    /** Selects the fastest stored tool that can legitimately harvest the block. */
    public int findMiningTool(BlockState state) {
        int best = -1;
        float bestSpeed = 1.0F;
        for (int index = 0; index < STORAGE_SIZE; index++) {
            ItemStack stack = storage.get(index);
            if (stack.isEmpty() || !stack.isCorrectToolForDrops(state)) {
                continue;
            }
            float speed = stack.getDestroySpeed(state);
            if (best < 0 || speed > bestSpeed) {
                best = index;
                bestSpeed = speed;
            }
        }
        return best;
    }

    public ItemStack storageItem(int index) {
        return index >= 0 && index < STORAGE_SIZE
                ? storage.get(index) : ItemStack.EMPTY;
    }

    public int findItem(Item item) {
        for (int index = 0; index < STORAGE_SIZE; index++) {
            if (storage.get(index).is(item)) {
                return index;
            }
        }
        return -1;
    }

    public boolean consumeOne(int index) {
        ItemStack stack = storageItem(index);
        if (stack.isEmpty()) {
            return false;
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            storage.set(index, ItemStack.EMPTY);
        }
        setChanged();
        return true;
    }

    /** Inserts as much as possible and returns the remainder. */
    public ItemStack insert(ItemStack incoming) {
        if (incoming.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack remainder = incoming.copy();
        for (int index = 0; index < STORAGE_SIZE && !remainder.isEmpty(); index++) {
            ItemStack present = storage.get(index);
            if (present.isEmpty() || !ItemStack.isSameItemSameTags(present, remainder)) {
                continue;
            }
            int moved = Math.min(
                    remainder.getCount(),
                    Math.min(getMaxStackSize(), present.getMaxStackSize()) - present.getCount());
            if (moved > 0) {
                present.grow(moved);
                remainder.shrink(moved);
            }
        }
        for (int index = 0; index < STORAGE_SIZE && !remainder.isEmpty(); index++) {
            if (!storage.get(index).isEmpty()) {
                continue;
            }
            int moved = Math.min(
                    remainder.getCount(),
                    Math.min(getMaxStackSize(), remainder.getMaxStackSize()));
            ItemStack inserted = remainder.copy();
            inserted.setCount(moved);
            storage.set(index, inserted);
            remainder.shrink(moved);
        }
        setChanged();
        return remainder;
    }

    public void damageStoredTool(int index, int amount) {
        ItemStack tool = storageItem(index);
        if (tool.isEmpty() || amount <= 0) {
            return;
        }
        tool.hurtAndBreak(
                amount,
                pet,
                broken -> broken.broadcastBreakEvent(net.minecraft.world.InteractionHand.MAIN_HAND));
        if (tool.isEmpty()) {
            storage.set(index, ItemStack.EMPTY);
        }
        setChanged();
    }

    private void saveStorage() {
        CompoundTag saved = new CompoundTag();
        ContainerHelper.saveAllItems(saved, storage);
        pet.getPersistentData().put(DATA_KEY, saved);
    }

    private static EquipmentSlot equipmentSlot(int index) {
        if (index >= ARMOR_START && index < OFFHAND_SLOT) {
            return ARMOR_BY_CONTAINER_INDEX[index - ARMOR_START];
        }
        return index == OFFHAND_SLOT ? EquipmentSlot.OFFHAND : null;
    }
}
