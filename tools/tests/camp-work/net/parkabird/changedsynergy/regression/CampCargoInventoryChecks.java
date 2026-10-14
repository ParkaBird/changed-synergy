package net.parkabird.changedsynergy.regression;

import io.netty.buffer.Unpooled;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.parkabird.changedsynergy.ai.CreaturePersonality;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostGoal;
import net.parkabird.changedsynergy.ai.PlayerOutpostService;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;
import net.parkabird.changedsynergy.world.inventory.BondedCreatureInventoryMenu;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.parkabird.changedsynergy.world.inventory.PlayerOutpostCargoInventory;

/** Open-menu and saved-cargo accounting: player extraction, live AI updates and release. */
final class CampCargoInventoryChecks {
    static void run(ServerPlayer owner, ChangedEntity mob, PlayerOutpostData.Outpost camp, Container chest) throws Exception {
        var level = owner.serverLevel();
        var originalData = mob.getPersistentData().copy();
        var oldOwnerData = owner.getPersistentData().copy();
        var originalPosition = mob.position(); var ownerPosition = owner.position();
        var oldConnection = owner.connection; var oldMenu = owner.containerMenu;
        var playerItems = owner.getInventory().save(new ListTag());
        var oldArmor = mob.getItemBySlot(EquipmentSlot.CHEST).copy();
        var personal = BondedInventoryService.inventory(mob, owner);
        var personalItems = new ArrayList<ItemStack>();
        for (int i = 0; i < personal.getContainerSize(); i++) personalItems.add(personal.getItem(i).copy());
        boolean addedViewer = !level.players().contains(owner);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        ItemEntity pickup = null;
        List<ItemEntity> released = new ArrayList<>();
        try {
            if (addedViewer) level.players().add(owner);
            owner.connection = new ServerGamePacketListenerImpl(owner.server, new Connection(PacketFlow.SERVERBOUND), owner);
            mob.setPos(6.5D, 90, 3.5D); owner.setPos(6.5D, 90, 4.5D);
            CreaturePersonality.establishRelationship(mob, owner);
            CreaturePersonality.setFamiliarity(mob, owner, 60);
            owner.getInventory().clearContent();
            personal.setItem(0, new ItemStack(Items.DIAMOND, 2));
            mob.setItemSlot(EquipmentSlot.CHEST, new ItemStack(Items.IRON_CHESTPLATE));

            // Simulate an old save using its original compact work bag and gathering key.
            var legacy = new ListTag();
            legacy.add(new ItemStack(Items.APPLE, 2).save(new CompoundTag()));
            legacy.add(new ItemStack(Items.POTATO, 3).save(new CompoundTag()));
            mob.getPersistentData().put("ChangedSynergyPlayerOutpostWorkBag", legacy);
            mob.getPersistentData().put("ChangedSynergyPlayerOutpostCargo", new ItemStack(Items.COD, 4).save(new CompoundTag()));
            mob.getPersistentData().putInt("ChangedSynergyPlayerOutpostHaulBatch", 4);
            var menu = new BondedCreatureInventoryMenu(1, owner.getInventory(), mob, personal);
            owner.containerMenu = menu;
            check(menu.stillValid(owner) && menu.hasWorkCargo(), "Trusted member exposes its saved work inventory");
            var cargo = (PlayerOutpostCargoInventory)menu.getWorkInventory();
            check(PlayerOutpostCargoInventory.inventory(mob) == cargo, "AI and viewer share one real cargo container");
            check(cargo.getItem(0).getCount() == 2 && cargo.getItem(1).getCount() == 3
                            && PlayerOutpostGoal.cargo(mob).getCount() == 4,
                    "Legacy gathering and all multi-item work cargo remain recoverable");
            check(PlayerOutpostCargoInventory.hasWorkItems(mob) && PlayerOutpostCargoInventory.hasGatherItem(mob),
                    "Availability checks recognize legacy cargo without constructing a viewer inventory");

            BondedCreatureInventoryMenu.writeOpenData(buffer, owner, mob);
            var mirror = new BondedCreatureInventoryMenu(1, owner.getInventory(), buffer);
            check(buffer.readableBytes() == 0 && mirror.hasWorkCargo() && mirror.slots.size() == menu.slots.size()
                            && !(mirror.getWorkInventory() instanceof PlayerOutpostCargoInventory),
                    "Opening payload gives the client the same slot layout without a second server inventory");
            mirror.initializeContents(0, menu.getItems(), ItemStack.EMPTY);
            check(mirror.getWorkInventory().getItem(0).getCount() == 2, "Vanilla slot sync fills the client work row");
            int workStart = menu.slots.size() - PlayerOutpostCargoInventory.SIZE;
            for (int i = 0; i < PlayerOutpostCargoInventory.SIZE; i++)
                check(!menu.slots.get(workStart + i).mayPlace(new ItemStack(Items.APPLE)), "Cargo is extraction-only");

            menu.quickMoveStack(owner, workStart);
            check(cargo.getItem(0).isEmpty() && cargo.getItem(1).getCount() == 3
                            && count(owner.getInventory(), Items.APPLE) == 2,
                    "Shift-click takes the cargo once without moving its neighbouring saved slots");
            check(new PlayerOutpostCargoInventory(mob).getItem(0).isEmpty()
                            && new PlayerOutpostCargoInventory(mob).getItem(1).getCount() == 3,
                    "Extraction is written through to the saved inventory immediately");
            menu.clicked(workStart + PlayerOutpostCargoInventory.GATHER_SLOT, 1, ClickType.PICKUP, owner);
            check(menu.getCarried().getCount() == 2 && PlayerOutpostGoal.cargo(mob).getCount() == 2,
                    "Manual right-click splits real gathering cargo");
            owner.getInventory().add(menu.getCarried()); menu.setCarried(ItemStack.EMPTY);
            menu.quickMoveStack(owner, workStart + PlayerOutpostCargoInventory.GATHER_SLOT);
            check(count(owner.getInventory(), Items.COD) == 4 && !PlayerOutpostGoal.hasCargo(mob),
                    "Gathering cannot deliver what the player has already recovered");
            check(!PlayerOutpostCargoInventory.hasGatherItem(mob),
                    "Fast gathering check follows live manual and Shift-click extraction");

            pickup = new ItemEntity(level, mob.getX(), mob.getY(), mob.getZ(), new ItemStack(Items.BEEF, 3));
            pickup.tickCount = 100; pickup.setPickUpDelay(0);
            level.addFreshEntity(pickup);
            var work = new PlayerOutpostWorkGoal(mob);
            field(work, "camp", camp); field(work, "member", camp.members.get(mob.getUUID())); field(work, "pickup", pickup);
            call(work, "collectPickup", level);
            check(!pickup.isAlive() && count(cargo, Items.BEEF) == 3 && count(cargo, Items.POTATO) == 3,
                    "Live collecting updates the displayed container without restoring taken items");
            for (int i = 0; i < PlayerOutpostCargoInventory.WORK_SLOTS; i++) menu.quickMoveStack(owner, workStart + i);
            check(!PlayerOutpostWorkGoal.hasCargo(mob) && !mob.getPersistentData().contains("ChangedSynergyPlayerOutpostHaulBatch")
                            && count(owner.getInventory(), Items.BEEF) == 3 && count(owner.getInventory(), Items.POTATO) == 3,
                    "Taking the whole bag ends its batch and conserves all collected resources");
            check(!PlayerOutpostCargoInventory.hasWorkItems(mob),
                    "Fast work check becomes empty as soon as the displayed cargo is taken");
            int before = total(chest);
            field(work, "block", camp.storage); mob.setPos(Vec3.atCenterOf(camp.storage).add(1, 0, 0));
            call(work, "deposit", level);
            check(total(chest) == before, "A previously selected deposit cannot duplicate retrieved cargo");
            check(personal.getItem(0).is(Items.DIAMOND) && personal.getItem(0).getCount() == 2
                            && mob.getItemBySlot(EquipmentSlot.CHEST).is(Items.IRON_CHESTPLATE),
                    "Work extraction and delivery preserve personal belongings and armour");

            cargo.setWorkItems(List.of(new ItemStack(Items.BONE, 2)));
            PlayerOutpostGoal.setCargo(mob, new ItemStack(Items.POTATO));
            var existing = level.getEntitiesOfClass(ItemEntity.class, new AABB(mob.blockPosition()).inflate(3));
            PlayerOutpostService.releaseCargo(mob);
            released = level.getEntitiesOfClass(ItemEntity.class, new AABB(mob.blockPosition()).inflate(3), item -> !existing.contains(item));
            check(cargo.isEmpty() && !PlayerOutpostWorkGoal.hasCargo(mob) && !PlayerOutpostGoal.hasCargo(mob)
                            && released.stream().mapToInt(item -> item.getItem().getCount()).sum() == 3,
                    "Death or assignment removal drains the open real inventory into drops once");
            PlayerOutpostService.releaseCargo(mob);
            check(level.getEntitiesOfClass(ItemEntity.class, new AABB(mob.blockPosition()).inflate(3), item -> !existing.contains(item)).size()
                            == released.size(), "A second release cannot recreate dropped resources");
            System.out.println("CAMP_WORK_CARGO_INVENTORY_PASS");
        } finally {
            if (owner.containerMenu != oldMenu) owner.containerMenu.removed(owner);
            owner.containerMenu = oldMenu;
            if (pickup != null) pickup.discard(); released.forEach(ItemEntity::discard);
            if (addedViewer) {
                // Drops track the temporary viewer even without adding its entity to the world.
                level.getChunkSource().removeEntity(owner);
                level.players().remove(owner);
            }
            owner.connection = oldConnection;
            owner.getInventory().load(playerItems);
            for (int i = 0; i < personalItems.size(); i++) personal.setItem(i, personalItems.get(i));
            mob.setItemSlot(EquipmentSlot.CHEST, oldArmor);
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(originalData);
            owner.getPersistentData().getAllKeys().stream().toList().forEach(owner.getPersistentData()::remove);
            owner.getPersistentData().merge(oldOwnerData);
            mob.setPos(originalPosition); owner.setPos(ownerPosition);
            buffer.release();
        }
    }

    private static int count(Container inventory, net.minecraft.world.item.Item item) {
        int count = 0; for (int i = 0; i < inventory.getContainerSize(); i++) if (inventory.getItem(i).is(item)) count += inventory.getItem(i).getCount(); return count;
    }
    private static int total(Container inventory) {
        int count = 0; for (int i = 0; i < inventory.getContainerSize(); i++) count += inventory.getItem(i).getCount(); return count;
    }
    private static void field(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }
    private static void call(Object target, String name, net.minecraft.server.level.ServerLevel level) throws Exception {
        var method = target.getClass().getDeclaredMethod(name, net.minecraft.server.level.ServerLevel.class); method.setAccessible(true); method.invoke(target, level);
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
