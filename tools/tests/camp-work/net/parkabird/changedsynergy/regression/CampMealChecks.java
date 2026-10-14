package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.ltxprogrammer.changed.block.Pillow;
import net.ltxprogrammer.changed.block.entity.PillowBlockEntity;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.SeatEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.parkabird.changedsynergy.world.inventory.BondedInventoryService;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Role;
import net.parkabird.changedsynergy.ai.PlayerOutpostMealGoal;

/** Real loaded-world meal checks; included only by camp-work.init.gradle. */
public final class CampMealChecks {
    private CampMealChecks() {}

    public static void run(ServerLevel level, ChangedEntity mob, Outpost camp, Container chest) throws Exception {
        var member = camp.members.get(mob.getUUID());
        check(member != null, "Meal fixture has a real recruited member");
        boolean mealsConfig = ChangedSynergyConfig.COMMON.playerOutpostMeals.get();
        boolean offlineConfig = ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.get();
        boolean oldMeals = camp.meals;
        long oldRequest = camp.mealRequestedAt;
        BlockPos oldCooking = camp.cooking;
        Role oldRole = member.role;
        String oldStatus = member.workStatus;
        int oldTick = mob.tickCount;
        boolean oldGround = mob.onGround();
        float oldHealth = mob.getHealth();
        Vec3 oldPosition = mob.position();
        CompoundTag oldData = mob.getPersistentData().copy();
        Container own = BondedInventoryService.inventory(mob, null);
        List<ItemStack> personalItems = new ArrayList<>();
        for (int slot = 0; slot < Math.min(24, own.getContainerSize()); slot++) personalItems.add(own.getItem(slot).copy());
        ItemStack oldHand = mob.getOffhandItem().copy();
        var oldPoints = new HashSet<>(camp.restPoints);
        List<ItemStack> oldItems = new ArrayList<>();
        for (int slot = 0; slot < chest.getContainerSize(); slot++) oldItems.add(chest.getItem(slot).copy());
        Map<BlockPos, BlockState> oldBlocks = new LinkedHashMap<>();
        List<PlayerOutpostMealGoal> goals = new ArrayList<>();
        SeatEntity foreignSeat = null;
        try {
            ChangedSynergyConfig.COMMON.playerOutpostMeals.set(true);
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(true);
            camp.meals = true;
            camp.mealRequestedAt = level.getGameTime();
            camp.cooking = camp.marker;
            member.role = Role.SUPPLY;
            mob.getPersistentData().remove("ChangedSynergyOutpostMealDay");
            mob.getPersistentData().remove("ChangedSynergyOutpostGatherDay");
            mob.getPersistentData().remove("ChangedSynergyOutpostMealRequest");
            mob.getPersistentData().remove("ChangedSynergyOutpostMealUnavailableUntil");
            mob.tickCount = Math.floorMod(mob.getId() + 1, 40);
            mob.setOnGround(true);
            check(!mob.isPassenger(), "Fixture does not start in another interaction's seat");
            check(level.getBlockState(camp.marker).getBlock() instanceof CampfireBlock,
                    "Meal fixture uses an actual vanilla campfire");

            Pillow pillow = ForgeRegistries.BLOCKS.getValues().stream()
                    .filter(block -> block instanceof Pillow).map(block -> (Pillow)block)
                    .findFirst().orElseThrow(() -> new AssertionError("Changed provides a native Pillow block"));
            BlockPos cushion = camp.marker.offset(4, 0, 2);
            replace(level, oldBlocks, cushion.below(), Blocks.STONE.defaultBlockState());
            replace(level, oldBlocks, cushion, pillow.defaultBlockState().setValue(Pillow.OCCUPIED, false));
            camp.restPoints.add(cushion);
            for (int up = 1; up < 4; up++)
                replace(level, oldBlocks, cushion.above(up), Blocks.AIR.defaultBlockState());
            mob.setPos(cushion.getX() + 0.5D, cushion.getY(), cushion.getZ() + 0.5D);
            var meal = new PlayerOutpostMealGoal(mob);
            goals.add(meal);
            check(meal.canUse(), "A requested meal finds reachable seating without waiting for the routine scan modulo");
            check((boolean)get(meal, "pillow") && cushion.equals(get(meal, "place")),
                    "Nearby free Pillow is selected before bare ground");
            Method safePlace = method("safePlace", ServerLevel.class, BlockPos.class, boolean.class);
            check((boolean)safePlace.invoke(meal, level, cushion, true),
                    "Actual Pillow collision height leaves legal sitting space");
            check(!(boolean)safePlace.invoke(meal, level, camp.marker, false),
                    "A campfire block cannot be selected as a ground seat");
            var orange = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("changed:orange"));
            check(orange != null, "Changed orange exists for the meal inventory check");
            for (int slot = 0; slot < personalItems.size(); slot++) own.setItem(slot, ItemStack.EMPTY);
            own.setItem(0, new ItemStack(orange, 3)); own.setChanged();
            mob.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
            chest.clearContent(); chest.setItem(0, new ItemStack(orange, 5));
            meal.start();
            mob.setPos(camp.marker.getX() - 8.5D, cushion.getY(), cushion.getZ() + 0.5D);
            meal.tick();
            var navigation = mob.getNavigation();
            check(!navigation.isDone() && navigation.getPath().getNodeCount() > 2,
                    "A distant meal attendee receives a real walking route");
            var route = navigation.getPath();
            for (int tick = 0; tick < 12; tick++) meal.tick();
            check(navigation.getPath() == route, "A usable meal route is retained while walking");
            navigation.stop();
            for (int tick = 0; tick < 10; tick++) meal.tick();
            check(!navigation.isDone(), "Interrupted meal navigation resumes within ten goal updates");
            double beforeWalking = mob.distanceToSqr(Vec3.atBottomCenterOf(cushion));
            for (int tick = 0; tick < 12; tick++) {
                meal.tick(); navigation.tick(); mob.getMoveControl().tick();
                mob.travel(new Vec3(mob.xxa, mob.yya, mob.zza));
            }
            check(mob.distanceToSqr(Vec3.atBottomCenterOf(cushion)) < beforeWalking,
                    "Native navigation and movement control actually advance toward the meal");
            mob.setPos(cushion.getX() + 0.5D, cushion.getY(), cushion.getZ() + 0.5D);
            mob.setOnGround(true);
            meal.tick();
            check(mob.getVehicle() instanceof SeatEntity, "Native Pillow actually seats the resident");
            SeatEntity ownSeat = (SeatEntity)mob.getVehicle();
            PillowBlockEntity cushionEntity = (PillowBlockEntity)level.getBlockEntity(cushion);
            // OCCUPIED represents sleeping; ordinary native sitting is tracked by the seat's passenger.
            check(cushionEntity != null && cushionEntity.getSeatedEntity() == mob,
                    "Native Pillow records the actual seated resident");
            check(!(boolean)safePlace.invoke(meal, level, cushion, true),
                    "A seated Pillow cannot be claimed by another camp meal");

            for (int tick = 0; tick < 40; tick++) meal.tick();
            check(mob.isUsingItem() && mob.getUsedItemHand() == InteractionHand.OFF_HAND
                            && mob.getOffhandItem().is(orange) && count(BondedInventoryService.inventory(mob, null), orange) == 2 && count(chest, orange) == 5,
                    "Eating stages real personal food: using=" + mob.isUsingItem() + ", hand=" + mob.getOffhandItem()
                            + ", personal=" + count(BondedInventoryService.inventory(mob, null), orange)
                            + ", communal=" + count(chest, orange) + ", status=" + member.workStatus);
            Method useTick = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_21329_");
            useTick.setAccessible(true);
            useTick.invoke(mob);
            check(!mob.getPersistentData().contains("ChangedSynergyOutpostMealDay") && mob.isUsingItem(),
                    "One animation tick does not prematurely consume the food");
            for (int tick = 0; tick < 40 && mob.isUsingItem(); tick++) useTick.invoke(mob);
            meal.tick();
            check(mob.getPersistentData().contains("ChangedSynergyOutpostMealDay")
                            && count(BondedInventoryService.inventory(mob, null), orange) == 2 && mob.getOffhandItem().is(Items.SHIELD),
                    "Real native food completion records the meal and restores the original offhand");
            var secondMeal = new PlayerOutpostMealGoal(mob);
            set(secondMeal, "outpost", camp);
            method("eat", ServerLevel.class).invoke(secondMeal, level);
            check(count(BondedInventoryService.inventory(mob, null), orange) == 2 && count(chest, orange) == 5,
                    "A recreated goal cannot consume a second same-day meal");

            member.role = Role.CREW;
            check(!meal.canContinueToUse(), "A following-crew assignment interrupts camp meals");
            meal.stop();
            check(!mob.isPassenger() && ownSeat.isRemoved(), "Role change clears only the meal's seat");
            check(cushionEntity.getSeatedEntity() == null && !level.getBlockState(cushion).getValue(Pillow.OCCUPIED),
                    "Leaving a meal releases native Pillow seating without setting sleeping occupation");

            member.role = Role.SUPPLY;
            replace(level, oldBlocks, cushion, Blocks.AIR.defaultBlockState());
            mob.getPersistentData().remove("ChangedSynergyOutpostMealRequest");
            mob.getPersistentData().remove("ChangedSynergyOutpostMealUnavailableUntil");
            mob.setPos(cushion.getX() + 0.5D, cushion.getY(), cushion.getZ() + 0.5D);
            mob.setOnGround(true);
            var groundMeal = new PlayerOutpostMealGoal(mob);
            goals.add(groundMeal);
            check(groundMeal.canUse() && !(boolean)get(groundMeal, "pillow"),
                    "Removing the nearby Pillow falls back to reachable safe ground");
            BlockPos ground = (BlockPos)get(groundMeal, "place");
            mob.setPos(ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D);
            groundMeal.start();
            groundMeal.tick();
            check(mob.getVehicle() instanceof SeatEntity, "Ground fallback creates a real seated pose");
            SeatEntity groundSeat = (SeatEntity)mob.getVehicle();
            ChangedSynergyConfig.COMMON.playerOutpostMeals.set(false);
            check(!groundMeal.canContinueToUse(), "Disabling meals interrupts a gathering immediately");
            groundMeal.stop();
            check(!mob.isPassenger() && groundSeat.isRemoved(), "Disabled gathering removes its own ground seat");
            interruptedFood(level, mob, camp, own);
            communalFood(level, mob, camp, chest, goals, orange);

            BlockPos otherFloor = camp.marker.offset(6, -1, 2);
            replace(level, oldBlocks, otherFloor, Blocks.STONE.defaultBlockState());
            foreignSeat = SeatEntity.createFor(level, level.getBlockState(otherFloor), otherFloor,
                    false, false, true);
            check(foreignSeat != null && mob.startRiding(foreignSeat, true),
                    "Fixture starts an independent non-meal seated interaction");
            PlayerOutpostMealGoal.cleanup(mob);
            check(mob.getVehicle() == foreignSeat && !foreignSeat.isRemoved(),
                    "Meal cleanup preserves another interaction's seat");
            mob.getPersistentData().putUUID("ChangedSynergyOutpostMealSeat", UUID.randomUUID());
            mob.getPersistentData().putLong("ChangedSynergyOutpostMealPlace", cushion.asLong());
            PlayerOutpostMealGoal.cleanupStale(mob);
            check(mob.getVehicle() == foreignSeat && !foreignSeat.isRemoved(),
                    "Stale meal UUID cannot remove a different current seat");
            System.out.println("CAMP_MEAL_REGRESSION_PASS");
        } finally {
            for (PlayerOutpostMealGoal goal : goals) goal.stop();
            PlayerOutpostMealGoal.cleanup(mob);
            if (foreignSeat != null) {
                if (mob.getVehicle() == foreignSeat) mob.stopRiding();
                foreignSeat.discard();
            }
            for (var entry : oldBlocks.entrySet()) level.setBlock(entry.getKey(), entry.getValue(), 3);
            chest.clearContent();
            for (int slot = 0; slot < oldItems.size(); slot++) chest.setItem(slot, oldItems.get(slot));
            chest.setChanged();
            for (int slot = 0; slot < personalItems.size(); slot++) own.setItem(slot, personalItems.get(slot));
            own.setChanged(); mob.setItemInHand(InteractionHand.OFF_HAND, oldHand);
            camp.restPoints.clear(); camp.restPoints.addAll(oldPoints);
            for (String key : new HashSet<>(mob.getPersistentData().getAllKeys())) mob.getPersistentData().remove(key);
            mob.getPersistentData().merge(oldData);
            mob.setPos(oldPosition.x, oldPosition.y, oldPosition.z);
            mob.setOnGround(oldGround);
            mob.setHealth(oldHealth);
            mob.tickCount = oldTick;
            member.role = oldRole;
            member.workStatus = oldStatus;
            camp.meals = oldMeals;
            camp.mealRequestedAt = oldRequest;
            camp.cooking = oldCooking;
            ChangedSynergyConfig.COMMON.playerOutpostMeals.set(mealsConfig);
            ChangedSynergyConfig.COMMON.playerOutpostWorkWhileOwnerOffline.set(offlineConfig);
        }
    }

    private static void interruptedFood(ServerLevel level, ChangedEntity mob, Outpost camp, Container own) throws Exception {
        mob.getPersistentData().remove("ChangedSynergyOutpostMealDay");
        var goal = new PlayerOutpostMealGoal(mob);
        set(goal, "outpost", camp);
        int before = count(BondedInventoryService.inventory(mob, null), Items.MUSHROOM_STEW);
        method("prepareFood", ItemStack.class).invoke(goal, new ItemStack(Items.MUSHROOM_STEW));
        method("eat", ServerLevel.class).invoke(goal, level);
        check(mob.isUsingItem(), "A second fixture begins actual eating");
        PlayerOutpostMealGoal.cleanupStale(mob);
        check(!mob.isUsingItem() && mob.getOffhandItem().is(Items.SHIELD)
                        && count(BondedInventoryService.inventory(mob, null), Items.MUSHROOM_STEW) == before + 1,
                "Reload/interruption restores real unconsumed food and original equipment once");
        PlayerOutpostMealGoal.cleanup(mob);
        check(count(BondedInventoryService.inventory(mob, null), Items.MUSHROOM_STEW) == before + 1, "Repeated cleanup never duplicates a staged serving");
        method("prepareFood", ItemStack.class).invoke(goal, new ItemStack(Items.MUSHROOM_STEW));
        method("eat", ServerLevel.class).invoke(goal, level);
        Method useTick = net.minecraftforge.fml.util.ObfuscationReflectionHelper.findMethod(LivingEntity.class, "m_21329_"); useTick.setAccessible(true);
        int bowls = count(BondedInventoryService.inventory(mob, null), Items.BOWL);
        for (int tick = 0; tick < 40 && mob.isUsingItem(); tick++) useTick.invoke(mob);
        method("eat", ServerLevel.class).invoke(goal, level);
        check(count(BondedInventoryService.inventory(mob, null), Items.BOWL) == bowls + 1 && mob.getOffhandItem().is(Items.SHIELD),
                "A fully eaten serving returns one real bowl and keeps the original equipment");
        System.out.println("CAMP_NATIVE_EATING_INTERRUPTION_PASS");
    }

    private static void communalFood(ServerLevel level, ChangedEntity mob, Outpost camp, Container chest,
                                     List<PlayerOutpostMealGoal> goals, net.minecraft.world.item.Item orange) throws Exception {
        ChangedSynergyConfig.COMMON.playerOutpostMeals.set(true);
        for (String key : new String[]{"ChangedSynergyOutpostMealDay", "ChangedSynergyOutpostMealRequest",
                "ChangedSynergyOutpostGatherDay", "ChangedSynergyOutpostMealUnavailableUntil"})
            mob.getPersistentData().remove(key);
        Container own = BondedInventoryService.inventory(mob, null);
        for (int slot = 0; slot < Math.min(24, own.getContainerSize()); slot++) own.setItem(slot, ItemStack.EMPTY);
        own.setChanged(); chest.clearContent(); chest.setItem(0, new ItemStack(orange, 5)); chest.setChanged();
        mob.setOnGround(true);
        var meal = new PlayerOutpostMealGoal(mob); goals.add(meal);
        check(meal.canUse(), "A hungry member can attend a camp meal");
        meal.start();
        check((boolean)get(meal, "fetchingFood") && count(chest, orange) == 5,
                "Missing personal food schedules a real storage visit without remote withdrawal");
        BlockPos stand = (BlockPos)get(meal, "foodStand");
        check(stand != null, "Storage has a reachable access point");
        mob.setPos(stand.getX() + 0.5D, stand.getY(), stand.getZ() + 0.5D);
        meal.tick();
        check(!(boolean)get(meal, "fetchingFood") && count(chest, orange) == 4
                        && mob.getOffhandItem().is(orange) && !mob.isUsingItem(),
                "At storage the member takes exactly one real serving before seating and eating");
        meal.stop();
        check(mob.getOffhandItem().is(Items.SHIELD)
                        && count(BondedInventoryService.inventory(mob, null), orange) == 1 && count(chest, orange) == 4,
                "An interrupted communal meal returns the withdrawn serving without loss or duplication");
        System.out.println("CAMP_COMMUNAL_FOOD_FETCH_PASS");
    }

    private static int count(Container chest, net.minecraft.world.item.Item item) {
        int count = 0;
        int slots = chest instanceof net.parkabird.changedsynergy.world.inventory.BondedCreatureInventory
                ? Math.min(24, chest.getContainerSize()) : chest.getContainerSize();
        for (int slot = 0; slot < slots; slot++) {
            if (chest.getItem(slot).is(item)) count += chest.getItem(slot).getCount();
        }
        return count;
    }

    private static void replace(ServerLevel level, Map<BlockPos, BlockState> saved,
                                BlockPos pos, BlockState state) {
        saved.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, 3);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Method method(String name, Class<?>... arguments) throws Exception {
        Method method = PlayerOutpostMealGoal.class.getDeclaredMethod(name, arguments);
        method.setAccessible(true);
        return method;
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
