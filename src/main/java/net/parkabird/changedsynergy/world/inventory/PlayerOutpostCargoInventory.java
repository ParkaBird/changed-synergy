package net.parkabird.changedsynergy.world.inventory;

import java.util.ArrayList;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;

/** The camp's real saved cargo, shown beside personal storage without duplicating either inventory. */
public final class PlayerOutpostCargoInventory implements Container {
    private static final String BAG = "ChangedSynergyPlayerOutpostWorkBag";
    private static final String GATHER = "ChangedSynergyPlayerOutpostCargo";
    public static final int WORK_SLOTS = 6;
    public static final int GATHER_SLOT = WORK_SLOTS;
    public static final int SIZE = WORK_SLOTS + 1;
    private final ChangedEntity mob;
    private final NonNullList<ItemStack> items = NonNullList.withSize(SIZE, ItemStack.EMPTY);

    public PlayerOutpostCargoInventory(ChangedEntity mob) {
        this.mob = mob;
        ListTag saved = mob.getPersistentData().getList(BAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < Math.min(WORK_SLOTS, saved.size()); i++) items.set(i, ItemStack.of(saved.getCompound(i)));
        items.set(GATHER_SLOT, ItemStack.of(mob.getPersistentData().getCompound(GATHER)));
    }

    /** AI and the open menu must mutate the same live objects, never separate saved snapshots. */
    public static PlayerOutpostCargoInventory inventory(ChangedEntity mob) {
        if (mob.level() instanceof ServerLevel level) {
            for (var viewer : level.players()) {
                if (viewer.containerMenu instanceof BondedCreatureInventoryMenu menu
                        && menu.getPet() == mob && menu.getWorkInventory() instanceof PlayerOutpostCargoInventory cargo) return cargo;
            }
        }
        return new PlayerOutpostCargoInventory(mob);
    }

    public static boolean visible(ChangedEntity mob) {
        return PlayerOutpostService.assigned(mob)
                || hasWorkItems(mob) || hasGatherItem(mob);
    }

    /** Menu mutations write through immediately; availability checks need no viewer search or container copy. */
    public static boolean hasWorkItems(ChangedEntity mob) {
        ListTag saved = mob.getPersistentData().getList(BAG, Tag.TAG_COMPOUND);
        for (int slot = 0; slot < Math.min(WORK_SLOTS, saved.size()); slot++)
            if (!ItemStack.of(saved.getCompound(slot)).isEmpty()) return true;
        return false;
    }

    public static boolean hasGatherItem(ChangedEntity mob) {
        return !ItemStack.of(mob.getPersistentData().getCompound(GATHER)).isEmpty();
    }

    public List<ItemStack> workItems() {
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < WORK_SLOTS; i++) if (!items.get(i).isEmpty()) result.add(items.get(i).copy());
        return result;
    }

    /** Empty slots and partially filled stacks are both usable work space. */
    public boolean hasWorkSpace() {
        for (int i = 0; i < WORK_SLOTS; i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty() || item.getCount() < item.getMaxStackSize()) return true;
        }
        return false;
    }

    public int workSpaceFor(ItemStack incoming) {
        int space = 0;
        for (int i = 0; i < WORK_SLOTS; i++) {
            ItemStack item = items.get(i);
            if (item.isEmpty()) space += incoming.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(item, incoming))
                space += Math.max(0, item.getMaxStackSize() - item.getCount());
        }
        return space;
    }

    public void setWorkItems(List<ItemStack> cargo) {
        for (int i = 0; i < WORK_SLOTS; i++) items.set(i, i < cargo.size() ? cargo.get(i).copy() : ItemStack.EMPTY);
        setChanged();
    }

    @Override public int getContainerSize() { return SIZE; }
    @Override public boolean isEmpty() { return items.stream().allMatch(ItemStack::isEmpty); }
    @Override public ItemStack getItem(int slot) { return slot >= 0 && slot < SIZE ? items.get(slot) : ItemStack.EMPTY; }
    @Override public ItemStack removeItem(int slot, int count) {
        if (slot < 0 || slot >= SIZE || count <= 0) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.removeItem(items, slot, count);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SIZE) return ItemStack.EMPTY;
        ItemStack removed = ContainerHelper.takeItem(items, slot);
        if (!removed.isEmpty()) setChanged();
        return removed;
    }
    @Override public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) return;
        items.set(slot, stack.copy());
        setChanged();
    }
    @Override public void setChanged() {
        // Empty slots remain in the saved list so Shift-clicking one slot cannot move its neighbours.
        ListTag saved = new ListTag();
        boolean hasWork = false;
        for (int i = 0; i < WORK_SLOTS; i++) {
            saved.add(items.get(i).save(new CompoundTag()));
            hasWork |= !items.get(i).isEmpty();
        }
        var data = mob.getPersistentData();
        if (hasWork) data.put(BAG, saved);
        else {
            data.remove(BAG);
            data.remove("ChangedSynergyPlayerOutpostFarmBatch");
            data.remove("ChangedSynergyPlayerOutpostHaulBatch");
        }
        if (items.get(GATHER_SLOT).isEmpty()) data.remove(GATHER);
        else data.put(GATHER, items.get(GATHER_SLOT).save(new CompoundTag()));
    }
    @Override public boolean stillValid(Player viewer) {
        return mob.isAlive() && !mob.isRemoved() && viewer.distanceToSqr(mob) <= 64.0D;
    }
    @Override public boolean canPlaceItem(int slot, ItemStack item) { return false; }
    @Override public void clearContent() { items.clear(); setChanged(); }
}
