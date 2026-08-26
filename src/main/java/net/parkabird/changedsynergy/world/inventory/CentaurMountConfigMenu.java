package net.parkabird.changedsynergy.world.inventory;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAccessorySlots;
import net.parkabird.changedsynergy.dialogue.NpcDialogue;
import net.parkabird.changedsynergy.dialogue.NpcDialogue.Cue;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Changed-style taur tack panel; cargo remains a separate two-row pack. */
public final class CentaurMountConfigMenu extends AbstractContainerMenu {
    public static final int CARGO_BUTTON_ID = 0;
    public static final int RIDE_BUTTON_ID = 1;
    private static final int PLAYER_MENU_START = 2;
    private static final int PLAYER_MAIN_END = PLAYER_MENU_START + 27;
    private static final int PLAYER_MENU_END = PLAYER_MAIN_END + 9;

    private final Player viewer;
    @Nullable
    private final ChangedEntity centaur;
    private final Container gear;
    private final boolean cargoEmpty;

    public CentaurMountConfigMenu(
            int id,
            Inventory inventory,
            ChangedEntity centaur) {
        this(
                id,
                inventory,
                centaur,
                new TaurGearContainer(centaur, inventory.player),
                new CentaurMountCargo(centaur).isEmpty());
    }

    public CentaurMountConfigMenu(
            int id,
            Inventory inventory,
            FriendlyByteBuf extraData) {
        this(
                id,
                inventory,
                resolveCentaur(inventory, extraData.readVarInt()),
                new SimpleContainer(2),
                extraData.readBoolean());
    }

    private CentaurMountConfigMenu(
            int id,
            Inventory inventory,
            @Nullable ChangedEntity centaur,
            Container gear,
            boolean cargoEmpty) {
        super(ChangedSynergyMenus.CENTAUR_MOUNT_CONFIG.get(), id);
        checkContainerSize(gear, 2);
        this.viewer = inventory.player;
        this.centaur = centaur;
        this.gear = gear;
        this.cargoEmpty = cargoEmpty;
        createSlots(inventory);
    }

    public static void writeOpenData(
            FriendlyByteBuf buffer,
            ChangedEntity centaur) {
        buffer.writeVarInt(centaur.getId());
        buffer.writeBoolean(new CentaurMountCargo(centaur).isEmpty());
    }

    @Nullable
    public ChangedEntity getCentaur() {
        return centaur;
    }

    public boolean hasSaddle() {
        return gear.getItem(0).is(Items.SADDLE);
    }

    public boolean hasChest() {
        return gear.getItem(1).is(Items.CHEST);
    }

    public boolean isViewerRiding() {
        return centaur != null && centaur.getFirstPassenger() == viewer;
    }

    @Override
    public boolean clickMenuButton(Player player, int buttonId) {
        if (buttonId != CARGO_BUTTON_ID && buttonId != RIDE_BUTTON_ID) {
            return false;
        }
        if (player.level().isClientSide) {
            return buttonId == CARGO_BUTTON_ID
                    ? hasChest()
                    : hasSaddle() && !isViewerRiding();
        }
        if (!(player instanceof ServerPlayer serverPlayer) || centaur == null) {
            return false;
        }
        return buttonId == CARGO_BUTTON_ID
                ? CentaurMountService.openCargo(serverPlayer, centaur)
                : CentaurMountService.mount(serverPlayer, centaur);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().isClientSide
                || centaur != null
                        && player instanceof ServerPlayer serverPlayer
                        && CentaurMountService.canConfigure(serverPlayer, centaur);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int slotIndex) {
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(slotIndex);
        if (!source.hasItem() || slotIndex == 1 && !cargoEmpty) {
            return ItemStack.EMPTY;
        }

        ItemStack moving = source.getItem();
        ItemStack original = moving.copy();
        if (slotIndex < PLAYER_MENU_START) {
            if (!moveItemStackTo(
                    moving, PLAYER_MENU_START, PLAYER_MENU_END, true)) {
                return ItemStack.EMPTY;
            }
        } else if (moving.is(Items.SADDLE) && !slots.get(0).hasItem()) {
            if (!moveItemStackTo(moving, 0, 1, false)) {
                return ItemStack.EMPTY;
            }
        } else if (moving.is(Items.CHEST) && !slots.get(1).hasItem()) {
            if (!moveItemStackTo(moving, 1, 2, false)) {
                return ItemStack.EMPTY;
            }
        } else if (slotIndex < PLAYER_MAIN_END) {
            if (!moveItemStackTo(
                    moving, PLAYER_MAIN_END, PLAYER_MENU_END, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(
                moving, PLAYER_MENU_START, PLAYER_MAIN_END, false)) {
            return ItemStack.EMPTY;
        }

        if (moving.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        if (moving.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        source.onTake(player, moving);
        return original;
    }

    private void createSlots(Inventory inventory) {
        addSlot(new GearSlot(gear, 0, 74, 13, Items.SADDLE));
        addSlot(new GearSlot(gear, 1, 92, 13, Items.CHEST) {
            @Override
            public boolean mayPickup(Player player) {
                return cargoEmpty && super.mayPickup(player);
            }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        inventory,
                        column + (row + 1) * 9,
                        10 + column * 18,
                        40 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 10 + column * 18, 98));
        }
    }

    @Nullable
    private static ChangedEntity resolveCentaur(
            Inventory inventory,
            int entityId) {
        Entity entity = inventory.player.level().getEntity(entityId);
        return entity instanceof ChangedEntity changed
                        && CentaurMountService.isCentaur(changed)
                ? changed
                : null;
    }

    private static class GearSlot extends Slot {
        private final net.minecraft.world.item.Item allowed;

        private GearSlot(
                Container container,
                int containerSlot,
                int x,
                int y,
                net.minecraft.world.item.Item allowed) {
            super(container, containerSlot, x, y);
            this.allowed = allowed;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return stack.is(allowed);
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }
    }

    private static final class TaurGearContainer implements Container {
        private static final AccessorySlotType[] TYPES = {
                ChangedAccessorySlots.LOWER_BODY.get(),
                ChangedAccessorySlots.LOWER_BODY_SIDE.get()
        };
        private final AccessorySlots backing;
        private final ChangedEntity centaur;
        @Nullable
        private final ServerPlayer actor;

        private TaurGearContainer(ChangedEntity centaur, Player actor) {
            this.centaur = centaur;
            this.actor = actor instanceof ServerPlayer serverPlayer
                    ? serverPlayer
                    : null;
            this.backing = AccessorySlots.getForEntity(centaur)
                    .orElseThrow(() -> new IllegalStateException(
                            "Changed taur has no accessory inventory"));
            backing.initialize(
                    type -> type == TYPES[0] || type == TYPES[1],
                    ignored -> { });
        }

        @Override
        public int getContainerSize() {
            return 2;
        }

        @Override
        public boolean isEmpty() {
            return getItem(0).isEmpty() && getItem(1).isEmpty();
        }

        @Override
        public ItemStack getItem(int index) {
            return index >= 0 && index < TYPES.length
                    ? backing.getItem(TYPES[index]).orElse(ItemStack.EMPTY)
                    : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            ItemStack current = getItem(index);
            if (current.isEmpty() || count <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = current.split(count);
            setItem(index, current.isEmpty() ? ItemStack.EMPTY : current);
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = getItem(index);
            if (!current.isEmpty()) {
                setItem(index, ItemStack.EMPTY);
            }
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index < 0 || index >= TYPES.length) {
                return;
            }
            ItemStack previous = getItem(index).copy();
            ItemStack equipped =
                    stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
            backing.setItem(TYPES[index], equipped);
            if (actor != null && previous.isEmpty() && !equipped.isEmpty()) {
                NpcDialogue.trigger(
                        centaur,
                        actor,
                        index == 0
                                ? Cue.CENTAUR_SADDLE_EQUIP
                                : Cue.CENTAUR_PACK_EQUIP);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            setItem(0, ItemStack.EMPTY);
            setItem(1, ItemStack.EMPTY);
        }
    }
}
