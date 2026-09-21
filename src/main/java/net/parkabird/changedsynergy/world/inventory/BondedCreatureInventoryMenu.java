package net.parkabird.changedsynergy.world.inventory;

import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.data.AccessorySlotType;
import net.ltxprogrammer.changed.data.AccessorySlots;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.parkabird.changedsynergy.ai.CreatureArmorService;
import net.parkabird.changedsynergy.ai.LatexSocialMemory;
import net.parkabird.changedsynergy.compat.curios.CuriosCompat;
import net.parkabird.changedsynergy.init.ChangedSynergyMenus;
import net.minecraft.core.NonNullList;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

/**
 * A client-safe view of a bonded creature's real server-side inventory.
 * The client always uses a plain mirror container and receives its contents
 * through vanilla menu slot synchronization.
 */
public final class BondedCreatureInventoryMenu extends AbstractContainerMenu {
    public static final int PET_INVENTORY_SIZE = 29;
    private static final int ARMOR_MENU_START = 0;
    private static final int ARMOR_MENU_END = 4;
    private static final int PLAYER_MENU_START = 4;
    private static final int PLAYER_MENU_END = 40;
    private static final int OFFHAND_MENU_SLOT = 40;
    private static final int STORAGE_MENU_START = 41;
    private static final int STORAGE_MENU_END = 65;
    private static final int CLOTHING_MENU_START = 65;
    public static final int CURIOS_BUTTON_ID = 0;
    public static final int CLOTHING_BUTTON_ID = 1;
    /** Changed apparel uses the same five-column content page as Changed's player UI. */
    public static final int CLOTHING_SLOT_X = 98;
    public static final int CLOTHING_SLOT_Y = 8;
    public static final int CLOTHING_COLUMNS = 5;
    /** Curios remains a distinct external panel, matching the player's Curios UI. */
    public static final int CURIO_SLOT_Y = 8;
    private static final int CURIO_ROWS = 4;
    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD,
            EquipmentSlot.CHEST,
            EquipmentSlot.LEGS,
            EquipmentSlot.FEET
    };
    private static final ResourceLocation[] EMPTY_ARMOR_TEXTURES = {
            InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS,
            InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
            InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
            InventoryMenu.EMPTY_ARMOR_SLOT_HELMET
    };

    private final Player owner;
    @Nullable
    private final ChangedEntity pet;
    private final Container petInventory;
    private final List<AccessorySlotType> accessoryTypes;
    private final AccessoryContainer accessoryContainer;
    private final List<CuriosCompat.SlotDescriptor> curioSlots;
    private final CurioContainer curioContainer;
    private boolean curiosVisible;
    private boolean clothingVisible;

    public BondedCreatureInventoryMenu(
            int id,
            Inventory playerInventory,
            ChangedEntity pet,
            Container petInventory) {
        this(
                id,
                playerInventory,
                pet,
                petInventory,
                accessoryTypes(pet),
                CuriosCompat.slotDescriptors(playerInventory.player, pet));
    }

    private BondedCreatureInventoryMenu(
            int id,
            Inventory playerInventory,
            @Nullable ChangedEntity pet,
            Container petInventory,
            List<AccessorySlotType> accessoryTypes,
            List<CuriosCompat.SlotDescriptor> curioSlots) {
        super(ChangedSynergyMenus.BONDED_CREATURE_INVENTORY.get(), id);
        checkContainerSize(petInventory, PET_INVENTORY_SIZE);
        this.owner = playerInventory.player;
        this.pet = pet;
        this.petInventory = petInventory;
        this.accessoryTypes = List.copyOf(accessoryTypes);
        this.accessoryContainer = new AccessoryContainer(pet, this.accessoryTypes);
        this.curioSlots = List.copyOf(curioSlots);
        this.curioContainer = new CurioContainer(pet, this.curioSlots);
        petInventory.startOpen(owner);
        createSlots(playerInventory);
    }

    public BondedCreatureInventoryMenu(
            int id,
            Inventory playerInventory,
            FriendlyByteBuf extraData) {
        this(id, playerInventory, readOpenData(playerInventory, extraData));
    }

    private BondedCreatureInventoryMenu(
            int id,
            Inventory playerInventory,
            OpenData openData) {
        this(
                id,
                playerInventory,
                openData.pet(),
                new SimpleContainer(PET_INVENTORY_SIZE),
                openData.accessoryTypes(),
                openData.curioSlots());
    }

    @Nullable
    public ChangedEntity getPet() {
        return pet;
    }

    public boolean hasClothing() {
        return !accessoryTypes.isEmpty();
    }

    public int clothingSlotCount() {
        return accessoryTypes.size();
    }

    public boolean clothingVisible() {
        return clothingVisible;
    }

    public boolean hasCurios() {
        return !curioSlots.isEmpty();
    }

    public boolean curiosVisible() {
        return curiosVisible;
    }

    public int curioSlotCount() {
        return curioSlots.size();
    }

    public int curioColumns() {
        return Math.max(1, (curioSlots.size() + CURIO_ROWS - 1) / CURIO_ROWS);
    }

    public int curioSlotX() {
        return -10 - curioColumns() * 18;
    }

    public static void writeOpenData(
            FriendlyByteBuf buffer,
            Player owner,
            ChangedEntity pet) {
        buffer.writeVarInt(pet.getId());
        List<AccessorySlotType> types = accessoryTypes(pet);
        buffer.writeVarInt(types.size());
        types.forEach(type ->
                ChangedRegistry.ACCESSORY_SLOTS.writeRegistryObject(buffer, type));
        List<CuriosCompat.SlotDescriptor> curios =
                CuriosCompat.slotDescriptors(owner, pet);
        buffer.writeVarInt(curios.size());
        for (CuriosCompat.SlotDescriptor descriptor : curios) {
            buffer.writeUtf(descriptor.identifier(), 64);
            buffer.writeVarInt(descriptor.index());
        }
    }

    @Override
    public boolean clickMenuButton(Player viewer, int buttonId) {
        if (!stillValid(viewer)) return false;
        if (buttonId == CURIOS_BUTTON_ID && hasCurios()) {
            curiosVisible = !curiosVisible;
            return true;
        }
        if (buttonId == CLOTHING_BUTTON_ID && hasClothing()) {
            clothingVisible = !clothingVisible;
            return true;
        }
        return false;
    }

    @Override
    public void clicked(int slot, int button, net.minecraft.world.inventory.ClickType type, Player viewer) {
        if (stillValid(viewer)) super.clicked(slot, button, type, viewer);
    }

    @Override
    public boolean stillValid(Player viewer) {
        if (viewer.level().isClientSide) {
            return true;
        }
        return pet != null
                && pet.isAlive()
                && !pet.isRemoved()
                && viewer instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                && BondedInventoryService.canAccess(serverPlayer, pet)
                && viewer.distanceToSqr(pet) <= 64.0D;
    }

    @Override
    public void removed(Player viewer) {
        super.removed(viewer);
        petInventory.stopOpen(viewer);
    }

    @Override
    public ItemStack quickMoveStack(Player viewer, int slotIndex) {
        if (!stillValid(viewer)) return ItemStack.EMPTY;
        if (slotIndex < 0 || slotIndex >= slots.size()) {
            return ItemStack.EMPTY;
        }
        Slot source = slots.get(slotIndex);
        if (!source.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack sourceStack = source.getItem();
        ItemStack original = sourceStack.copy();
        if (slotIndex < ARMOR_MENU_END || slotIndex >= OFFHAND_MENU_SLOT) {
            if (!moveItemStackTo(sourceStack, PLAYER_MENU_START, PLAYER_MENU_END, false)) {
                return ItemStack.EMPTY;
            }
        } else {
            EquipmentSlot equipmentSlot = Mob.getEquipmentSlotForItem(sourceStack);
            int armorMenuSlot = armorMenuSlot(equipmentSlot);
            if (armorMenuSlot >= 0
                    && !slots.get(armorMenuSlot).hasItem()
                    && slots.get(armorMenuSlot).mayPlace(sourceStack)) {
                if (!moveItemStackTo(sourceStack, armorMenuSlot, armorMenuSlot + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (pet != null
                    && !slots.get(OFFHAND_MENU_SLOT).hasItem()
                    && sourceStack.canEquip(EquipmentSlot.OFFHAND, pet)) {
                if (!moveItemStackTo(
                        sourceStack, OFFHAND_MENU_SLOT, OFFHAND_MENU_SLOT + 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveToAccessorySlot(sourceStack)
                    && (clothingVisible
                            || !moveItemStackTo(
                                    sourceStack,
                                    STORAGE_MENU_START,
                                    STORAGE_MENU_END,
                                    false))) {
                return ItemStack.EMPTY;
            }
        }

        if (sourceStack.isEmpty()) {
            source.set(ItemStack.EMPTY);
        } else {
            source.setChanged();
        }
        if (sourceStack.getCount() == original.getCount()) {
            return ItemStack.EMPTY;
        }
        source.onTake(viewer, sourceStack);
        return original;
    }

    private void createSlots(Inventory playerInventory) {
        for (int index = 0; index < ARMOR_SLOTS.length; index++) {
            EquipmentSlot equipmentSlot = ARMOR_SLOTS[index];
            addSlot(new ArmorSlot(
                    petInventory,
                    27 - index,
                    8,
                    8 + index * 18,
                    equipmentSlot));
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(
                        playerInventory,
                        column + (row + 1) * 9,
                        26 + column * 18,
                        84 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(playerInventory, column, 26 + column * 18, 142));
        }

        addSlot(new Slot(petInventory, 28, 77, 62));
        for (int row = 0; row < 4; row++) {
            for (int column = 0; column < 6; column++) {
                addSlot(new StorageSlot(
                        petInventory,
                        column + row * 6,
                        98 + column * 18,
                        8 + row * 18));
            }
        }
        for (int index = 0; index < accessoryTypes.size(); index++) {
            addSlot(new AccessorySlot(
                    accessoryContainer,
                    index,
                    CLOTHING_SLOT_X + index % CLOTHING_COLUMNS * 18,
                    CLOTHING_SLOT_Y + index / CLOTHING_COLUMNS * 18,
                    accessoryTypes.get(index)));
        }
        for (int index = 0; index < curioSlots.size(); index++) {
            int columns = curioColumns();
            addSlot(new CurioSlot(
                    curioContainer,
                    index,
                    curioSlotX() + index % columns * 18,
                    CURIO_SLOT_Y + index / columns * 18,
                    curioSlots.get(index)));
        }
    }

    private boolean moveToAccessorySlot(ItemStack stack) {
        int clothingEnd = CLOTHING_MENU_START + clothingSlotCount();
        if (clothingVisible
                && moveToEmptyEquipmentSlot(
                        stack, CLOTHING_MENU_START, clothingEnd)) {
            return true;
        }
        if (!curiosVisible) {
            return false;
        }
        int curioEnd = clothingEnd + curioSlotCount();
        return moveToEmptyEquipmentSlot(stack, clothingEnd, curioEnd);
    }

    private boolean moveToEmptyEquipmentSlot(ItemStack stack, int start, int end) {
        for (int index = start; index < end; index++) {
            Slot target = slots.get(index);
            if (!target.hasItem()
                    && target.isActive()
                    && target.mayPlace(stack)
                    && moveItemStackTo(stack, index, index + 1, false)) {
                return true;
            }
        }
        return false;
    }

    private boolean canPetWear(ItemStack original, EquipmentSlot slot) {
        return pet != null && CreatureArmorService.canWear(pet, original, slot);
    }

    private static int armorMenuSlot(EquipmentSlot equipmentSlot) {
        return switch (equipmentSlot) {
            case HEAD -> 0;
            case CHEST -> 1;
            case LEGS -> 2;
            case FEET -> 3;
            default -> -1;
        };
    }

    @Nullable
    private static ChangedEntity resolvePet(Inventory inventory, int entityId) {
        Entity entity = inventory.player.level().getEntity(entityId);
        return entity instanceof ChangedEntity changed ? changed : null;
    }

    private static List<AccessorySlotType> accessoryTypes(
            @Nullable ChangedEntity pet) {
        if (pet == null) {
            return List.of();
        }
        return AccessorySlots.getForEntity(pet)
                .map(slots -> List.copyOf(slots.getOrderedSlots()))
                .orElseGet(List::of);
    }

    private static OpenData readOpenData(
            Inventory playerInventory,
            FriendlyByteBuf extraData) {
        ChangedEntity pet = resolvePet(playerInventory, extraData.readVarInt());
        int accessoryCount = Math.min(Math.max(extraData.readVarInt(), 0), 32);
        List<AccessorySlotType> types = new ArrayList<>(accessoryCount);
        for (int index = 0; index < accessoryCount; index++) {
            AccessorySlotType type =
                    ChangedRegistry.ACCESSORY_SLOTS.readRegistryObject(extraData);
            if (type != null) {
                types.add(type);
            }
        }
        int curioCount = Math.min(Math.max(extraData.readVarInt(), 0), 64);
        List<CuriosCompat.SlotDescriptor> curios = new ArrayList<>(curioCount);
        for (int index = 0; index < curioCount; index++) {
            String identifier = extraData.readUtf(64);
            int slotIndex = Math.max(0, extraData.readVarInt());
            if (!identifier.isBlank()) {
                curios.add(new CuriosCompat.SlotDescriptor(identifier, slotIndex));
            }
        }
        return new OpenData(pet, List.copyOf(types), List.copyOf(curios));
    }

    private static final class AccessoryContainer implements Container {
        @Nullable
        private final AccessorySlots backing;
        private final List<AccessorySlotType> types;
        private final NonNullList<ItemStack> mirror;

        private AccessoryContainer(
                @Nullable ChangedEntity pet,
                List<AccessorySlotType> types) {
            this.types = types;
            this.mirror = NonNullList.withSize(types.size(), ItemStack.EMPTY);
            this.backing = pet == null
                    ? null
                    : AccessorySlots.getForEntity(pet).orElse(null);
            if (backing != null) {
                backing.initialize(types::contains, ignored -> { });
            }
        }

        private boolean isItemAllowed(AccessorySlotType type, ItemStack stack) {
            return backing == null || backing.isItemAllowedWithOthers(type, stack);
        }

        @Override
        public int getContainerSize() {
            return types.size();
        }

        @Override
        public boolean isEmpty() {
            for (int index = 0; index < getContainerSize(); index++) {
                if (!getItem(index).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            if (index < 0 || index >= types.size()) {
                return ItemStack.EMPTY;
            }
            if (backing != null) {
                return backing.getItem(types.get(index)).orElse(ItemStack.EMPTY);
            }
            return mirror.get(index);
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
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            setItem(index, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index < 0 || index >= types.size()) {
                return;
            }
            ItemStack limited = stack;
            if (!limited.isEmpty() && limited.getCount() > 1) {
                limited = limited.copyWithCount(1);
            }
            mirror.set(index, limited);
            if (backing != null) {
                backing.setItem(types.get(index), limited);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(Player player) {
            return backing == null || backing.stillValid(player);
        }

        @Override
        public void clearContent() {
            for (int index = 0; index < types.size(); index++) {
                setItem(index, ItemStack.EMPTY);
            }
        }
    }

    private static final class CurioContainer implements Container {
        private final List<CuriosCompat.SlotDescriptor> descriptors;
        private final List<CuriosCompat.SlotAccess> access;
        private final NonNullList<ItemStack> mirror;

        private CurioContainer(
                @Nullable ChangedEntity pet,
                List<CuriosCompat.SlotDescriptor> descriptors) {
            this.descriptors = descriptors;
            this.access = CuriosCompat.resolveSlots(pet, descriptors);
            this.mirror = NonNullList.withSize(descriptors.size(), ItemStack.EMPTY);
        }

        private boolean isItemAllowed(int index, ItemStack stack) {
            if (index < 0 || index >= descriptors.size()) {
                return false;
            }
            CuriosCompat.SlotAccess slot = access.get(index);
            return slot == null || slot.handler().isItemValid(slot.handlerIndex(), stack);
        }

        @Override
        public int getContainerSize() {
            return descriptors.size();
        }

        @Override
        public boolean isEmpty() {
            for (int index = 0; index < getContainerSize(); index++) {
                if (!getItem(index).isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int index) {
            if (index < 0 || index >= descriptors.size()) {
                return ItemStack.EMPTY;
            }
            CuriosCompat.SlotAccess slot = access.get(index);
            return slot == null
                    ? mirror.get(index)
                    : slot.handler().getStackInSlot(slot.handlerIndex());
        }

        @Override
        public ItemStack removeItem(int index, int count) {
            if (index < 0 || index >= descriptors.size() || count <= 0) {
                return ItemStack.EMPTY;
            }
            CuriosCompat.SlotAccess slot = access.get(index);
            if (slot != null) {
                return slot.handler().extractItem(slot.handlerIndex(), count, false);
            }
            ItemStack current = mirror.get(index);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack removed = current.split(count);
            mirror.set(index, current.isEmpty() ? ItemStack.EMPTY : current);
            return removed;
        }

        @Override
        public ItemStack removeItemNoUpdate(int index) {
            ItemStack current = getItem(index);
            if (current.isEmpty()) {
                return ItemStack.EMPTY;
            }
            setItem(index, ItemStack.EMPTY);
            return current;
        }

        @Override
        public void setItem(int index, ItemStack stack) {
            if (index < 0 || index >= descriptors.size()) {
                return;
            }
            ItemStack limited = stack;
            if (!limited.isEmpty() && limited.getCount() > 1) {
                limited = limited.copyWithCount(1);
            }
            mirror.set(index, limited);
            CuriosCompat.SlotAccess slot = access.get(index);
            if (slot != null) {
                slot.handler().setStackInSlot(slot.handlerIndex(), limited);
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
            for (int index = 0; index < descriptors.size(); index++) {
                setItem(index, ItemStack.EMPTY);
            }
        }
    }

    private record OpenData(
            @Nullable ChangedEntity pet,
            List<AccessorySlotType> accessoryTypes,
            List<CuriosCompat.SlotDescriptor> curioSlots) {
    }

    private final class ArmorSlot extends Slot {
        private final EquipmentSlot equipmentSlot;

        private ArmorSlot(
                Container container,
                int containerSlot,
                int x,
                int y,
                EquipmentSlot equipmentSlot) {
            super(container, containerSlot, x, y);
            this.equipmentSlot = equipmentSlot;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return pet != null
                    && stack.canEquip(equipmentSlot, pet)
                    && canPetWear(stack, equipmentSlot);
        }

        @Override
        public boolean mayPickup(Player player) {
            ItemStack stack = getItem();
            return (stack.isEmpty()
                            || player.isCreative()
                            || !EnchantmentHelper.hasBindingCurse(stack))
                    && super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(
                    InventoryMenu.BLOCK_ATLAS,
                    EMPTY_ARMOR_TEXTURES[equipmentSlot.getIndex()]);
        }
    }

    private final class AccessorySlot extends Slot {
        private final AccessorySlotType slotType;

        private AccessorySlot(
                Container container,
                int containerSlot,
                int x,
                int y,
                AccessorySlotType slotType) {
            super(container, containerSlot, x, y);
            this.slotType = slotType;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return clothingVisible;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return clothingVisible
                    && pet != null
                    && slotType.canHoldItem(stack, pet)
                    && accessoryContainer.isItemAllowed(slotType, stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (!clothingVisible) {
                return false;
            }
            ItemStack stack = getItem();
            return (stack.isEmpty()
                            || player.isCreative()
                            || !EnchantmentHelper.hasBindingCurse(stack))
                    && super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(InventoryMenu.BLOCK_ATLAS, slotType.getNoItemIcon());
        }
    }

    private final class StorageSlot extends Slot {
        private StorageSlot(
                Container container,
                int containerSlot,
                int x,
                int y) {
            super(container, containerSlot, x, y);
        }

        @Override
        public boolean isActive() {
            return !clothingVisible;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return !clothingVisible && super.mayPlace(stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            return !clothingVisible && super.mayPickup(player);
        }
    }

    private final class CurioSlot extends Slot {
        private final CuriosCompat.SlotDescriptor descriptor;

        private CurioSlot(
                Container container,
                int containerSlot,
                int x,
                int y,
                CuriosCompat.SlotDescriptor descriptor) {
            super(container, containerSlot, x, y);
            this.descriptor = descriptor;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean isActive() {
            return curiosVisible;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return curiosVisible
                    && pet != null
                    && curioContainer.isItemAllowed(getContainerSlot(), stack);
        }

        @Override
        public boolean mayPickup(Player player) {
            if (!curiosVisible) {
                return false;
            }
            ItemStack stack = getItem();
            return (stack.isEmpty()
                            || player.isCreative()
                            || !EnchantmentHelper.hasBindingCurse(stack))
                    && super.mayPickup(player);
        }

        @Override
        public Pair<ResourceLocation, ResourceLocation> getNoItemIcon() {
            return Pair.of(
                    InventoryMenu.BLOCK_ATLAS,
                    CuriosCompat.slotIcon(descriptor.identifier()));
        }
    }
}
