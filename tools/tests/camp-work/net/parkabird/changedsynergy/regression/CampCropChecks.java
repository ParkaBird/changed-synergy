package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.PlayerOutpostData;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Outpost;
import net.parkabird.changedsynergy.ai.PlayerOutpostData.Work;
import net.parkabird.changedsynergy.ai.PlayerOutpostSupplyType;
import net.parkabird.changedsynergy.ai.PlayerOutpostWorkGoal;

/** Optional-mod checks use real block methods, actual loot and two sequential worker submissions. */
public final class CampCropChecks {
    private static final String BAG = "ChangedSynergyPlayerOutpostWorkBag";
    private CampCropChecks() {}

    public static void run(ServerLevel level, ChangedEntity mob, Outpost camp,
                           PlayerOutpostWorkGoal goal, BlockPos position) throws Exception {
        if (!ModList.get().isLoaded("farmersdelight")) return;
        check(camp.farmContains(position) && camp.farmContains(position.above()), "Special crop belongs to linked farm");
        CompoundTag oldData = mob.getPersistentData().copy();
        ItemStack oldHand = mob.getMainHandItem();
        Object oldTarget = read(goal, "block");
        String oldStatus = camp.members.get(mob.getUUID()).workStatus;
        boolean oldFarming = ChangedSynergyConfig.COMMON.playerOutpostFarming.get();
        Map<BlockPos, BlockState> oldBlocks = new LinkedHashMap<>();
        ChangedEntity second = (ChangedEntity)mob.getType().create(level);
        check(second != null, "Create second actual farmer");
        second.setPos(mob.getX(), mob.getY(), mob.getZ());
        level.addFreshEntity(second);
        PlayerOutpostData data = PlayerOutpostData.get(level.getServer());
        check(data.add(camp, second.getUUID(), "CropCheckSecond", camp.dimension,
                second.blockPosition(), PlayerOutpostSupplyType.ORANGE), "Second farmer recruited");
        data.setWork(camp, second.getUUID(), Work.FARM, null);
        PlayerOutpostWorkGoal other = new PlayerOutpostWorkGoal(second);
        set(other, "camp", camp);
        set(other, "member", camp.members.get(second.getUUID()));
        try {
            ChangedSynergyConfig.COMMON.playerOutpostFarming.set(true);
            mob.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            mob.getPersistentData().remove(BAG);
            Block tomatoes = block("tomatoes");
            Block hanging = block("tomatoes_on_rope");
            CropBlock tomatoCrop = (CropBlock)tomatoes;
            CropBlock hangingCrop = (CropBlock)hanging;
            replace(level, oldBlocks, position.below(), Blocks.FARMLAND.defaultBlockState());
            replace(level, oldBlocks, position.above(), Blocks.AIR.defaultBlockState());
            replace(level, oldBlocks, position, tomatoCrop.getStateForAge(tomatoCrop.getMaxAge()));
            set(goal, "block", position);
            int before = total(level, mob, second, position, item("tomato"));
            farm(goal, level);
            check(level.getBlockState(position).is(tomatoes)
                    && tomatoCrop.getAge(level.getBlockState(position)) == 0,
                    "Native tomato picking retains stem and resets fruit age");
            int harvested = total(level, mob, second, position, item("tomato"));
            check(harvested > before, "Native tomato picking produces actual tomatoes");
            set(other, "block", position);
            farm(other, level);
            check(total(level, mob, second, position, item("tomato")) == harvested,
                    "Second farmer cannot harvest the same immature tomatoes again");

            BlockState root = level.getBlockState(position);
            replace(level, oldBlocks, position.above(), hangingCrop.getStateForAge(hangingCrop.getMaxAge()));
            set(goal, "block", position.above());
            before = total(level, mob, second, position, item("tomato"));
            farm(goal, level);
            check(level.getBlockState(position.above()).is(hanging)
                    && hangingCrop.getAge(level.getBlockState(position.above())) == 0
                    && level.getBlockState(position).equals(root),
                    "Rope tomato picking retains rope crop and lower vine");
            harvested = total(level, mob, second, position, item("tomato"));
            check(harvested > before, "Rope vine produces actual tomatoes");
            set(other, "block", position.above());
            farm(other, level);
            check(total(level, mob, second, position, item("tomato")) == harvested,
                    "Second farmer cannot duplicate rope-vine harvest");

            Block rice = block("rice");
            CropBlock panicle = (CropBlock)block("rice_panicles");
            replace(level, oldBlocks, position.above(), Blocks.AIR.defaultBlockState());
            replace(level, oldBlocks, position.below(), Blocks.DIRT.defaultBlockState());
            IntegerProperty riceAge = (IntegerProperty)rice.getStateDefinition().getProperty("age");
            BooleanProperty supporting = (BooleanProperty)rice.getStateDefinition().getProperty("supporting");
            check(riceAge != null && supporting != null, "Rice has native root-state properties");
            replace(level, oldBlocks, position, rice.defaultBlockState().setValue(riceAge, 3));
            replace(level, oldBlocks, position.above(), panicle.getStateForAge(panicle.getMaxAge()));
            check(level.getFluidState(position).is(FluidTags.WATER)
                    && level.getBlockState(position).getValue(supporting), "Mature rice retains source water and upper support");
            set(goal, "block", position.above());
            Item ricePanicle = item("rice_panicle");
            before = total(level, mob, second, position, ricePanicle);
            farm(goal, level);
            check(level.getBlockState(position.above()).isAir()
                    && level.getBlockState(position).is(rice)
                    && level.getBlockState(position).getValue(riceAge) == 3
                    && !level.getBlockState(position).getValue(supporting)
                    && level.getFluidState(position).is(FluidTags.WATER)
                    && level.getFluidState(position).isSource(),
                    "Rice harvest keeps mature waterlogged root ready for native regrowth");
            harvested = total(level, mob, second, position, ricePanicle);
            check(harvested == before + 1, "Rice harvest retains exactly one native panicle drop");
            set(other, "block", position.above());
            farm(other, level);
            check(total(level, mob, second, position, ricePanicle) == harvested,
                    "Second farmer cannot harvest an already removed panicle");
            System.out.println("CAMP_FD_SPECIAL_CROP_REGRESSION_PASS");
        } finally {
            data.remove(camp, second.getUUID());
            second.discard();
            for (Map.Entry<BlockPos, BlockState> entry : oldBlocks.entrySet())
                level.setBlock(entry.getKey(), entry.getValue(), Block.UPDATE_ALL);
            mob.getPersistentData().getAllKeys().stream().toList().forEach(mob.getPersistentData()::remove);
            mob.getPersistentData().merge(oldData);
            mob.setItemInHand(InteractionHand.MAIN_HAND, oldHand);
            set(goal, "block", oldTarget);
            camp.members.get(mob.getUUID()).workStatus = oldStatus;
            ChangedSynergyConfig.COMMON.playerOutpostFarming.set(oldFarming);
        }
    }

    private static void replace(ServerLevel level, Map<BlockPos, BlockState> original, BlockPos pos, BlockState state) {
        original.putIfAbsent(pos.immutable(), level.getBlockState(pos));
        level.setBlock(pos, state, Block.UPDATE_ALL);
    }

    private static int total(ServerLevel level, ChangedEntity first, ChangedEntity second, BlockPos center, Item item) throws Exception {
        Method bag = PlayerOutpostWorkGoal.class.getDeclaredMethod("bag", ChangedEntity.class);
        bag.setAccessible(true);
        int result = 0;
        for (ChangedEntity creature : List.of(first, second))
            for (ItemStack stack : (List<ItemStack>)bag.invoke(null, creature)) if (stack.is(item)) result += stack.getCount();
        for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(center).inflate(3.0D),
                entity -> entity.isAlive() && entity.getItem().is(item))) result += drop.getItem().getCount();
        return result;
    }

    private static void farm(PlayerOutpostWorkGoal goal, ServerLevel level) throws Exception {
        Method method = PlayerOutpostWorkGoal.class.getDeclaredMethod("farm", ServerLevel.class);
        method.setAccessible(true);
        method.invoke(goal, level);
    }

    private static Block block(String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse("farmersdelight:" + path));
        check(block != null && block != Blocks.AIR, "Farmer's Delight block exists: " + path);
        return block;
    }

    private static Item item(String path) {
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse("farmersdelight:" + path));
        check(item != null, "Farmer's Delight item exists: " + path);
        return item;
    }

    private static Object read(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(target);
    }

    private static void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target, value);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
