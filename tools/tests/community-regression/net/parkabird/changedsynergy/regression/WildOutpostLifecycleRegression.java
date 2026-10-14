package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.parkabird.changedsynergy.ai.CreatureCommunityData;
import net.parkabird.changedsynergy.ai.CreatureSettlementService;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints.Blueprint;

/** Blueprints, stock and cargo use production methods in a private server world. */
final class WildOutpostLifecycleRegression {
    private static int checks;
    private static final String BLUEPRINT = "ChangedSynergyCacheBlueprint";
    private static final String DECORATED = "ChangedSynergyCacheDecorated";

    static void run(MinecraftServer server) throws Exception {
        var records = CreatureCommunityData.class.getDeclaredField("communities"); records.setAccessible(true);
        var data = CreatureCommunityData.get(server);
        Object previousRecords = records.get(data);
        records.set(data, new LinkedHashMap<>());
        try {
        List<String> layouts = List.of("dark_cache", "light_cache", "cave_cache", "taiga_cache",
                "snowy_cache", "desert_cache", "badlands_cache", "ruin_cache", "dark_ruin_cache",
                "white_ruin_cache", "aquatic_ruin_cache", "latex_bee_hive_cache", "aquatic_cache", "aquatic_offshore_cache");
        for (int i = 0; i < layouts.size(); i++) layout(server, layouts.get(i), i);
        borderMigration(server);
        structurePlacement(server, false);
        structurePlacement(server, true);
        System.out.println("WILD_OUTPOST_LIFECYCLE_REGRESSION_PASS: " + checks + " checks");
        } finally { records.set(data, previousRecords); }
    }

    private static Method method(String name, Class<?>... arguments) throws Exception {
        Method result = CreatureSettlementService.class.getDeclaredMethod(name, arguments);
        result.setAccessible(true); return result;
    }
    private static ResourceLocation id(String name) { return new ResourceLocation("changed_synergy", name); }
    private static int count(Container container) {
        int count = 0; for (int slot = 0; slot < container.getContainerSize(); slot++) count += container.getItem(slot).getCount();
        return count;
    }
    private static boolean sameStock(List<CreatureCommunityData.TradeStock> left, List<CreatureCommunityData.TradeStock> right) {
        return left.size() == right.size() && java.util.stream.IntStream.range(0, left.size()).allMatch(index ->
                left.get(index).count() == right.get(index).count() && ItemStack.matches(left.get(index).stack(), right.get(index).stack()));
    }

    private static void layout(MinecraftServer server, String name, int index) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos base = level.getSharedSpawnPos().offset(64 + index * 96, 100, 64);
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        mob.setPos(base.getX() + .5, base.getY(), base.getZ() + .5);
        Blueprint blueprint = CreatureSettlementBlueprints.INSTANCE.byId(id(name)).orElseThrow();
        var old = new LinkedHashMap<BlockPos, BlockState>();
        try {
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-6, -1, -6), base.offset(6, 3, 6))) {
                old.put(pos.immutable(), level.getBlockState(pos));
                level.setBlock(pos, (pos.getY() < base.getY() ? Blocks.STONE : Blocks.AIR).defaultBlockState(), 2);
            }
            method("place", ServerLevel.class, BlockPos.class, Blueprint.class).invoke(null, level, base, blueprint);
            BlockPos cache = base.offset(blueprint.cacheOffset());
            var entity = level.getBlockEntity(cache);
            check(entity instanceof Container, name + ": actual blueprint creates storage");
            Container container = (Container) entity;
            entity.getPersistentData().putString(BLUEPRINT, blueprint.id().toString());
            CreatureCommunityData.markCache(mob, cache);
            Method prepare = method("prepareCache", ServerLevel.class, ChangedEntity.class, BlockPos.class, Container.class);
            prepare.invoke(null, level, mob, cache, container);
            int initial = count(container);
            var credit = CreatureCommunityData.tradeStock(mob);
            check(initial > 0 && credit.stream().mapToInt(CreatureCommunityData.TradeStock::count).sum() == initial,
                    name + ": initial stock has exactly matching trade credit");
            CompoundTag saved = entity.saveWithFullMetadata(); entity.load(saved);
            level.getChunk(cache).setUnsaved(false);
            prepare.invoke(null, level, mob, cache, container);
            check(!level.getChunk(cache).isUnsaved(), name + ": an unchanged storage visit does not dirty the chunk");
            check(count(container) == initial && sameStock(credit, CreatureCommunityData.tradeStock(mob)),
                    name + ": loading and preparing do not refill or duplicate stock");
            check(blueprint.id().toString().equals(entity.getPersistentData().getString(BLUEPRINT)),
                    name + ": stored layout remains authoritative for visitors");
            if (name.contains("ruin") || name.equals("latex_bee_hive_cache") || name.equals("dark_cache")) {
                check(level.getBlockState(cache.above()).is(ChangedBlocks.DROPPED_ORANGE.get()),
                        name + ": oranges are a physical pile");
            }
            check(level.getEntitiesOfClass(ItemFrame.class, new AABB(cache).inflate(7),
                            frame -> frame.getItem().is( net.ltxprogrammer.changed.init.ChangedItems.ORANGE.get())).isEmpty(),
                    name + ": no orange is trapped in a display frame");
            container.clearContent(); container.setChanged();
            prepare.invoke(null, level, mob, cache, container);
            check(container.isEmpty(), name + ": depleted storage stays depleted");
            method("setCargo", ChangedEntity.class, ItemStack.class, CreatureSettlementService.ResourceKind.class)
                    .invoke(null, mob, new ItemStack(Items.WHEAT, 7), CreatureSettlementService.ResourceKind.FOOD);
            check(CreatureSettlementService.depositCargo(mob) && count(container) == 7 && !CreatureSettlementService.hasCargo(mob),
                    name + ": delivery moves the actual cargo into storage once");
            for (int slot = 0; slot < container.getContainerSize(); slot++) container.setItem(slot, new ItemStack(Items.STONE, 64));
            method("setCargo", ChangedEntity.class, ItemStack.class, CreatureSettlementService.ResourceKind.class)
                    .invoke(null, mob, new ItemStack(Items.WHEAT, 7), CreatureSettlementService.ResourceKind.FOOD);
            check(!CreatureSettlementService.depositCargo(mob) && CreatureSettlementService.cargo(mob).getCount() == 7,
                    name + ": full storage retains undelivered cargo without loss");
            check(CreatureCommunityData.snapshot(mob).orElseThrow().cache().orElseThrow().equals(cache),
                    name + ": failed delivery retains the outpost binding");
        } finally {
            level.getEntitiesOfClass(ItemFrame.class, new AABB(base).inflate(8), frame ->
                    frame.getPersistentData().getBoolean("ChangedSynergyCacheDecoration")).forEach(ItemFrame::discard);
            level.getEntitiesOfClass(MinecartChest.class, new AABB(base).inflate(8), cart ->
                    cart.getPersistentData().getBoolean("ChangedSynergyDecorativeMinecart")).forEach(MinecartChest::discard);
            old.forEach((pos, state) -> level.setBlock(pos, state, 2));
            CreatureCommunityData.detach(mob);
        }
    }

    private static void borderMigration(MinecraftServer server) throws Exception {
        ServerLevel level = server.overworld();
        int coordinate = 4000000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(10000) * 16;
        BlockPos cache = new BlockPos(coordinate, 200, coordinate);
        level.getChunk(cache);
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        mob.setPos(cache.getX() + .5, cache.getY(), cache.getZ() + .5);
        level.setBlock(cache, Blocks.BARREL.defaultBlockState(), 18);
        var entity = level.getBlockEntity(cache);
        entity.getPersistentData().putString(BLUEPRINT, id("dark_cache").toString());
        CreatureCommunityData.markCache(mob, cache);
        Method prepare = method("prepareCache", ServerLevel.class, ChangedEntity.class, BlockPos.class, Container.class);
        System.out.println("WILD_BORDER_SAMPLE: beforePrepare westLoaded=" + level.hasChunkAt(cache.west())
                + " decorated=" + entity.getPersistentData().getInt(DECORATED));
        prepare.invoke(null, level, mob, cache, (Container) entity);
        System.out.println("WILD_BORDER_SAMPLE: afterPrepare westLoaded=" + level.hasChunkAt(cache.west())
                + " decorated=" + entity.getPersistentData().getInt(DECORATED));
        check(!level.hasChunkAt(cache.west()) && entity.getPersistentData().getInt(DECORATED) == 0,
                "Old edge-of-chunk cache defers decoration without loading its neighbors");
        int initial = count((Container) entity);
        check(initial > 0, "Deferred decoration leaves real storage available");
        for (int x = -1; x <= 0; x++) for (int z = -1; z <= 0; z++) level.getChunk(cache.offset(x * 16, 0, z * 16));
        prepare.invoke(null, level, mob, cache, (Container) entity);
        check(entity.getPersistentData().getInt(DECORATED) == 11 && count((Container) entity) == initial,
                "Decoration completes after neighboring chunks load without reseeding stock");
        level.removeBlock(cache, false); CreatureCommunityData.detach(mob);
    }

    @SuppressWarnings("unchecked")
    private static void structurePlacement(MinecraftServer server, boolean hive) throws Exception {
        ServerLevel level = server.overworld();
        BlockPos center = level.getSharedSpawnPos().offset(hive ? 1800 : 1600, 110,
                160 + java.util.concurrent.ThreadLocalRandom.current().nextInt(1000) * 32);
        var bounds = new BoundingBox(center.getX() - 8, center.getY() - 1, center.getZ() - 8,
                center.getX() + 8, center.getY() + 6, center.getZ() + 8);
        for (BlockPos floor : BlockPos.betweenClosed(center.offset(-20, -1, -20), center.offset(20, -1, 20)))
            level.setBlock(floor, Blocks.STONE.defaultBlockState(), 2);
        ChangedEntity mob = ChangedEntities.DARK_LATEX_WOLF_MALE.get().create(level);
        mob.setPos(center.getX() + .5, center.getY(), center.getZ() + .5);
        Class<?> outpostType = Class.forName(CreatureSettlementService.class.getName() + "$StructureOutpost");
        var outpostConstructor = outpostType.getDeclaredConstructors()[0]; outpostConstructor.setAccessible(true);
        Object outpost = outpostConstructor.newInstance(center, bounds, new ResourceLocation("changed", hive ? "beehive1" : "office_ruin1"),
                id(hive ? "latex_bee_hive_cache" : "dark_ruin_cache"), hive);
        Class<?> budgetType = Class.forName(CreatureSettlementService.class.getName() + "$PlacementSearch");
        var budgetConstructor = budgetType.getDeclaredConstructor(ChangedEntity.class); budgetConstructor.setAccessible(true);
        Method establish = method("establishStructureOutpost", ServerLevel.class, ChangedEntity.class, outpostType, budgetType);
        Optional<BlockPos> selected = Optional.empty();
        for (int i = 0; i < 20 && selected.isEmpty(); i++)
            selected = (Optional<BlockPos>) establish.invoke(null, level, mob, outpost, budgetConstructor.newInstance(mob));
        BlockPos cache = selected.orElseThrow(() -> new AssertionError("No synthetic structure placement: hive=" + hive));
        check(hive != bounds.isInside(cache), hive ? "Hive layout is placed outside the hive" : "Ruin layout uses its available interior");
        check(level.getBlockEntity(cache).getPersistentData().getLong("ChangedSynergyCacheStructureAnchor") == center.asLong(),
                "Actual structure establishment preserves its owning structure anchor");
        check(((Optional<BlockPos>) establish.invoke(null, level, mob, outpost, budgetConstructor.newInstance(mob))).orElseThrow().equals(cache),
                "Revisiting the same structure reuses its cache instead of building another");
        CreatureCommunityData.detach(mob);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++; System.out.println("WILD_LIFECYCLE_PASS: " + message);
    }
}
