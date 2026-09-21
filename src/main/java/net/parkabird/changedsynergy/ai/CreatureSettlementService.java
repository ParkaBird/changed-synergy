package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.block.DroppedOrange;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.ai.navigation.WaterBoundPathNavigation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CaveVines;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.Tags;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.ChangedVanillaCompat;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;
import net.parkabird.changedsynergy.event.TerritoryContextEvents;
import net.parkabird.changedsynergy.event.TerritoryContextEvents.FacilitySnapshot;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints.Blueprint;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints.Cell;
import net.parkabird.changedsynergy.world.CreatureSettlementBlueprints.Display;
import net.parkabird.changedsynergy.world.OrangeLeafRegrowthData;

/** Real item carrying and datapack-driven community cache placement. */
public final class CreatureSettlementService {
    private static final String CARGO = "ChangedSynergyCarriedResource";
    private static final String CARGO_STACK = "Stack";
    private static final String CARGO_KIND = "Kind";
    private static final String TRADE_RETURNS =
            "ChangedSynergyTradeReturns";
    private static final String LAST_PROVISION_SOURCE =
            "ChangedSynergyLastProvisionSource";
    private static final String LAST_PROVISION_ITEM =
            "ChangedSynergyLastProvisionItem";
    private static final String CACHE_INITIALIZED =
            "ChangedSynergyCacheInitialized";
    private static final String CACHE_TRADE_STOCK_VERSION =
            "ChangedSynergyTradeStockVersion";
    private static final int TRADE_STOCK_VERSION = 1;
    private static final String CACHE_DECORATED =
            "ChangedSynergyCacheDecorated";
    private static final String CACHE_DECORATION_ENTITY =
            "ChangedSynergyCacheDecoration";
    private static final String CACHE_DECORATIVE_MINECART =
            "ChangedSynergyDecorativeMinecart";

    /** Migrates decoration entities created by older builds that made their item frames fixed. */
    public static void normalizeDecorationEntity(ItemFrame frame) {
        if (!frame.getPersistentData().getBoolean(CACHE_DECORATION_ENTITY)) return;
        CompoundTag tag = new CompoundTag();
        frame.saveWithoutId(tag);
        tag.remove("Fixed");
        tag.putBoolean("Invulnerable", false);
        frame.load(tag);
        frame.setInvulnerable(false);
    }
    private static final String CACHE_BLUEPRINT =
            "ChangedSynergyCacheBlueprint";
    private static final String CACHE_STRUCTURE_ANCHOR =
            "ChangedSynergyCacheStructureAnchor";
    private static final String CACHE_STRUCTURE_ID =
            "ChangedSynergyCacheStructureId";
    private static final String NEXT_STRUCTURE_OUTPOST_SEARCH =
            "ChangedSynergyNextStructureOutpostSearch";
    private static final String CACHE_HAS_ORANGE_PILE =
            "ChangedSynergyCacheHasOrangePile";
    private static final String FACILITY_WORK_CODE =
            "ChangedSynergyFacilityWorkCode";
    private static final String FACILITY_WORK_SECTION =
            "ChangedSynergyFacilityWorkSection";
    private static final String FACILITY_ORANGE_ROOM =
            "ChangedSynergyFacilityOrangeRoom";
    private static final String FACILITY_STORAGE_POS =
            "ChangedSynergyFacilityStoragePos";
    private static final String HUNT_CLAIMED_BY =
            "ChangedSynergyHuntClaimedBy";
    private static final String HUNT_CLAIMED_UNTIL =
            "ChangedSynergyHuntClaimedUntil";
    private static final String MINECART_CLAIMED_BY =
            "ChangedSynergyMinecartClaimedBy";
    private static final String MINECART_CLAIMED_UNTIL =
            "ChangedSynergyMinecartClaimedUntil";
    private static final String NEXT_HUNT =
            "ChangedSynergyNextHunt";
    private static final String RESOURCE_CLAIMED_BY =
            "ChangedSynergyResourceClaimedBy";
    private static final String RESOURCE_CLAIMED_UNTIL =
            "ChangedSynergyResourceClaimedUntil";
    private static final String FISH_CLAIMED_BY =
            "ChangedSynergyFishClaimedBy";
    private static final String FISH_CLAIMED_UNTIL =
            "ChangedSynergyFishClaimedUntil";
    private static final int CACHE_DECORATION_VERSION = 11;
    private static final List<BlockPos> LEGACY_CACHE_OFFSETS = List.of(
            new BlockPos(-1, 0, 0), new BlockPos(1, 0, 0),
            new BlockPos(0, 0, -1), new BlockPos(0, 0, 1),
            new BlockPos(-2, 0, 0), new BlockPos(2, 0, 0),
            new BlockPos(0, 0, -2), new BlockPos(0, 0, 2),
            new BlockPos(-2, 0, -1), new BlockPos(-2, 0, 1),
            new BlockPos(1, 0, 1), new BlockPos(1, 0, 2),
            new BlockPos(2, 0, -1),
            new BlockPos(-2, 0, 2), new BlockPos(2, 0, -2),
            new BlockPos(2, 0, 2));
    private static final int RESOURCE_MIN_AGE = 60;
    private static final int TERRITORY_CACHE_SEARCH_RADIUS = 10;
    private static final int NEARBY_CACHE_SEARCH_RADIUS = 16;
    private static final int CACHE_VERTICAL_SEARCH = 4;
    private static final int AQUATIC_SHORE_DETECTION_RADIUS = 56;
    private static final int AQUATIC_SHORE_PLACEMENT_RADIUS = 64;
    private static final int AQUATIC_NEARBY_SHORE_RADIUS = 80;
    private static final int OFFSHORE_STRUCTURE_RADIUS_CHUNKS = 8;
    private static final int OFFSHORE_CACHE_SEARCH_RADIUS = 18;
    private static final int OFFSHORE_LEGACY_BIND_RADIUS = 32;
    private static final int FACILITY_STORAGE_CHUNK_RADIUS = 6;
    private static final int FACILITY_PATH_CANDIDATE_LIMIT = 64;
    private static final double LAND_SHARED_CACHE_RADIUS = 72.0D;
    private static final double AQUATIC_SHORE_SHARED_CACHE_RADIUS = 80.0D;
    private static final int HUNT_SEARCH_RADIUS = 42;
    private static final int HUNT_POPULATION_RADIUS = 40;
    private static final int HUNT_MINIMUM_LOCAL_ADULTS = 2;
    private static final int BADLANDS_MINECART_SEARCH_RADIUS = 160;
    private static final int BADLANDS_MINECART_VERTICAL_RADIUS = 48;
    private static final int BADLANDS_MINECART_PATH_CANDIDATES = 20;
    private static final long HUNT_COOLDOWN_MIN = 5L * 60L * 20L;
    private static final int HUNT_COOLDOWN_VARIATION = 3 * 60 * 20;
    private static final long STRUCTURE_SEARCH_RETRY_TICKS = 60L * 20L;
    private static final TagKey<Item> BUILDING_MATERIALS = TagKey.create(
            Registries.ITEM,
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "creature_building_materials"));
    private static final ResourceLocation ORANGE_LEAVES =
            ResourceLocation.fromNamespaceAndPath("changed", "orange_tree_leaves");
    private static final ResourceLocation CARDBOARD_BOX =
            ResourceLocation.fromNamespaceAndPath("changed", "cardboard_box");
    private static final ResourceLocation CARDBOARD_BOX_SMALL =
            ResourceLocation.fromNamespaceAndPath(
                    "changed", "cardboard_box_small");
    private static final ResourceLocation VANILLA_CHEST =
            ResourceLocation.fromNamespaceAndPath("minecraft", "chest");
    private static final ResourceLocation AQUATIC_SHORE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "aquatic_cache");
    private static final ResourceLocation AQUATIC_OFFSHORE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "aquatic_offshore_cache");
    private static final ResourceLocation LIGHT_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "light_cache");
    private static final ResourceLocation DARK_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "dark_cache");
    private static final ResourceLocation CAVE_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "cave_cache");
    private static final ResourceLocation TAIGA_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "taiga_cache");
    private static final ResourceLocation SNOWY_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "snowy_cache");
    private static final ResourceLocation DESERT_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "desert_cache");
    private static final ResourceLocation BADLANDS_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "badlands_cache");
    private static final ResourceLocation RUIN_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "ruin_cache");
    private static final ResourceLocation DARK_RUIN_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "dark_ruin_cache");
    private static final ResourceLocation WHITE_RUIN_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "white_ruin_cache");
    private static final ResourceLocation AQUATIC_RUIN_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "aquatic_ruin_cache");
    private static final ResourceLocation BEE_HIVE_CACHE_BLUEPRINT =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "latex_bee_hive_cache");
    private static final TagKey<Biome> HAS_ORANGE_TREE = TagKey.create(
            Registries.BIOME,
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "has_orange_tree"));
    private static final TagKey<Structure> AQUATIC_CACHE_ANCHORS = TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "aquatic_cache_anchors"));
    private static final TagKey<Structure> CLAIMABLE_CHANGED_RUINS = TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "claimable_changed_ruins"));
    private static final TagKey<Structure> LATEX_BEE_HIVES = TagKey.create(
            Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "latex_bee_hives"));
    private static final ResourceLocation LATEX_BEE =
            ResourceLocation.fromNamespaceAndPath("changed", "latex_bee");

    public record FishingSite(
            BlockPos stand,
            BlockPos water,
            boolean iceCovered) {
        public FishingSite(BlockPos stand, BlockPos water) {
            this(stand, water, false);
        }
    }

    public record GlowBerrySite(BlockPos berries, BlockPos stand) {
    }

    /** A real supply cart plus a nearby block the provisioner can reach. */
    public record MinecartSupplyTarget(
            MinecartChest cart,
            BlockPos stand) {
    }

    private record FacilityWorkArea(
            FacilitySnapshot facility,
            String section,
            @Nullable FacilitySnapshot orangeRoom) {
    }

    private record StructureOutpost(
            BlockPos anchor,
            BoundingBox bounds,
            ResourceLocation structureId,
            ResourceLocation blueprintId,
            boolean outside) {
    }

    private enum FacilityStorageKind {
        ORANGE_ROOM_CHEST,
        ORANGE_ROOM_CARDBOARD,
        CARDBOARD,
        NONE
    }

    public enum AquaticProvisionMode {
        NEARSHORE("nearshore"),
        OPEN_OCEAN("open_ocean");

        private final String id;

        AquaticProvisionMode(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public String translationKey() {
            return "provision.changed_synergy.aquatic." + id;
        }
    }

    public enum ProvisionSource {
        ORANGE("orange"),
        SWEET_BERRIES("sweet_berries"),
        GLOW_BERRIES("glow_berries"),
        MINERAL("mineral"),
        NEARSHORE_FISH("nearshore_fish"),
        OPEN_OCEAN_FISH("open_ocean_fish"),
        BADLANDS_MINECART("badlands_minecart"),
        FORAGED_FOOD("foraged_food"),
        MATERIAL("material");

        private final String id;

        ProvisionSource(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static ProvisionSource fromId(String id) {
            for (ProvisionSource source : values()) {
                if (source.id.equals(id)) {
                    return source;
                }
            }
            return null;
        }
    }

    public enum ResourceKind {
        FOOD("food"),
        MATERIAL("material");

        private final String id;

        ResourceKind(String id) {
            this.id = id;
        }

        private static ResourceKind fromId(String id) {
            return MATERIAL.id.equals(id) ? MATERIAL : FOOD;
        }
    }

    private CreatureSettlementService() {
    }

    public static boolean enabled(ChangedEntity creature) {
        return ChangedSynergyGameRules.enabled(
                creature.level(), ChangedSynergyGameRules.CREATURE_LIFE);
    }

    public static boolean hasCargo(ChangedEntity creature) {
        return !cargo(creature).isEmpty();
    }

    public static ProvisionSource recentProvisionSource(
            ChangedEntity creature) {
        ProvisionSource remembered = ProvisionSource.fromId(
                creature.getPersistentData().getString(LAST_PROVISION_SOURCE));
        if (remembered != null) {
            return remembered;
        }
        if (isCaveCommunity(creature)) {
            return ProvisionSource.MINERAL;
        }
        if (isTaigaCommunity(creature)) {
            return ProvisionSource.SWEET_BERRIES;
        }
        if (isBadlandsCommunity(creature)) {
            return ProvisionSource.BADLANDS_MINECART;
        }
        if (usesClimateHunting(creature)) {
            return ProvisionSource.FORAGED_FOOD;
        }
        if (usesOpenOceanHarvest(creature)) {
            return ProvisionSource.OPEN_OCEAN_FISH;
        }
        if (canFish(creature)) {
            return ProvisionSource.NEARSHORE_FISH;
        }
        return ProvisionSource.ORANGE;
    }

    public static Optional<Item> recentProvisionItem(
            ChangedEntity creature) {
        ResourceLocation id = ResourceLocation.tryParse(
                creature.getPersistentData().getString(LAST_PROVISION_ITEM));
        return id == null ? Optional.empty()
                : Optional.ofNullable(ForgeRegistries.ITEMS.getValue(id));
    }

    public static void tickCargoIndicator(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || creature.tickCount % 12 != Math.floorMod(creature.getId(), 12)) {
            return;
        }
        ItemStack carried = cargo(creature);
        if (carried.isEmpty()) {
            return;
        }
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, carried),
                creature.getX(),
                creature.getY(0.72D),
                creature.getZ(),
                1,
                0.16D,
                0.12D,
                0.16D,
                0.01D);
    }

    public static void dropCargo(ChangedEntity creature) {
        ItemStack carried = cargo(creature);
        if (carried.isEmpty()) {
            return;
        }
        clearCargo(creature);
        creature.spawnAtLocation(carried);
    }

    public static ItemStack cargo(ChangedEntity creature) {
        CompoundTag persistent = creature.getPersistentData();
        if (!persistent.contains(CARGO, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        CompoundTag cargo = persistent.getCompound(CARGO);
        if (!cargo.contains(CARGO_STACK, Tag.TAG_COMPOUND)) {
            return ItemStack.EMPTY;
        }
        return ItemStack.of(cargo.getCompound(CARGO_STACK));
    }

    public static ResourceKind cargoKind(ChangedEntity creature) {
        CompoundTag cargo = creature.getPersistentData().getCompound(CARGO);
        return ResourceKind.fromId(cargo.getString(CARGO_KIND));
    }

    /** True only when one real orange can be moved into a placed pile. */
    public static boolean hasOrangeSupply(ChangedEntity creature) {
        if (RelationshipFavorService.isOrdinaryOrange(cargo(creature))) {
            return true;
        }
        if (creature.level() instanceof ServerLevel level) {
            Container container = cachePosition(creature)
                    .map(position -> containerAt(level, position))
                    .orElse(null);
            if (container != null) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (RelationshipFavorService.isOrdinaryOrange(
                            container.getItem(slot))) {
                        return true;
                    }
                }
            }
        }
        return HunterFaction.of(creature) == HunterFaction.WHITE
                && !isFacilityCommunity(creature)
                && CreatureCommunityData.hasStoredOrange(creature);
    }

    /** Removes exactly one orange from cargo, a cache, or white consensus stock. */
    public static boolean consumeOrangeSupply(ChangedEntity creature) {
        ItemStack carried = cargo(creature);
        if (RelationshipFavorService.isOrdinaryOrange(carried)) {
            carried.shrink(1);
            if (carried.isEmpty()) {
                clearCargo(creature);
            } else {
                setCargo(creature, carried, cargoKind(creature),
                        recentProvisionSource(creature));
            }
            return true;
        }
        if (creature.level() instanceof ServerLevel level) {
            Container container = cachePosition(creature)
                    .map(position -> containerAt(level, position))
                    .orElse(null);
            if (container != null) {
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    if (RelationshipFavorService.isOrdinaryOrange(
                            container.getItem(slot))) {
                        shrinkOne(container, slot);
                        return true;
                    }
                }
            }
        }
        return HunterFaction.of(creature) == HunterFaction.WHITE
                && !isFacilityCommunity(creature)
                && CreatureCommunityData.consumeStoredOrange(creature);
    }

    public static Optional<ItemEntity> findResource(
            ChangedEntity creature,
            double radius) {
        if (!(creature.level() instanceof ServerLevel level) || !enabled(creature)) {
            return Optional.empty();
        }
        return level.getEntitiesOfClass(
                        ItemEntity.class,
                        creature.getBoundingBox().inflate(radius, 5.0D, radius),
                        item -> item.isAlive()
                                && !item.getItem().isEmpty()
                                && item.getOwner() == null
                                && item.tickCount >= RESOURCE_MIN_AGE
                                && classify(creature, item.getItem()) != null
                                && claimAvailable(
                                        item.getPersistentData(), creature,
                                        level.getGameTime(),
                                        RESOURCE_CLAIMED_BY,
                                        RESOURCE_CLAIMED_UNTIL))
                .stream()
                .min(Comparator.comparingDouble(creature::distanceToSqr))
                .map(item -> {
                    claim(item.getPersistentData(), creature,
                            level.getGameTime() + 800L,
                            RESOURCE_CLAIMED_BY,
                            RESOURCE_CLAIMED_UNTIL);
                    return item;
                });
    }

    public static void releaseResourceClaim(
            ChangedEntity creature,
            @Nullable ItemEntity item) {
        if (item != null) {
            releaseClaim(item.getPersistentData(), creature,
                    RESOURCE_CLAIMED_BY, RESOURCE_CLAIMED_UNTIL);
        }
    }

    public static boolean isFacilityCommunity(ChangedEntity creature) {
        return facilityFor(creature) != null;
    }

    /** Maintenance has no harvestable facility supply room. */
    public static boolean isMaintenanceFacilityCommunity(
            ChangedEntity creature) {
        FacilityWorkArea area = facilityWorkArea(creature);
        return area != null && "maintenance".equals(area.section());
    }

    /** Whether this facility section has a reachable, real orange work room. */
    public static boolean hasFacilityProvisionWork(ChangedEntity creature) {
        FacilityWorkArea area = facilityWorkArea(creature);
        return area != null
                && !"maintenance".equals(area.section())
                && area.orangeRoom() != null;
    }

    public static boolean isCaveCommunity(ChangedEntity creature) {
        // Facility rooms are frequently underground, but they are a distinct
        // supply environment. Treating them as caves made their provisioners
        // mine ore and glow berries instead of tending the facility's orange
        // trees.
        return !isFacilityCommunity(creature)
                && HunterFaction.of(creature) == HunterFaction.LIGHT
                && "cave".equals(LightFactionGroup.of(creature));
    }

    public static boolean isTaigaCommunity(ChangedEntity creature) {
        return isLightRegion(creature, "taiga");
    }

    public static boolean isSnowyCommunity(ChangedEntity creature) {
        return isLightRegion(creature, "snowy");
    }

    public static boolean isDesertCommunity(ChangedEntity creature) {
        return isLightRegion(creature, "desert");
    }

    public static boolean isBadlandsCommunity(ChangedEntity creature) {
        return isLightRegion(creature, "badlands");
    }

    public static boolean usesClimateHunting(ChangedEntity creature) {
        return isSnowyCommunity(creature)
                || isDesertCommunity(creature)
                || isBadlandsCommunity(creature);
    }

    private static boolean isLightRegion(
            ChangedEntity creature,
            String region) {
        return !isFacilityCommunity(creature)
                && HunterFaction.of(creature) == HunterFaction.LIGHT
                && region.equals(LightFactionGroup.of(creature));
    }

    @Nullable
    private static FacilitySnapshot facilityFor(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return null;
        }
        FacilitySnapshot current = TerritoryContextEvents.facilityAt(
                level, creature.blockPosition());
        if (current != null) {
            return current;
        }
        BlockPos center = CreatureCommunityData.snapshot(creature)
                .map(CreatureCommunityData.Snapshot::center)
                .orElse(null);
        return center == null || center.equals(creature.blockPosition())
                ? null : TerritoryContextEvents.facilityAt(level, center);
    }

    @Nullable
    private static FacilityWorkArea facilityWorkArea(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return null;
        }
        FacilitySnapshot exact = TerritoryContextEvents.facilityAt(
                level, creature.blockPosition());
        FacilitySnapshot facility = exact != null ? exact : facilityFor(creature);
        if (facility == null) {
            return null;
        }

        CompoundTag memory = creature.getPersistentData();
        if (!facility.facilityCode().equals(
                memory.getString(FACILITY_WORK_CODE))) {
            memory.putString(FACILITY_WORK_CODE, facility.facilityCode());
            memory.remove(FACILITY_WORK_SECTION);
            memory.remove(FACILITY_ORANGE_ROOM);
            memory.remove(FACILITY_STORAGE_POS);
        }

        FacilitySnapshot centerRoom = CreatureCommunityData.snapshot(creature)
                .map(CreatureCommunityData.Snapshot::center)
                .map(position -> TerritoryContextEvents.facilityAt(level, position))
                .orElse(null);
        String section = memory.getString(FACILITY_WORK_SECTION);
        if (section.isBlank()) {
            section = facilitySection(exact);
            if (section.isBlank()) {
                section = facilitySection(centerRoom);
            }
            if (section.isBlank()) {
                BlockPos origin = creature.blockPosition();
                double bestDistance = Double.MAX_VALUE;
                for (var piece
                        : facility.facility().getPieceGenerationInfos()) {
                    FacilitySnapshot candidate = new FacilitySnapshot(
                            facility.facility(), piece,
                            facility.facilityCode());
                    String candidateSection = facilitySection(candidate);
                    if (candidateSection.isBlank()) {
                        continue;
                    }
                    double distance = piece.region().getCenter()
                            .distSqr(origin);
                    if (distance < bestDistance) {
                        section = candidateSection;
                        bestDistance = distance;
                    }
                }
            }
            if (!section.isBlank()) {
                memory.putString(FACILITY_WORK_SECTION, section);
            }
        }
        if (section.isBlank()) {
            return new FacilityWorkArea(facility, "", null);
        }

        FacilitySnapshot orangeRoom = null;
        if (memory.contains(FACILITY_ORANGE_ROOM, Tag.TAG_LONG)) {
            FacilitySnapshot remembered = TerritoryContextEvents.facilityAt(
                    level,
                    BlockPos.of(memory.getLong(FACILITY_ORANGE_ROOM)));
            if (isOrangeWorkRoom(remembered, facility, section)) {
                orangeRoom = remembered;
            } else {
                memory.remove(FACILITY_ORANGE_ROOM);
            }
        }
        if (orangeRoom == null && !"maintenance".equals(section)) {
            BlockPos origin = centerRoom != null
                    ? centerRoom.piece().region().getCenter()
                    : creature.blockPosition();
            double bestDistance = Double.MAX_VALUE;
            // A provider can spawn in any room in its coloured section. Scan
            // the facility layout rather than assuming it started in (or had
            // a community centre inside) the garden/tree room.
            for (var piece : facility.facility().getPieceGenerationInfos()) {
                FacilitySnapshot candidate = new FacilitySnapshot(
                        facility.facility(), piece, facility.facilityCode());
                if (!isOrangeWorkRoom(candidate, facility, section)) {
                    continue;
                }
                double distance = candidate.piece().region().getCenter()
                        .distSqr(origin);
                if (distance < bestDistance) {
                    orangeRoom = candidate;
                    bestDistance = distance;
                }
            }
            if (orangeRoom != null) {
                memory.putLong(FACILITY_ORANGE_ROOM,
                        orangeRoom.piece().region().getCenter().asLong());
            }
        }
        return new FacilityWorkArea(facility, section, orangeRoom);
    }

    public static boolean isInsideFacilityWorkSection(
            ChangedEntity creature) {
        return isInsideFacilityWorkSection(
                creature, creature.blockPosition(), true);
    }

    public static boolean isInsideFacilitySection(
            ChangedEntity creature,
            BlockPos position) {
        return isInsideFacilityWorkSection(creature, position, false);
    }

    private static boolean isInsideFacilityWorkSection(
            ChangedEntity creature,
            BlockPos position,
            boolean requireOrangeRoom) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return false;
        }
        FacilityWorkArea area = facilityWorkArea(creature);
        FacilitySnapshot exact = TerritoryContextEvents.facilityAt(
                level, position);
        return area != null
                && (!requireOrangeRoom || area.orangeRoom() != null)
                && sameFacilitySection(exact, area);
    }

    private static boolean isOrangeWorkRoom(
            @Nullable FacilitySnapshot candidate,
            FacilitySnapshot facility,
            String section) {
        return candidate != null
                && candidate.facilityCode().equals(facility.facilityCode())
                && section.equals(facilitySection(candidate))
                && isOrangeTemplate(candidate);
    }

    private static boolean isOrangeTemplate(
            @Nullable FacilitySnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        String path = snapshot.piece().pieceName().getPath()
                .toLowerCase(Locale.ROOT);
        return path.contains("garden") || hasFacilityMarker(path, "tree");
    }

    private static boolean isTreeTemplate(
            @Nullable FacilitySnapshot snapshot) {
        if (snapshot == null) {
            return false;
        }
        return hasFacilityMarker(
                snapshot.piece().pieceName().getPath()
                        .toLowerCase(Locale.ROOT),
                "tree");
    }

    private static String facilitySection(
            @Nullable FacilitySnapshot snapshot) {
        return TerritoryContextEvents.facilitySectionId(snapshot);
    }

    private static boolean hasFacilityMarker(
            String path,
            String marker) {
        int slash = path.lastIndexOf('/');
        String file = slash >= 0 ? path.substring(slash + 1) : path;
        return path.contains("/" + marker + "/")
                || file.startsWith(marker + "_")
                || file.contains("_" + marker + "_")
                || file.endsWith("_" + marker);
    }

    private static boolean sameFacilitySection(
            @Nullable FacilitySnapshot candidate,
            FacilityWorkArea area) {
        return candidate != null
                && !area.section().isBlank()
                && candidate.facilityCode().equals(
                        area.facility().facilityCode())
                && area.section().equals(facilitySection(candidate));
    }

    private static boolean facilityPathWithinSection(
            ChangedEntity creature,
            BlockPos target,
            FacilityWorkArea area,
            int accuracy) {
        return facilityPathWithinSection(
                creature, target, area, accuracy, false);
    }

    private static boolean facilityPathWithinSection(
            ChangedEntity creature,
            BlockPos target,
            FacilityWorkArea area,
            int accuracy,
            boolean returningCargo) {
        if (!(creature.level() instanceof ServerLevel level)
                || !sameFacilitySection(
                        TerritoryContextEvents.facilityAt(level, target), area)) {
            return false;
        }
        if (creature.distanceToSqr(Vec3.atCenterOf(target))
                <= accuracy * accuracy) {
            return true;
        }
        Path path = creature.getNavigation().createPath(target, accuracy);
        if (path == null || !path.canReach()) {
            return false;
        }
        for (int index = 0; index < path.getNodeCount(); index++) {
            FacilitySnapshot step = TerritoryContextEvents.facilityAt(
                    level, path.getNode(index).asBlockPos());
            if (returningCargo) {
                // Combat may push a loaded worker through a transition or even
                // just outside the facility. Permit a delivery route back
                // through those spaces, but never through another facility.
                if (step != null && !step.facilityCode().equals(
                        area.facility().facilityCode())) {
                    return false;
                }
            } else if (!sameFacilitySection(step, area)) {
                return false;
            }
        }
        return true;
    }

    public static boolean canFish(ChangedEntity creature) {
        HunterArchetype archetype = HunterArchetype.of(creature);
        return archetype == HunterArchetype.AQUATIC
                || archetype == HunterArchetype.FELINE
                || usesClimateHunting(creature);
    }

    /**
     * Selects one sustainable vanilla food animal for a cold or arid
     * provisioner.  Named, young, leashed, tamed and scarce animals are left
     * alone, and a short claim prevents several workers choosing one target.
     */
    public static Optional<Animal> findHuntPrey(
            ChangedEntity creature,
            double radius) {
        if (!(creature.level() instanceof ServerLevel level)
                || !usesClimateHunting(creature)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || creature.getPersistentData().getLong(NEXT_HUNT)
                        > level.getGameTime()) {
            return Optional.empty();
        }
        List<Animal> nearby = level.getEntitiesOfClass(
                Animal.class,
                creature.getBoundingBox().inflate(
                        Math.min(radius, HUNT_SEARCH_RADIUS),
                        8.0D,
                        Math.min(radius, HUNT_SEARCH_RADIUS)),
                prey -> isHuntablePrey(prey)
                        && !ChangedVanillaCompat
                                .protectsAnimalNearRespectedHuman(
                                        creature, prey));
        long now = level.getGameTime();
        return nearby.stream()
                .filter(prey -> claimAvailable(prey, creature, now))
                .filter(prey -> nearby.stream()
                        .filter(other -> other.getType() == prey.getType()
                                && other.distanceToSqr(prey)
                                        <= HUNT_POPULATION_RADIUS
                                                * HUNT_POPULATION_RADIUS)
                        .count() >= HUNT_MINIMUM_LOCAL_ADULTS)
                .min(Comparator.comparingDouble(creature::distanceToSqr))
                .map(prey -> {
                    CompoundTag data = prey.getPersistentData();
                    data.putUUID(HUNT_CLAIMED_BY, creature.getUUID());
                    data.putLong(HUNT_CLAIMED_UNTIL, now + 600L);
                    return prey;
                });
    }

    public static void releaseHuntClaim(
            ChangedEntity creature,
            @Nullable Animal prey) {
        if (prey == null) {
            return;
        }
        CompoundTag data = prey.getPersistentData();
        if (data.hasUUID(HUNT_CLAIMED_BY)
                && creature.getUUID().equals(data.getUUID(HUNT_CLAIMED_BY))) {
            data.remove(HUNT_CLAIMED_BY);
            data.remove(HUNT_CLAIMED_UNTIL);
        }
    }

    /** Converts one selected prey into real food cargo without invoking latex
     * combat or creating duplicate vanilla death drops. */
    public static boolean harvestPrey(
            ChangedEntity creature,
            Animal prey) {
        if (!(creature.level() instanceof ServerLevel level)
                || prey == null
                || !isHuntablePrey(prey)
                || ChangedVanillaCompat.protectsAnimalNearRespectedHuman(
                        creature, prey)
                || hasCargo(creature)
                || creature.distanceToSqr(prey) > 3.0D * 3.0D
                || !claimOwnedBy(prey, creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        Item food = foodFromPrey(prey);
        if (food == Items.AIR) {
            return false;
        }
        Vec3 position = prey.position();
        ItemStack carried = new ItemStack(food, prey.getType() == EntityType.RABBIT
                || prey.getType() == EntityType.CHICKEN ? 1 : 2);
        releaseHuntClaim(creature, prey);
        prey.discard();
        setCargo(creature, carried, ResourceKind.FOOD,
                ProvisionSource.FORAGED_FOOD);
        creature.getPersistentData().putLong(
                NEXT_HUNT,
                level.getGameTime() + HUNT_COOLDOWN_MIN
                        + creature.getRandom().nextInt(HUNT_COOLDOWN_VARIATION + 1));
        level.playSound(null, BlockPos.containing(position),
                SoundEvents.GENERIC_EAT, SoundSource.NEUTRAL,
                0.45F, 0.7F + creature.getRandom().nextFloat() * 0.15F);
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, carried),
                position.x, position.y + 0.45D, position.z,
                8, 0.3D, 0.25D, 0.3D, 0.025D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    /**
     * Finds food in a reachable, surface-exposed abandoned-mineshaft chest
     * minecart. A short claim keeps nearby provisioners from crowding the same
     * cart while one of them walks over.
     */
    public static Optional<MinecartSupplyTarget> findBadlandsSupplyMinecart(
            ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || !isBadlandsCommunity(creature)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return Optional.empty();
        }
        long now = level.getGameTime();
        List<MinecartChest> candidates = level.getEntitiesOfClass(
                        MinecartChest.class,
                        creature.getBoundingBox().inflate(
                                BADLANDS_MINECART_SEARCH_RADIUS,
                                BADLANDS_MINECART_VERTICAL_RADIUS,
                                BADLANDS_MINECART_SEARCH_RADIUS),
                        cart -> cart.isAlive()
                                && !cart.isRemoved()
                                && !isDecorativeMinecart(cart)
                                && minecartClaimAvailable(cart, creature, now)
                                && edibleMinecartSlot(cart) >= 0
                                && isExposedMineshaftCart(level, cart))
                .stream()
                .sorted(Comparator.comparingDouble(creature::distanceToSqr))
                .limit(BADLANDS_MINECART_PATH_CANDIDATES)
                .toList();
        for (MinecartChest cart : candidates) {
            Optional<BlockPos> stand = findReachableMinecartStand(
                    level, creature, cart);
            if (stand.isEmpty()) {
                continue;
            }
            CompoundTag data = cart.getPersistentData();
            data.putUUID(MINECART_CLAIMED_BY, creature.getUUID());
            long travelTicks = 900L + (long)Math.ceil(
                    Math.sqrt(creature.distanceToSqr(cart)) * 12.0D);
            data.putLong(MINECART_CLAIMED_UNTIL,
                    now + Math.max(1200L, Math.min(3600L, travelTicks)));
            return Optional.of(new MinecartSupplyTarget(cart, stand.get()));
        }
        return Optional.empty();
    }

    /** Keeps a long-distance claim alive while its owner is still walking. */
    public static void refreshMinecartClaim(
            ChangedEntity creature,
            @Nullable MinecartChest cart) {
        if (cart == null || !minecartClaimOwnedBy(cart, creature)) {
            return;
        }
        cart.getPersistentData().putLong(
                MINECART_CLAIMED_UNTIL,
                creature.level().getGameTime() + 1200L);
    }

    public static void releaseMinecartClaim(
            ChangedEntity creature,
            @Nullable MinecartChest cart) {
        if (cart == null) {
            return;
        }
        CompoundTag data = cart.getPersistentData();
        if (data.hasUUID(MINECART_CLAIMED_BY)
                && creature.getUUID().equals(
                        data.getUUID(MINECART_CLAIMED_BY))) {
            data.remove(MINECART_CLAIMED_BY);
            data.remove(MINECART_CLAIMED_UNTIL);
        }
    }

    /** Moves food straight from the minecart into carried cargo. */
    public static boolean harvestBadlandsMinecartFood(
            ChangedEntity creature,
            MinecartChest cart) {
        if (!(creature.level() instanceof ServerLevel level)
                || cart == null
                || !cart.isAlive()
                || cart.isRemoved()
                || isDecorativeMinecart(cart)
                || hasCargo(creature)
                || creature.distanceToSqr(cart) > 3.4D * 3.4D
                || !minecartClaimOwnedBy(cart, creature)
                || !isExposedMineshaftCart(level, cart)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        int slot = edibleMinecartSlot(cart);
        if (slot < 0) {
            releaseMinecartClaim(creature, cart);
            return false;
        }
        ItemStack available = cart.getItem(slot);
        int amount = Math.min(
                available.getCount(), 1 + creature.getRandom().nextInt(2));
        ItemStack carried = cart.removeItem(slot, amount);
        cart.setChanged();
        releaseMinecartClaim(creature, cart);
        if (carried.isEmpty()) {
            return false;
        }
        setCargo(creature, carried, ResourceKind.FOOD,
                ProvisionSource.BADLANDS_MINECART);
        creature.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, cart.blockPosition(), SoundEvents.ITEM_PICKUP,
                SoundSource.NEUTRAL, 0.55F,
                0.82F + creature.getRandom().nextFloat() * 0.16F);
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, carried),
                cart.getX(), cart.getY() + 0.65D, cart.getZ(),
                7, 0.28D, 0.24D, 0.28D, 0.025D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    private static boolean minecartClaimAvailable(
            MinecartChest cart,
            ChangedEntity creature,
            long now) {
        CompoundTag data = cart.getPersistentData();
        return data.getLong(MINECART_CLAIMED_UNTIL) <= now
                || data.hasUUID(MINECART_CLAIMED_BY)
                        && creature.getUUID().equals(
                                data.getUUID(MINECART_CLAIMED_BY));
    }

    private static boolean minecartClaimOwnedBy(
            MinecartChest cart,
            ChangedEntity creature) {
        CompoundTag data = cart.getPersistentData();
        return data.hasUUID(MINECART_CLAIMED_BY)
                && creature.getUUID().equals(
                        data.getUUID(MINECART_CLAIMED_BY));
    }

    private static int edibleMinecartSlot(MinecartChest cart) {
        for (int slot = 0; slot < cart.getContainerSize(); slot++) {
            ItemStack stack = cart.getItem(slot);
            if (!stack.isEmpty() && stack.isEdible()) {
                return slot;
            }
        }
        return -1;
    }

    private static boolean isDecorativeMinecart(MinecartChest cart) {
        return cart.getPersistentData().getBoolean(
                CACHE_DECORATIVE_MINECART);
    }

    private static Optional<BlockPos> findReachableMinecartStand(
            ServerLevel level,
            ChangedEntity creature,
            MinecartChest cart) {
        BlockPos origin = cart.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (int radius = 1; radius <= 3; radius++) {
            for (int dy : new int[] {0, 1, -1, 2, -2}) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos stand = origin.offset(dx, dy, dz);
                        if (isSafeMinecartStand(level, stand)) {
                            candidates.add(stand.immutable());
                        }
                    }
                }
            }
        }
        return candidates.stream()
                .sorted(Comparator.comparingDouble(stand ->
                        stand.distSqr(origin)))
                .filter(stand -> cart.distanceToSqr(
                        Vec3.atBottomCenterOf(stand)) <= 3.2D * 3.2D)
                .filter(stand -> isReachableStand(creature, stand))
                .findFirst();
    }

    private static boolean isSafeMinecartStand(
            ServerLevel level,
            BlockPos stand) {
        if (!level.hasChunkAt(stand)
                || !level.getFluidState(stand).isEmpty()
                || !level.getFluidState(stand.above()).isEmpty()
                || !level.getBlockState(stand).getCollisionShape(
                        level, stand).isEmpty()
                || !level.getBlockState(stand.above()).getCollisionShape(
                        level, stand.above()).isEmpty()) {
            return false;
        }
        BlockPos floor = stand.below();
        return level.getBlockState(floor).isFaceSturdy(
                level, floor, Direction.UP);
    }

    private static boolean isExposedMineshaftCart(
            ServerLevel level,
            MinecartChest cart) {
        BlockPos position = cart.blockPosition();
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                position.getX(), position.getZ());
        if (!level.canSeeSky(position.above())
                && surface - position.getY() > 18) {
            return false;
        }

        boolean rail = false;
        boolean mineshaftMaterial = false;
        for (BlockPos nearby : BlockPos.betweenClosed(
                position.offset(-4, -3, -4),
                position.offset(4, 3, 4))) {
            BlockState state = level.getBlockState(nearby);
            rail |= state.is(BlockTags.RAILS);
            mineshaftMaterial |= state.is(Blocks.COBWEB)
                    || state.is(BlockTags.PLANKS)
                    || state.is(BlockTags.WOODEN_FENCES);
            if (rail && mineshaftMaterial) {
                return true;
            }
        }
        return false;
    }

    private static boolean isHuntablePrey(Animal prey) {
        return prey.isAlive()
                && !prey.isRemoved()
                && !prey.isBaby()
                && !prey.hasCustomName()
                && !prey.isLeashed()
                && !prey.isPassenger()
                && !prey.isInvulnerable()
                && (!(prey instanceof TamableAnimal tame) || !tame.isTame())
                && foodFromPrey(prey) != Items.AIR;
    }

    private static boolean claimAvailable(
            Animal prey,
            ChangedEntity creature,
            long now) {
        CompoundTag data = prey.getPersistentData();
        return data.getLong(HUNT_CLAIMED_UNTIL) <= now
                || data.hasUUID(HUNT_CLAIMED_BY)
                        && creature.getUUID().equals(
                                data.getUUID(HUNT_CLAIMED_BY));
    }

    private static boolean claimOwnedBy(
            Animal prey,
            ChangedEntity creature) {
        CompoundTag data = prey.getPersistentData();
        return data.hasUUID(HUNT_CLAIMED_BY)
                && creature.getUUID().equals(data.getUUID(HUNT_CLAIMED_BY));
    }

    private static boolean claimAvailable(
            CompoundTag data,
            ChangedEntity creature,
            long now,
            String ownerKey,
            String untilKey) {
        return data.getLong(untilKey) <= now
                || data.hasUUID(ownerKey)
                        && creature.getUUID().equals(data.getUUID(ownerKey));
    }

    private static void claim(
            CompoundTag data,
            ChangedEntity creature,
            long until,
            String ownerKey,
            String untilKey) {
        data.putUUID(ownerKey, creature.getUUID());
        data.putLong(untilKey, until);
    }

    private static void releaseClaim(
            CompoundTag data,
            ChangedEntity creature,
            String ownerKey,
            String untilKey) {
        if (data.hasUUID(ownerKey)
                && creature.getUUID().equals(data.getUUID(ownerKey))) {
            data.remove(ownerKey);
            data.remove(untilKey);
        }
    }

    private static Item foodFromPrey(Animal prey) {
        EntityType<?> type = prey.getType();
        if (type == EntityType.COW) {
            return Items.BEEF;
        }
        if (type == EntityType.SHEEP) {
            return Items.MUTTON;
        }
        if (type == EntityType.PIG) {
            return Items.PORKCHOP;
        }
        if (type == EntityType.RABBIT) {
            return Items.RABBIT;
        }
        if (type == EntityType.CHICKEN) {
            return Items.CHICKEN;
        }
        return Items.AIR;
    }

    /**
     * River, coast and near-shore populations use rods from land. A genuinely
     * pelagic population instead hunts fish and maintains a submerged salvage
     * cache beside a non-monument ocean structure.
     */
    public static AquaticProvisionMode aquaticProvisionMode(
            ChangedEntity creature) {
        if (HunterArchetype.of(creature) != HunterArchetype.AQUATIC
                || !(creature.level() instanceof ServerLevel level)) {
            return AquaticProvisionMode.NEARSHORE;
        }

        Optional<BlockPos> cache = CreatureCommunityData.snapshot(creature)
                .flatMap(CreatureCommunityData.Snapshot::cache);
        if (cache.isPresent()) {
            BlockEntity blockEntity = level.getBlockEntity(cache.get());
            if (blockEntity != null) {
                ResourceLocation blueprint = ResourceLocation.tryParse(
                        blockEntity.getPersistentData().getString(CACHE_BLUEPRINT));
                if (AQUATIC_OFFSHORE_BLUEPRINT.equals(blueprint)) {
                    return AquaticProvisionMode.OPEN_OCEAN;
                }
                if (AQUATIC_SHORE_BLUEPRINT.equals(blueprint)) {
                    return AquaticProvisionMode.NEARSHORE;
                }
            }
            return level.getFluidState(cache.get()).is(FluidTags.WATER)
                    ? AquaticProvisionMode.OPEN_OCEAN
                    : AquaticProvisionMode.NEARSHORE;
        }

        BlockPos center = CreatureCommunityData.snapshot(creature)
                .map(CreatureCommunityData.Snapshot::center)
                .orElse(creature.blockPosition());
        return findDryShore(level, center, AQUATIC_SHORE_DETECTION_RADIUS)
                .isPresent()
                ? AquaticProvisionMode.NEARSHORE
                : AquaticProvisionMode.OPEN_OCEAN;
    }

    public static boolean usesOpenOceanHarvest(ChangedEntity creature) {
        return HunterArchetype.of(creature) == HunterArchetype.AQUATIC
                && aquaticProvisionMode(creature)
                        == AquaticProvisionMode.OPEN_OCEAN;
    }

    public static Optional<AbstractFish> findSeaFish(
            ChangedEntity creature,
            double radius) {
        if (!(creature.level() instanceof ServerLevel level)
                || !usesOpenOceanHarvest(creature)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return Optional.empty();
        }
        return level.getEntitiesOfClass(
                        AbstractFish.class,
                        creature.getBoundingBox().inflate(
                                radius, Math.max(8.0D, radius * 0.5D), radius),
                        fish -> fish.isAlive()
                                && !fish.isRemoved()
                                && fish.isInWater()
                                && claimAvailable(
                                        fish.getPersistentData(), creature,
                                        level.getGameTime(),
                                        FISH_CLAIMED_BY,
                                        FISH_CLAIMED_UNTIL))
                .stream()
                .min(Comparator.comparingDouble(creature::distanceToSqr))
                .map(fish -> {
                    claim(fish.getPersistentData(), creature,
                            level.getGameTime() + 800L,
                            FISH_CLAIMED_BY,
                            FISH_CLAIMED_UNTIL);
                    return fish;
                });
    }

    public static void releaseSeaFishClaim(
            ChangedEntity creature,
            @Nullable AbstractFish fish) {
        if (fish != null) {
            releaseClaim(fish.getPersistentData(), creature,
                    FISH_CLAIMED_BY, FISH_CLAIMED_UNTIL);
        }
    }

    /** Captures a wild fish as real cargo without entering ordinary combat. */
    public static boolean captureSeaFish(
            ChangedEntity creature,
            AbstractFish fish) {
        if (!(creature.level() instanceof ServerLevel level)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || fish == null
                || !fish.isAlive()
                || fish.isRemoved()
                || creature.distanceToSqr(fish) > 3.0D * 3.0D) {
            return false;
        }
        Item item = fish.getType() == EntityType.SALMON
                ? Items.SALMON
                : fish.getType() == EntityType.PUFFERFISH
                        ? Items.PUFFERFISH
                        : fish.getType() == EntityType.TROPICAL_FISH
                                ? Items.TROPICAL_FISH
                                : Items.COD;
        ItemStack caught = new ItemStack(item);
        releaseSeaFishClaim(creature, fish);
        setCargo(creature, caught, ResourceKind.FOOD,
                ProvisionSource.OPEN_OCEAN_FISH);
        Vec3 position = fish.position();
        fish.discard();
        creature.swing(InteractionHand.MAIN_HAND);
        level.playSound(null, BlockPos.containing(position),
                SoundEvents.FISH_SWIM, SoundSource.NEUTRAL,
                0.75F, 0.85F + creature.getRandom().nextFloat() * 0.25F);
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, caught),
                position.x, position.y + 0.25D, position.z,
                8, 0.28D, 0.2D, 0.28D, 0.035D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    public static Optional<BlockPos> findOrangeLeaves(
            ChangedEntity creature,
            int horizontalRadius,
            int verticalRadius) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        Block orangeLeaves = ForgeRegistries.BLOCKS.getValue(ORANGE_LEAVES);
        if (orangeLeaves == null) {
            return Optional.empty();
        }

        FacilitySnapshot facility = facilityFor(creature);
        if (facility != null) {
            FacilityWorkArea area = facilityWorkArea(creature);
            if (area == null || area.orangeRoom() == null) {
                return Optional.empty();
            }
            var region = area.orangeRoom().piece().region();
            List<BlockPos> candidates = new ArrayList<>();
            for (BlockPos candidate : BlockPos.betweenClosed(
                    new BlockPos(region.minX(), region.minY(), region.minZ()),
                    new BlockPos(region.maxX(), region.maxY(), region.maxZ()))) {
                if (level.hasChunkAt(candidate)
                        && level.getBlockState(candidate).is(orangeLeaves)) {
                    candidates.add(candidate.immutable());
                }
            }
            candidates.sort(Comparator.comparingDouble(position ->
                    position.distSqr(creature.blockPosition())));
            int attempts = Math.min(
                    candidates.size(), FACILITY_PATH_CANDIDATE_LIMIT);
            for (int index = 0; index < attempts; index++) {
                BlockPos candidate = candidates.get(index);
                if (facilityPathWithinSection(
                        creature, candidate, area, 3)) {
                    return Optional.of(candidate);
                }
            }
            return Optional.empty();
        }
        if (isCaveCommunity(creature) || isTaigaCommunity(creature)) {
            return Optional.empty();
        }

        BlockPos origin = creature.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                origin.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            if (!level.hasChunkAt(candidate)
                    || !level.getBlockState(candidate).is(orangeLeaves)) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /** Harvests the fruiting leaf without deleting the tree canopy. */
    public static boolean harvestOrangeLeaves(
            ChangedEntity creature,
            BlockPos position) {
        if (!(creature.level() instanceof ServerLevel level)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        Block orangeLeaves = ForgeRegistries.BLOCKS.getValue(ORANGE_LEAVES);
        Item orange = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("changed", "orange"));
        if (orangeLeaves == null || orange == null
                || !level.getBlockState(position).is(orangeLeaves)) {
            return false;
        }
        BlockState fruitingLeaves = level.getBlockState(position);
        BlockState harvestedLeaves = localLeaves(level, position);
        if (!level.setBlock(position, harvestedLeaves, Block.UPDATE_ALL)) {
            return false;
        }
        OrangeLeafRegrowthData.schedule(
                level, position, fruitingLeaves, harvestedLeaves);
        setCargo(creature, new ItemStack(orange), ResourceKind.FOOD,
                ProvisionSource.ORANGE);
        level.sendParticles(ParticleTypes.COMPOSTER,
                position.getX() + 0.5D, position.getY() + 0.5D,
                position.getZ() + 0.5D, 7, 0.32D, 0.32D, 0.32D, 0.02D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    /**
     * Finds ripe cave vines together with a reachable floor position beneath
     * them. Cave provisioners can therefore gather berries that are hanging
     * above ordinary melee reach without trying to path into the ceiling.
     */
    public static Optional<GlowBerrySite> findGlowBerrySite(
            ChangedEntity creature,
            int horizontalRadius,
            int upwardRadius,
            int downwardRadius) {
        if (!(creature.level() instanceof ServerLevel level)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !isCaveCommunity(creature)) {
            return Optional.empty();
        }
        BlockPos origin = creature.blockPosition();
        List<BlockPos> candidates = new ArrayList<>();
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-horizontalRadius, -downwardRadius, -horizontalRadius),
                origin.offset(horizontalRadius, upwardRadius, horizontalRadius))) {
            if (level.hasChunkAt(candidate)
                    && hasRipeGlowBerries(level.getBlockState(candidate))) {
                candidates.add(candidate.immutable());
            }
        }
        candidates.sort(Comparator.comparingDouble(origin::distSqr));
        for (BlockPos berries : candidates.stream().limit(48).toList()) {
            BlockPos stand = findGlowBerryStand(level, creature, berries, 10);
            if (stand != null) {
                return Optional.of(new GlowBerrySite(berries, stand));
            }
        }
        return Optional.empty();
    }

    public static boolean harvestGlowBerries(
            ChangedEntity creature,
            BlockPos position) {
        if (!(creature.level() instanceof ServerLevel level)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        if (!hasRipeGlowBerries(state)) {
            return false;
        }
        level.setBlock(position, state.setValue(CaveVines.BERRIES, false),
                Block.UPDATE_ALL);
        ItemStack berries = new ItemStack(
                Items.GLOW_BERRIES, 1 + creature.getRandom().nextInt(2));
        setCargo(creature, berries, ResourceKind.FOOD,
                ProvisionSource.GLOW_BERRIES);
        level.playSound(null, position, SoundEvents.CAVE_VINES_PICK_BERRIES,
                SoundSource.BLOCKS, 0.75F,
                0.9F + creature.getRandom().nextFloat() * 0.2F);
        level.sendParticles(
                new ItemParticleOption(ParticleTypes.ITEM, berries),
                position.getX() + 0.5D,
                position.getY() + 0.5D,
                position.getZ() + 0.5D,
                7,
                0.24D,
                0.3D,
                0.24D,
                0.025D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    public static Optional<BlockPos> findSweetBerryBush(
            ChangedEntity creature,
            int horizontalRadius,
            int verticalRadius) {
        if (!(creature.level() instanceof ServerLevel level)
                || !isTaigaCommunity(creature)) {
            return Optional.empty();
        }
        BlockPos origin = creature.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                origin.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            BlockState state = level.getBlockState(candidate);
            if (!(state.getBlock() instanceof SweetBerryBushBlock)
                    || !state.hasProperty(SweetBerryBushBlock.AGE)
                    || state.getValue(SweetBerryBushBlock.AGE) < 2) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Harvests straight into the provisioner's cargo, like orange picking.
     * No item entity is spawned, so the result cannot be lost or stolen on
     * the ground before delivery.
     */
    public static boolean harvestSweetBerries(
            ChangedEntity creature,
            BlockPos position) {
        if (!(creature.level() instanceof ServerLevel level)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        if (!(state.getBlock() instanceof SweetBerryBushBlock)
                || !state.hasProperty(SweetBerryBushBlock.AGE)) {
            return false;
        }
        int age = state.getValue(SweetBerryBushBlock.AGE);
        if (age < 2) {
            return false;
        }
        int count = 1 + creature.getRandom().nextInt(2)
                + (age == 3 ? 1 : 0);
        ItemStack berries = new ItemStack(Items.SWEET_BERRIES, count);
        level.setBlock(
                position,
                state.setValue(SweetBerryBushBlock.AGE, 1),
                Block.UPDATE_ALL);
        setCargo(creature, berries, ResourceKind.FOOD,
                ProvisionSource.SWEET_BERRIES);
        level.playSound(null, position,
                SoundEvents.SWEET_BERRY_BUSH_PICK_BERRIES,
                SoundSource.BLOCKS, 0.8F,
                0.9F + creature.getRandom().nextFloat() * 0.2F);
        level.sendParticles(
                ParticleTypes.COMPOSTER,
                position.getX() + 0.5D,
                position.getY() + 0.55D,
                position.getZ() + 0.5D,
                7, 0.25D, 0.3D, 0.25D, 0.025D);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    public static Optional<FishingSite> findFishingSite(
            ChangedEntity creature,
            int radius,
            int verticalRadius) {
        return findFishingSite(creature, radius, verticalRadius, true);
    }

    /** Fishing-site search used by bonded creatures of any body type. */
    public static Optional<FishingSite> findCompanionFishingSite(
            ChangedEntity creature,
            int radius,
            int verticalRadius) {
        return findFishingSite(creature, radius, verticalRadius, false);
    }

    private static Optional<FishingSite> findFishingSite(
            ChangedEntity creature,
            int radius,
            int verticalRadius,
            boolean requireFishingArchetype) {
        if (!(creature.level() instanceof ServerLevel level)
                || requireFishingArchetype && !canFish(creature)) {
            return Optional.empty();
        }
        BlockPos origin = creature.blockPosition();
        boolean allowIceFishing = level.getGameRules()
                .getBoolean(GameRules.RULE_MOBGRIEFING);
        for (int r = 1; r <= radius; r++) {
            List<FishingSite> ring = new ArrayList<>();
            for (int x = -r; x <= r; x++) {
                for (int z = -r; z <= r; z++) {
                    if (Math.max(Math.abs(x), Math.abs(z)) != r) {
                        continue;
                    }
                    int worldX = origin.getX() + x;
                    int worldZ = origin.getZ() + z;
                    BlockPos column = new BlockPos(worldX, origin.getY(), worldZ);
                    if (!level.hasChunkAt(column)) {
                        continue;
                    }
                    int surfaceY = level.getHeight(
                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                            worldX, worldZ);
                    addFishingSitesAt(level,
                            new BlockPos(worldX, surfaceY, worldZ), ring,
                            allowIceFishing);
                    for (int step = 0; step <= verticalRadius * 2; step++) {
                        int magnitude = (step + 1) / 2;
                        int y = step == 0 ? 0
                                : step % 2 == 1 ? magnitude : -magnitude;
                        int candidateY = origin.getY() + y;
                        if (candidateY != surfaceY) {
                            addFishingSitesAt(level,
                                    new BlockPos(worldX, candidateY, worldZ), ring,
                                    allowIceFishing);
                        }
                    }
                }
            }
            ring.sort(Comparator.comparingDouble(
                    site -> site.stand().distSqr(origin)));
            for (FishingSite site : ring) {
                if (canReachFishingSite(creature, site)) {
                    return Optional.of(site);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Water-only navigation reaches the shoreline water first; land and
     * amphibious navigation can target the dry casting position directly.
     */
    public static Vec3 fishingApproach(
            ChangedEntity creature,
            FishingSite site) {
        return approachesFishingSiteThroughWater(creature)
                ? Vec3.atCenterOf(site.water())
                : Vec3.atBottomCenterOf(site.stand());
    }

    public static boolean approachesFishingSiteThroughWater(
            ChangedEntity creature) {
        return creature.isInWater()
                && creature.getNavigation() instanceof WaterBoundPathNavigation;
    }

    private static void addFishingSitesAt(
            ServerLevel level,
            BlockPos stand,
            List<FishingSite> output,
            boolean allowIceFishing) {
        if (!isDryStand(level, stand)) {
            return;
        }
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            BlockPos beside = stand.relative(direction);
            for (int drop = 0; drop <= 1; drop++) {
                BlockPos water = beside.below(drop);
                if (level.getBlockState(water).is(Blocks.WATER)
                        && level.getFluidState(water.above()).isEmpty()) {
                    FishingSite site = new FishingSite(
                            stand.immutable(), water.immutable());
                    if (!output.contains(site)) {
                        output.add(site);
                    }
                    break;
                }
            }
            if (allowIceFishing) {
                BlockPos ice = beside.below();
                if (level.getBlockState(ice).is(Blocks.ICE)
                        && level.getFluidState(ice.below()).is(FluidTags.WATER)) {
                    FishingSite site = new FishingSite(
                            stand.immutable(), ice.immutable(), true);
                    if (!output.contains(site)) {
                        output.add(site);
                    }
                }
            }
        }
    }

    /** Opens one temporary fishing hole and returns the exact state to restore. */
    @Nullable
    public static BlockState openIceFishingHole(
            ServerLevel level,
            FishingSite site) {
        if (site == null || !site.iceCovered()) {
            return null;
        }
        BlockState original = level.getBlockState(site.water());
        if (!original.is(Blocks.ICE)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return null;
        }
        level.levelEvent(2001, site.water(), Block.getId(original));
        return level.setBlock(
                site.water(), Blocks.WATER.defaultBlockState(), Block.UPDATE_ALL)
                ? original : null;
    }

    public static void restoreIceFishingHole(
            ServerLevel level,
            @Nullable FishingSite site,
            @Nullable BlockState original) {
        if (site == null || original == null || !site.iceCovered()) {
            return;
        }
        BlockState current = level.getBlockState(site.water());
        if (current.is(Blocks.WATER)) {
            level.setBlock(site.water(), original, Block.UPDATE_ALL);
        }
    }

    private static boolean canReachFishingSite(
            ChangedEntity creature,
            FishingSite site) {
        BlockPos target = approachesFishingSiteThroughWater(creature)
                ? site.water() : site.stand();
        return isReachableStand(creature, target);
    }

    public static boolean catchFish(
            ChangedEntity creature,
            BlockPos water) {
        if (!(creature.level() instanceof ServerLevel level)
                || hasCargo(creature)
                || !level.getBlockState(water).is(Blocks.WATER)) {
            return false;
        }
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(water))
                .withParameter(LootContextParams.TOOL, rod)
                .withParameter(LootContextParams.THIS_ENTITY, creature)
                .withLuck(0.0F)
                .create(LootContextParamSets.FISHING);
        LootTable table = level.getServer().getLootData()
                .getLootTable(BuiltInLootTables.FISHING);
        List<ItemStack> caught = table.getRandomItems(params);
        int carriedIndex = -1;
        ResourceKind carriedKind = null;
        for (int i = 0; i < caught.size(); i++) {
            ResourceKind candidateKind = classify(creature, caught.get(i));
            if (!caught.get(i).isEmpty() && candidateKind != null) {
                carriedIndex = i;
                carriedKind = candidateKind;
                break;
            }
        }
        ItemStack carried = carriedIndex >= 0
                ? caught.get(carriedIndex).copy() : ItemStack.EMPTY;
        if (carried.isEmpty()) {
            caught.stream()
                    .filter(stack -> !stack.isEmpty())
                    .forEach(stack -> Block.popResource(
                            level, creature.blockPosition(), stack));
            return false;
        }
        setCargo(creature, carried, carriedKind,
                classifyProvisionSource(creature, carried, carriedKind));
        for (int i = 0; i < caught.size(); i++) {
            if (i != carriedIndex && !caught.get(i).isEmpty()) {
                Block.popResource(level, creature.blockPosition(), caught.get(i));
            }
        }
        level.playSound(null, creature.blockPosition(),
                SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.NEUTRAL,
                0.8F, 0.9F + creature.getRandom().nextFloat() * 0.2F);
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    public static Optional<BlockPos> findExposedOre(
            ChangedEntity creature,
            int horizontalRadius,
            int verticalRadius) {
        if (!(creature.level() instanceof ServerLevel level)
                || !isCaveCommunity(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return Optional.empty();
        }
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        BlockPos origin = creature.blockPosition();
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                origin.offset(-horizontalRadius, -verticalRadius, -horizontalRadius),
                origin.offset(horizontalRadius, verticalRadius, horizontalRadius))) {
            BlockState state = level.getBlockState(candidate);
            if (!state.is(Tags.Blocks.ORES)
                    || state.getDestroySpeed(level, candidate) < 0.0F
                    || !pickaxe.isCorrectToolForDrops(state)
                    || !isExposed(level, candidate)) {
                continue;
            }
            double distance = candidate.distSqr(origin);
            if (distance < bestDistance) {
                best = candidate.immutable();
                bestDistance = distance;
            }
        }
        return Optional.ofNullable(best);
    }

    public static boolean mineOre(
            ChangedEntity creature,
            BlockPos position) {
        if (!(creature.level() instanceof ServerLevel level)
                || hasCargo(creature)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        ItemStack pickaxe = new ItemStack(Items.IRON_PICKAXE);
        if (!state.is(Tags.Blocks.ORES)
                || !pickaxe.isCorrectToolForDrops(state)
                || !isExposed(level, position)) {
            return false;
        }
        List<ItemStack> drops = Block.getDrops(
                state, level, position, level.getBlockEntity(position), creature, pickaxe);
        ItemStack carried = drops.stream()
                .filter(stack -> !stack.isEmpty())
                .findFirst()
                .map(ItemStack::copy)
                .orElse(ItemStack.EMPTY);
        if (carried.isEmpty()) {
            return false;
        }
        setCargo(creature, carried, ResourceKind.MATERIAL,
                ProvisionSource.MINERAL);
        boolean skippedFirst = false;
        for (ItemStack drop : drops) {
            if (!skippedFirst && !drop.isEmpty()) {
                skippedFirst = true;
            } else if (!drop.isEmpty()) {
                Block.popResource(level, position, drop);
            }
        }
        level.setBlock(position, state.getFluidState().createLegacyBlock(), Block.UPDATE_ALL);
        level.levelEvent(2001, position, Block.getId(state));
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    public static boolean collect(
            ChangedEntity creature,
            ItemEntity itemEntity) {
        if (!enabled(creature)
                || hasCargo(creature)
                || !itemEntity.isAlive()
                || creature.distanceToSqr(itemEntity) > 2.4D * 2.4D) {
            return false;
        }
        ItemStack source = itemEntity.getItem();
        ResourceKind kind = classify(creature, source);
        if (kind == null) {
            releaseResourceClaim(creature, itemEntity);
            return false;
        }
        ItemStack carried = source.copy();
        carried.setCount(1);
        releaseResourceClaim(creature, itemEntity);
        setCargo(creature, carried, kind,
                classifyProvisionSource(creature, carried, kind));
        source.shrink(1);
        if (source.isEmpty()) {
            itemEntity.discard();
        } else {
            itemEntity.setItem(source);
        }
        if (creature.level() instanceof ServerLevel level) {
            level.sendParticles(
                    ParticleTypes.COMPOSTER,
                    creature.getX(),
                    creature.getY(0.65D),
                    creature.getZ(),
                    5,
                    0.22D,
                    0.18D,
                    0.22D,
                    0.02D);
        }
        CreatureLifeMemory.incrementRoleStat(creature, 0);
        return true;
    }

    public static boolean depositCargo(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level) || !hasCargo(creature)) {
            return false;
        }
        ItemStack carried = cargo(creature);
        ResourceKind kind = cargoKind(creature);
        ProvisionSource source = recentProvisionSource(creature);
        Optional<BlockPos> cache = ensureCachePosition(creature);
        if (HunterFaction.of(creature) == HunterFaction.WHITE
                && !isFacilityCommunity(creature)
                && cache.isEmpty()) {
            int amount = carried.getCount();
            ItemStack delivered = carried.copy();
            boolean orange = RelationshipFavorService
                    .isOrdinaryOrange(carried);
            clearCargo(creature);
            CreatureCommunityData.recordConsensusDeposit(
                    creature, kind, amount, orange);
            CreatureCommunityData.recordTradeDeposit(
                    creature, delivered, amount);
            ProvisionerTradeService.recordDelivery(
                    creature, delivered, source);
            showDepositParticles(level, creature.blockPosition());
            return true;
        }

        if (cache.isEmpty()) {
            return false;
        }
        Container container = containerAt(level, cache.get());
        if (container == null) {
            // An unloaded chunk is not evidence that the cache was destroyed.
            // Keep the binding and retry when the provider can approach it.
            if (!level.hasChunkAt(cache.get())) {
                return false;
            }
            if (isFacilityCommunity(creature)) {
                creature.getPersistentData().remove(FACILITY_STORAGE_POS);
            } else {
                CreatureCommunityData.clearCache(creature);
            }
            return false;
        }
        int before = carried.getCount();
        ItemStack remainder = insert(container, carried);
        int inserted = before - remainder.getCount();
        if (inserted <= 0) {
            return false;
        }
        if (remainder.isEmpty()) {
            clearCargo(creature);
        } else {
            setCargo(creature, remainder, kind);
        }
        CreatureCommunityData.recordDeposit(creature, kind, inserted);
        ItemStack delivered = carried.copyWithCount(inserted);
        CreatureCommunityData.recordTradeDeposit(creature, delivered, inserted);
        ProvisionerTradeService.recordDelivery(creature, delivered, source);
        CreatureLifeMemory.incrementRoleStat(creature, 1);
        showDepositParticles(level, cache.get());
        return true;
    }

    public static Optional<BlockPos> cachePosition(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return Optional.empty();
        }
        FacilitySnapshot facility = facilityFor(creature);
        if (facility != null) {
            FacilityWorkArea area = facilityWorkArea(creature);
            CompoundTag memory = creature.getPersistentData();
            if (area == null
                    || area.orangeRoom() == null
                    || !memory.contains(FACILITY_STORAGE_POS, Tag.TAG_LONG)) {
                return Optional.empty();
            }
            BlockPos position = BlockPos.of(
                    memory.getLong(FACILITY_STORAGE_POS));
            if (!level.hasChunkAt(position)) {
                return Optional.of(position);
            }
            if (facilityStorageKind(level, position, area)
                            == FacilityStorageKind.NONE
                    || containerAt(level, position) == null) {
                memory.remove(FACILITY_STORAGE_POS);
                return Optional.empty();
            }
            return Optional.of(position);
        }

        Optional<BlockPos> stored = CreatureCommunityData.snapshot(creature)
                .flatMap(CreatureCommunityData.Snapshot::cache);
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        if (!level.hasChunkAt(stored.get())) {
            return stored;
        }
        if (containerAt(level, stored.get()) == null) {
            CreatureCommunityData.clearCache(creature);
            return Optional.empty();
        }
        return stored;
    }

    /**
     * Finds the nearest loaded storage point belonging to this creature's
     * compatible faction branch. Light-latex regional groups remain separate,
     * matching their independent reputation and community records.
     */
    public static Optional<BlockPos> nearestCompatibleCache(
            ChangedEntity creature,
            double radius) {
        if (!(creature.level() instanceof ServerLevel level) || radius <= 0.0D) {
            return Optional.empty();
        }
        return compatibleCachePositions(creature, radius).stream()
                .filter(position -> containerAt(level, position) != null)
                .min(Comparator.comparingDouble(position ->
                        position.distSqr(creature.blockPosition())));
    }

    /** Returns a real, replenish-able orange-pile position at a compatible cache. */
    public static Optional<BlockPos> nearestOrangePileSite(
            ChangedEntity creature,
            double radius) {
        if (!(creature.level() instanceof ServerLevel level) || radius <= 0.0D) {
            return Optional.empty();
        }
        return compatibleCachePositions(creature, radius).stream()
                .filter(position -> cacheHasOrangePile(level, position))
                .map(BlockPos::above)
                .filter(position -> canRestockOrangePileAt(level, position))
                .min(Comparator.comparingDouble(position ->
                        position.distSqr(creature.blockPosition())));
    }

    /** True for the designated top of an orange-producing community cache. */
    public static boolean canRestockOrangePileAt(
            ServerLevel level,
            BlockPos position) {
        if (!cacheHasOrangePile(level, position.below())) {
            return false;
        }
        BlockState state = level.getBlockState(position);
        return isOrangePile(state)
                ? state.getValue(DroppedOrange.ORANGES) < 8
                : state.canBeReplaced();
    }

    /** Adds exactly one physical orange to a designated cache pile. */
    public static boolean addOneOrangeToPile(
            ServerLevel level,
            BlockPos position) {
        if (!canRestockOrangePileAt(level, position)) {
            return false;
        }
        BlockState current = level.getBlockState(position);
        BlockState replacement;
        if (isOrangePile(current)) {
            replacement = current.setValue(
                    DroppedOrange.ORANGES,
                    current.getValue(DroppedOrange.ORANGES) + 1);
        } else {
            replacement = ChangedBlocks.DROPPED_ORANGE.get()
                    .defaultBlockState()
                    .setValue(DroppedOrange.ORANGES, 1)
                    .setValue(
                            DroppedOrange.WATERLOGGED,
                            level.getFluidState(position).is(FluidTags.WATER));
        }
        return level.setBlock(position, replacement, Block.UPDATE_ALL);
    }

    private static List<BlockPos> compatibleCachePositions(
            ChangedEntity creature,
            double radius) {
        List<BlockPos> positions = new ArrayList<>();
        Optional<BlockPos> own = cachePosition(creature);
        own.ifPresent(positions::add);
        // Facility workers remain inside their own section and must not adopt
        // an outdoor cache merely because its faction happens to match.
        if (!isFacilityCommunity(creature)) {
            positions.addAll(CreatureCommunityData.nearbyCompatibleCaches(
                    creature, radius));
        }
        double radiusSqr = radius * radius;
        return positions.stream()
                .filter(position -> position.distSqr(creature.blockPosition())
                        <= radiusSqr)
                .distinct()
                .toList();
    }

    private static Optional<BlockPos> findFacilityStorage(
            ChangedEntity creature,
            FacilityWorkArea area) {
        if (!(creature.level() instanceof ServerLevel level)
                || area.orangeRoom() == null) {
            return Optional.empty();
        }
        ItemStack carried = cargo(creature);
        if (carried.isEmpty()) {
            Item orange = ForgeRegistries.ITEMS.getValue(
                    ResourceLocation.fromNamespaceAndPath(
                            "changed", "orange"));
            carried = orange == null ? ItemStack.EMPTY : new ItemStack(orange);
        }
        boolean returningCargo = hasCargo(creature);
        CompoundTag memory = creature.getPersistentData();
        if (memory.contains(FACILITY_STORAGE_POS, Tag.TAG_LONG)) {
            BlockPos remembered = BlockPos.of(
                    memory.getLong(FACILITY_STORAGE_POS));
            // Keep an unloaded binding: a temporarily unloaded chunk is not a
            // destroyed box, and forgetting it causes repeated facility-wide
            // container scans.
            if (!level.hasChunkAt(remembered)) {
                return Optional.of(remembered);
            }
            Container container = containerAt(level, remembered);
            if (container != null
                    && facilityStorageKind(level, remembered, area)
                            != FacilityStorageKind.NONE
                    && hasStorageRoom(container, carried)
                    && facilityPathWithinSection(
                            creature, remembered, area, 2, returningCargo)) {
                return Optional.of(remembered);
            }
            memory.remove(FACILITY_STORAGE_POS);
        }
        BlockPos origin = area.orangeRoom().piece().region().getCenter();
        int originChunkX = origin.getX() >> 4;
        int originChunkZ = origin.getZ() >> 4;
        BlockPos nearestRoomChest = null;
        BlockPos nearestRoomCardboard = null;
        BlockPos nearestCardboard = null;
        double chestDistance = Double.MAX_VALUE;
        double roomCardboardDistance = Double.MAX_VALUE;
        double cardboardDistance = Double.MAX_VALUE;
        for (int radius = 0; radius <= FACILITY_STORAGE_CHUNK_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0
                            && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(
                            originChunkX + dx, originChunkZ + dz);
                    if (chunk == null) {
                        continue;
                    }
                    for (BlockEntity blockEntity
                            : chunk.getBlockEntities().values()) {
                        BlockPos position = blockEntity.getBlockPos();
                        if (!(blockEntity instanceof Container container)) {
                            continue;
                        }
                        FacilityStorageKind kind = facilityStorageKind(
                                level, position, area);
                        if (kind == FacilityStorageKind.NONE
                                || !hasStorageRoom(container, carried)
                                || !facilityPathWithinSection(
                                        creature, position, area, 2,
                                        returningCargo)) {
                            continue;
                        }
                        double distance = position.distSqr(origin);
                        if (kind == FacilityStorageKind.ORANGE_ROOM_CHEST
                                && distance < chestDistance) {
                            nearestRoomChest = position.immutable();
                            chestDistance = distance;
                        } else if (kind
                                        == FacilityStorageKind.ORANGE_ROOM_CARDBOARD
                                && distance < roomCardboardDistance) {
                            nearestRoomCardboard = position.immutable();
                            roomCardboardDistance = distance;
                        } else if (kind == FacilityStorageKind.CARDBOARD
                                && distance < cardboardDistance) {
                            nearestCardboard = position.immutable();
                            cardboardDistance = distance;
                        }
                    }
                }
            }
        }
        BlockPos selected;
        if (isTreeTemplate(area.orangeRoom())) {
            // The vanilla chest in *_tree templates sits behind a locked lab
            // door. These rooms deliberately use the cardboard boxes beside
            // the tree and never attempt to route through that door.
            selected = nearestRoomCardboard != null
                    ? nearestRoomCardboard : nearestCardboard;
        } else {
            selected = nearestRoomChest != null
                    ? nearestRoomChest
                    : nearestRoomCardboard != null
                            ? nearestRoomCardboard : nearestCardboard;
        }
        return Optional.ofNullable(selected);
    }

    private static FacilityStorageKind facilityStorageKind(
            ServerLevel level,
            BlockPos position,
            FacilityWorkArea area) {
        if (!level.hasChunkAt(position)) {
            return FacilityStorageKind.NONE;
        }
        FacilitySnapshot actual = TerritoryContextEvents.facilityAt(
                level, position);
        if (!sameFacilitySection(actual, area)
                || !(level.getBlockEntity(position) instanceof Container)) {
            return FacilityStorageKind.NONE;
        }
        ResourceLocation blockId = ForgeRegistries.BLOCKS.getKey(
                level.getBlockState(position).getBlock());
        boolean insideOrangeRoom = area.orangeRoom() != null
                && area.orangeRoom().piece().region().isInside(position);
        if (VANILLA_CHEST.equals(blockId)
                && !isTreeTemplate(area.orangeRoom())
                && insideOrangeRoom) {
            return FacilityStorageKind.ORANGE_ROOM_CHEST;
        }
        if (CARDBOARD_BOX.equals(blockId)
                || CARDBOARD_BOX_SMALL.equals(blockId)) {
            return insideOrangeRoom
                    ? FacilityStorageKind.ORANGE_ROOM_CARDBOARD
                    : FacilityStorageKind.CARDBOARD;
        }
        return FacilityStorageKind.NONE;
    }

    private static boolean hasStorageRoom(
            Container container,
            ItemStack carried) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack existing = container.getItem(slot);
            if (carried.isEmpty()) {
                if (existing.isEmpty()
                        || existing.getCount() < Math.min(
                                existing.getMaxStackSize(),
                                container.getMaxStackSize())) {
                    return true;
                }
                continue;
            }
            if (!container.canPlaceItem(slot, carried)) {
                continue;
            }
            if (existing.isEmpty()
                    || ItemStack.isSameItemSameTags(existing, carried)
                            && existing.getCount() < Math.min(
                                    existing.getMaxStackSize(),
                                    container.getMaxStackSize())) {
                return true;
            }
        }
        return false;
    }

    /** Stable personal rest point inside a shared cache blueprint. */
    public static Optional<Vec3> restPosition(ChangedEntity creature) {
        if (HunterFaction.of(creature) == HunterFaction.WHITE) {
            return Optional.empty();
        }
        Optional<BlockPos> cache = cachePosition(creature);
        Optional<Blueprint> blueprint = cache.flatMap(position ->
                blueprintFor(creature, position));
        if (blueprint.isEmpty()
                || cache.isEmpty()
                || blueprint.get().restPoints().isEmpty()) {
            return Optional.empty();
        }
        Blueprint selected = blueprint.get();
        BlockPos base = cache.get().subtract(selected.cacheOffset());
        long mixed = creature.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(creature.getUUID().getLeastSignificantBits(), 17);
        int index = (int)Math.floorMod(mixed, selected.restPoints().size());
        return Optional.of(Vec3.atBottomCenterOf(
                base.offset(selected.restPoints().get(index))));
    }

    private static Optional<Blueprint> blueprintFor(
            ChangedEntity creature,
            @Nullable BlockPos cache) {
        // Once an outpost exists, its stored blueprint is authoritative. A
        // visitor from another regional population must never dismantle and
        // rebuild the same physical cache in its own style.
        if (creature.level() instanceof ServerLevel level && cache != null) {
            BlockEntity blockEntity = level.getBlockEntity(cache);
            if (blockEntity != null) {
                ResourceLocation stored = ResourceLocation.tryParse(
                        blockEntity.getPersistentData().getString(CACHE_BLUEPRINT));
                if (stored != null) {
                    Optional<Blueprint> existing =
                            CreatureSettlementBlueprints.INSTANCE.byId(stored);
                    if (existing.isPresent()) {
                        return existing;
                    }
                }
            }
        }
        ResourceLocation regionalLayout = switch (
                settlementRegion(creature, cache)) {
            case "cave" -> CAVE_CACHE_BLUEPRINT;
            case "taiga" -> TAIGA_CACHE_BLUEPRINT;
            case "snowy" -> SNOWY_CACHE_BLUEPRINT;
            case "desert" -> DESERT_CACHE_BLUEPRINT;
            case "badlands" -> BADLANDS_CACHE_BLUEPRINT;
            default -> null;
        };
        if (regionalLayout != null) {
            Optional<Blueprint> selected =
                    CreatureSettlementBlueprints.INSTANCE.byId(regionalLayout);
            if (selected.isPresent()) {
                return selected;
            }
        }
        if (HunterArchetype.of(creature) == HunterArchetype.AQUATIC) {
            boolean offshore = cache != null
                    ? creature.level().getFluidState(cache).is(FluidTags.WATER)
                    : aquaticProvisionMode(creature)
                            == AquaticProvisionMode.OPEN_OCEAN;
            Optional<Blueprint> selected = CreatureSettlementBlueprints.INSTANCE.byId(
                    offshore ? AQUATIC_OFFSHORE_BLUEPRINT : AQUATIC_SHORE_BLUEPRINT);
            if (selected.isPresent()) {
                return selected;
            }
        }
        Optional<Blueprint> matched =
                CreatureSettlementBlueprints.INSTANCE.forCreature(creature);
        if (matched.isPresent()) {
            return matched;
        }
        return Optional.empty();
    }

    /** Selects decoration from the outpost's actual habitat when inspecting an
     * existing cache. Before placement, the creature's stable light-faction
     * region is authoritative so a trip underground cannot turn a forest
     * community into a cave community. */
    private static String settlementRegion(
            ChangedEntity creature,
            @Nullable BlockPos cache) {
        if (!(creature.level() instanceof ServerLevel level)
                || HunterFaction.of(creature) != HunterFaction.LIGHT) {
            return LightFactionGroup.GENERAL;
        }
        return cache == null
                ? LightFactionGroup.of(creature)
                : LightFactionGroup.at(level, cache);
    }

    public static int storedItemCount(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return 0;
        }
        Container container = cachePosition(creature)
                .map(position -> containerAt(level, position))
                .orElse(null);
        if (container == null) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            count += container.getItem(slot).getCount();
        }
        return count;
    }

    /** Loaded physical storage used by server-authoritative community trades. */
    @Nullable
    public static Container communityContainer(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return null;
        }
        return cachePosition(creature)
                .map(position -> containerAt(level, position))
                .orElse(null);
    }

    public static int storedCount(ChangedEntity creature, ItemStack sample) {
        Container container = communityContainer(creature);
        if (container == null || sample.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (ItemStack.isSameItemSameTags(stack, sample)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    public static boolean canStoreFully(ChangedEntity creature, ItemStack offered) {
        Container container = communityContainer(creature);
        if (container == null || offered.isEmpty()) {
            return false;
        }
        int remaining = offered.getCount();
        for (int slot = 0;
                slot < container.getContainerSize() && remaining > 0;
                slot++) {
            if (!container.canPlaceItem(slot, offered)) {
                continue;
            }
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                remaining -= Math.min(
                        offered.getMaxStackSize(), container.getMaxStackSize());
            } else if (ItemStack.isSameItemSameTags(existing, offered)) {
                remaining -= Math.max(0, Math.min(
                        existing.getMaxStackSize(), container.getMaxStackSize())
                        - existing.getCount());
            }
        }
        return remaining <= 0;
    }

    public static boolean storeCommunityPayment(
            ChangedEntity creature, ItemStack payment) {
        Container container = communityContainer(creature);
        return container != null
                && canStoreFully(creature, payment)
                && insert(container, payment).isEmpty();
    }

    /** Goods accepted from a player stay on the provisioner until its next
     * delivery trip, so the exchange remains visible in creature behaviour. */
    public static void queueTradeReturn(
            ChangedEntity creature, ItemStack received) {
        if (received.isEmpty()) {
            return;
        }
        CompoundTag memory = creature.getPersistentData();
        List<ItemStack> stacks = new ArrayList<>();
        ListTag saved = memory.getList(TRADE_RETURNS, Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            ItemStack stack = ItemStack.of(saved.getCompound(index));
            if (!stack.isEmpty()) {
                stacks.add(stack);
            }
        }
        ItemStack remainder = received.copy();
        for (ItemStack stack : stacks) {
            if (ItemStack.isSameItemSameTags(stack, remainder)
                    && stack.getCount() < stack.getMaxStackSize()) {
                int moved = Math.min(
                        remainder.getCount(),
                        stack.getMaxStackSize() - stack.getCount());
                stack.grow(moved);
                remainder.shrink(moved);
                if (remainder.isEmpty()) {
                    break;
                }
            }
        }
        while (!remainder.isEmpty()) {
            int moved = Math.min(remainder.getCount(), remainder.getMaxStackSize());
            stacks.add(remainder.copyWithCount(moved));
            remainder.shrink(moved);
        }
        writeTradeReturns(memory, stacks);
        CreatureLifeMemory.scheduleNextDecision(
                creature, creature.level().getGameTime() + 10L);
    }

    public static boolean hasTradeReturns(ChangedEntity creature) {
        return !creature.getPersistentData()
                .getList(TRADE_RETURNS, Tag.TAG_COMPOUND).isEmpty();
    }

    public static boolean depositTradeReturns(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || !hasTradeReturns(creature)) {
            return false;
        }
        CompoundTag memory = creature.getPersistentData();
        ListTag saved = memory.getList(TRADE_RETURNS, Tag.TAG_COMPOUND);
        Optional<BlockPos> cache = ensureCachePosition(creature);
        boolean consensus = HunterFaction.of(creature) == HunterFaction.WHITE
                && !isFacilityCommunity(creature) && cache.isEmpty();
        Container container = cache.map(position -> containerAt(level, position))
                .orElse(null);
        if (!consensus && container == null) {
            return false;
        }
        List<ItemStack> remaining = new ArrayList<>();
        int deposited = 0;
        for (int index = 0; index < saved.size(); index++) {
            ItemStack original = ItemStack.of(saved.getCompound(index));
            if (original.isEmpty()) {
                continue;
            }
            ItemStack remainder = consensus
                    ? ItemStack.EMPTY : insert(container, original);
            int inserted = original.getCount() - remainder.getCount();
            if (inserted > 0) {
                ItemStack delivered = original.copyWithCount(inserted);
                ResourceKind kind = classify(creature, delivered);
                if (kind == null) {
                    kind = isGeneralFood(delivered)
                            ? ResourceKind.FOOD : ResourceKind.MATERIAL;
                }
                CreatureCommunityData.recordDeposit(creature, kind, inserted);
                CreatureCommunityData.recordTradeDeposit(
                        creature, delivered, inserted);
                deposited += inserted;
            }
            if (!remainder.isEmpty()) {
                remaining.add(remainder);
            }
        }
        writeTradeReturns(memory, remaining);
        if (deposited > 0) {
            showDepositParticles(
                    level, cache.orElse(creature.blockPosition()));
        }
        return deposited > 0;
    }

    private static void writeTradeReturns(
            CompoundTag memory, List<ItemStack> stacks) {
        if (stacks.isEmpty()) {
            memory.remove(TRADE_RETURNS);
            return;
        }
        ListTag saved = new ListTag();
        for (ItemStack stack : stacks) {
            if (!stack.isEmpty()) {
                saved.add(stack.save(new CompoundTag()));
            }
        }
        memory.put(TRADE_RETURNS, saved);
    }

    /** Removes an exact stack only after confirming that the full amount exists. */
    public static ItemStack extractStored(
            ChangedEntity creature, ItemStack sample, int amount) {
        Container container = communityContainer(creature);
        if (container == null || sample.isEmpty() || amount <= 0
                || storedCount(creature, sample) < amount) {
            return ItemStack.EMPTY;
        }
        ItemStack extracted = sample.copyWithCount(amount);
        int remaining = amount;
        for (int slot = 0;
                slot < container.getContainerSize() && remaining > 0;
                slot++) {
            ItemStack stack = container.getItem(slot);
            if (!ItemStack.isSameItemSameTags(stack, sample)) {
                continue;
            }
            int moved = Math.min(remaining, stack.getCount());
            stack.shrink(moved);
            remaining -= moved;
            if (stack.isEmpty()) {
                container.setItem(slot, ItemStack.EMPTY);
            }
        }
        container.setChanged();
        return remaining == 0 ? extracted : ItemStack.EMPTY;
    }

    /** Consumes one real food item from the shared cache. */
    public static boolean consumeFood(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return false;
        }
        Container container = cachePosition(creature)
                .map(position -> containerAt(level, position))
                .orElse(null);
        if (container == null) {
            return false;
        }
        int fallbackSlot = -1;
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (stack.isEmpty()) {
                continue;
            }
            if (RelationshipFavorService.isDedicatedDietFood(creature, stack)) {
                shrinkOne(container, slot);
                return true;
            }
            if (fallbackSlot < 0 && isGeneralFood(stack)) {
                fallbackSlot = slot;
            }
        }
        if (fallbackSlot >= 0) {
            shrinkOne(container, fallbackSlot);
            return true;
        }
        return false;
    }

    public static Optional<BlockPos> ensureCachePosition(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || !ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.CREATURE_LIFE)) {
            return Optional.empty();
        }

        // Facilities already contain real Changed storage. Their workers must
        // neither seed nor decorate those inventories and must never build a
        // second Synergy cache merely because the rooms happen to be below
        // ground.
        FacilitySnapshot facility = facilityFor(creature);
        if (facility != null) {
            FacilityWorkArea area = facilityWorkArea(creature);
            Optional<BlockPos> storage = area == null
                            || area.orangeRoom() == null
                            || "maintenance".equals(area.section())
                    ? Optional.empty()
                    : findFacilityStorage(creature, area);
            if (storage.isPresent()) {
                creature.getPersistentData().putLong(
                        FACILITY_STORAGE_POS, storage.get().asLong());
            } else {
                creature.getPersistentData().remove(FACILITY_STORAGE_POS);
            }
            return storage;
        }

        Optional<BlockPos> recordedCache = cachePosition(creature);
        if (recordedCache.isPresent()) {
            BlockPos position = recordedCache.get();
            if (!level.hasChunkAt(position)) {
                return recordedCache;
            }
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity instanceof Container container
                    && blockEntity.getPersistentData().contains(
                            CACHE_STRUCTURE_ID, Tag.TAG_STRING)) {
                prepareCache(level, creature, position, container);
                return recordedCache;
            }
        }
        if (HunterFaction.of(creature) == HunterFaction.WHITE
                && recordedCache.isPresent()) {
            BlockPos position = recordedCache.get();
            if (!level.hasChunkAt(position)) {
                return recordedCache;
            }
            Container container = containerAt(level, position);
            if (container != null) {
                prepareCache(level, creature, position, container);
                return recordedCache;
            }
            CreatureCommunityData.clearCache(creature);
            recordedCache = Optional.empty();
        }

        if (recordedCache.isEmpty()
                && CreatureLifeMemory.role(creature)
                        == CreatureLifeMemory.GroupRole.PROVISIONER) {
            Optional<BlockPos> claimed = tryClaimChangedStructure(creature, level);
            if (claimed.isPresent()) {
                return claimed;
            }
        }

        // Pure-white bodies outside a facility can dissolve back into their
        // territory and therefore use the hive stock counter instead. A
        // provisioner may still establish the one fixed wild cache allowed
        // for this faction by claiming an existing Changed ruin above.
        if (HunterFaction.of(creature) == HunterFaction.WHITE) {
            return Optional.empty();
        }

        boolean aquatic = HunterArchetype.of(creature) == HunterArchetype.AQUATIC;
        AquaticProvisionMode aquaticMode = aquaticProvisionMode(creature);
        Optional<Blueprint> blueprint = blueprintFor(creature, null);
        if (blueprint.isEmpty()) {
            return Optional.empty();
        }

        BlockPos center = CreatureCommunityData.snapshot(creature)
                .map(CreatureCommunityData.Snapshot::center)
                .orElse(creature.blockPosition());
        BlockPos offshoreAnchor = null;
        if (aquatic && aquaticMode == AquaticProvisionMode.OPEN_OCEAN) {
            offshoreAnchor = level.findNearestMapStructure(
                    AQUATIC_CACHE_ANCHORS,
                    center,
                    OFFSHORE_STRUCTURE_RADIUS_CHUNKS,
                    false);
            if (offshoreAnchor == null) {
                return Optional.empty();
            }
        }

        Optional<BlockPos> existing = cachePosition(creature);
        if (existing.isPresent()) {
            if (!level.hasChunkAt(existing.get())) {
                return existing;
            }
            boolean structureMismatch = offshoreAnchor != null
                    && !cacheBelongsToStructure(
                            level, existing.get(), offshoreAnchor);
            boolean habitatMismatch = !cacheMatchesProvisionMode(
                    level, creature, existing.get(), blueprint.get());
            if (structureMismatch || habitatMismatch) {
                CreatureCommunityData.clearCache(creature);
            } else {
                Container container = containerAt(level, existing.get());
                if (container != null) {
                    if (offshoreAnchor != null) {
                        bindCacheToStructure(
                                level, existing.get(), offshoreAnchor);
                    }
                    prepareCache(level, creature, existing.get(), container);
                }
                return existing;
            }
        }

        Optional<BlockPos> shared = offshoreAnchor == null
                ? findReusableCache(level, creature, blueprint.get())
                : findOffshoreStructureCache(
                        level, creature, offshoreAnchor);
        if (shared.isPresent()) {
            BlockPos position = shared.get();
            Container container = containerAt(level, position);
            if (container == null) {
                // A recorded structure cache may sit just outside the current
                // loaded area. Waiting avoids opening a duplicate cache beside
                // the same shipwreck or ocean ruin.
                return Optional.empty();
            }
            CreatureCommunityData.markCache(creature, position);
            if (offshoreAnchor != null) {
                bindCacheToStructure(level, position, offshoreAnchor);
            }
            prepareCache(level, creature, position, container);
            CreatureCacheGuardService.ensureGuardPresence(creature, position);
            return shared;
        }
        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return Optional.empty();
        }
        Optional<BlockPos> base;
        if (aquatic && aquaticMode == AquaticProvisionMode.NEARSHORE) {
            base = findShorePlacement(
                    level, center, creature, blueprint.get(),
                    AQUATIC_SHORE_PLACEMENT_RADIUS, true);
            if (base.isEmpty()) {
                base = findShorePlacement(
                        level, center, creature, blueprint.get(),
                        AQUATIC_NEARBY_SHORE_RADIUS, false);
            }
        } else if (aquatic) {
            base = findOceanFloorPlacement(
                    level, offshoreAnchor, creature, blueprint.get(),
                    OFFSHORE_CACHE_SEARCH_RADIUS, true);
            if (base.isEmpty()) {
                base = findOceanFloorPlacement(
                        level, offshoreAnchor, creature, blueprint.get(),
                        OFFSHORE_CACHE_SEARCH_RADIUS, false);
            }
        } else {
            base = findPlacement(
                    level, center, creature, blueprint.get(), false,
                    TERRITORY_CACHE_SEARCH_RADIUS, true);
            if (base.isEmpty()) {
                base = findPlacement(
                        level, center, creature, blueprint.get(), false,
                        NEARBY_CACHE_SEARCH_RADIUS, false);
            }
        }
        if (base.isEmpty()) {
            return Optional.empty();
        }
        place(level, base.get(), blueprint.get());
        BlockPos cache = base.get().offset(blueprint.get().cacheOffset());
        Container container = containerAt(level, cache);
        if (container == null) {
            ChangedSynergyMod.LOGGER.warn(
                    "Settlement blueprint {} placed without a container at {}",
                    blueprint.get().id(), cache);
            return Optional.empty();
        }
        BlockEntity placedCache = level.getBlockEntity(cache);
        if (placedCache != null) {
            placedCache.getPersistentData().putString(
                    CACHE_BLUEPRINT, blueprint.get().id().toString());
            if (offshoreAnchor != null) {
                placedCache.getPersistentData().putLong(
                        CACHE_STRUCTURE_ANCHOR, offshoreAnchor.asLong());
            }
            placedCache.setChanged();
        }
        CreatureCommunityData.markCache(creature, cache);
        prepareCache(level, creature, cache, container);
        CreatureCacheGuardService.ensureGuardPresence(creature, cache);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                cache.getX() + 0.5D,
                cache.getY() + 0.8D,
                cache.getZ() + 0.5D,
                8,
                0.5D,
                0.35D,
                0.5D,
                0.02D);
        return Optional.of(cache);
    }

    /** Claims already-generated Changed ruins without loading or generating
     * chunks solely for the search. The facility is absent from both tags and
     * can therefore never enter this path. */
    private static Optional<BlockPos> tryClaimChangedStructure(
            ChangedEntity creature,
            ServerLevel level) {
        boolean ruinsEnabled = ChangedSynergyConfig.COMMON
                .useChangedStructureOutposts.get();
        boolean beeEnabled = ChangedSynergyConfig.COMMON
                .latexBeeHiveOutposts.get() && isLatexBee(creature);
        if ((!ruinsEnabled && !beeEnabled)
                || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) {
            return Optional.empty();
        }
        long now = level.getGameTime();
        CompoundTag memory = creature.getPersistentData();
        if (memory.getLong(NEXT_STRUCTURE_OUTPOST_SEARCH) > now) {
            return Optional.empty();
        }

        BlockPos center = CreatureCommunityData.snapshot(creature)
                .map(CreatureCommunityData.Snapshot::center)
                .orElse(creature.blockPosition());
        Registry<Structure> structures = level.registryAccess()
                .registryOrThrow(Registries.STRUCTURE);
        Predicate<Structure> accepted = structure -> {
            var holder = structures.wrapAsHolder(structure);
            return ruinsEnabled && holder.is(CLAIMABLE_CHANGED_RUINS)
                    || beeEnabled && holder.is(LATEX_BEE_HIVES);
        };
        int radius = ChangedSynergyConfig.COMMON
                .structureOutpostSearchRadiusChunks.get();
        int centerChunkX = center.getX() >> 4;
        int centerChunkZ = center.getZ() >> 4;
        List<StructureOutpost> candidates = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                int chunkX = centerChunkX + dx;
                int chunkZ = centerChunkZ + dz;
                if (level.getChunkSource().getChunkNow(chunkX, chunkZ) == null) {
                    continue;
                }
                for (StructureStart start : level.structureManager()
                        .startsForStructure(new ChunkPos(chunkX, chunkZ), accepted)) {
                    if (!start.isValid()) {
                        continue;
                    }
                    ResourceLocation structureId = structures.getKey(
                            start.getStructure());
                    if (structureId == null) {
                        continue;
                    }
                    BoundingBox bounds = start.getBoundingBox();
                    BlockPos anchor = new BlockPos(
                            (bounds.minX() + bounds.maxX()) / 2,
                            bounds.minY(),
                            (bounds.minZ() + bounds.maxZ()) / 2);
                    if (candidates.stream().anyMatch(candidate ->
                            candidate.anchor().equals(anchor)
                                    && candidate.structureId().equals(structureId))) {
                        continue;
                    }
                    boolean hive = structures.wrapAsHolder(start.getStructure())
                            .is(LATEX_BEE_HIVES);
                    candidates.add(new StructureOutpost(
                            anchor,
                            bounds,
                            structureId,
                            structureBlueprint(structureId, hive),
                            hive));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(candidate ->
                candidate.anchor().distSqr(center)));
        for (StructureOutpost candidate : candidates) {
            Optional<BlockPos> claimed = establishStructureOutpost(
                    level, creature, candidate);
            if (claimed.isPresent()) {
                memory.remove(NEXT_STRUCTURE_OUTPOST_SEARCH);
                return claimed;
            }
        }
        memory.putLong(
                NEXT_STRUCTURE_OUTPOST_SEARCH,
                now + STRUCTURE_SEARCH_RETRY_TICKS);
        return Optional.empty();
    }

    private static boolean isLatexBee(ChangedEntity creature) {
        return LATEX_BEE.equals(
                ForgeRegistries.ENTITY_TYPES.getKey(creature.getType()));
    }

    private static ResourceLocation structureBlueprint(
            ResourceLocation structureId,
            boolean hive) {
        if (hive) {
            return BEE_HIVE_CACHE_BLUEPRINT;
        }
        String path = structureId.getPath();
        if (path.startsWith("office_area")) {
            return DARK_RUIN_CACHE_BLUEPRINT;
        }
        if (path.startsWith("white_latex_lab")) {
            return WHITE_RUIN_CACHE_BLUEPRINT;
        }
        if (path.startsWith("aquatic")) {
            return AQUATIC_RUIN_CACHE_BLUEPRINT;
        }
        return RUIN_CACHE_BLUEPRINT;
    }

    private static Optional<BlockPos> establishStructureOutpost(
            ServerLevel level,
            ChangedEntity creature,
            StructureOutpost outpost) {
        Optional<BlockPos> shared = CreatureCommunityData.compatibleCaches(creature)
                .stream()
                .filter(position -> cacheBelongsToStructure(
                        level, position, outpost.anchor()))
                .findFirst();
        if (shared.isPresent()) {
            Container container = containerAt(level, shared.get());
            if (container != null) {
                CreatureCommunityData.markCache(creature, shared.get());
                prepareCache(level, creature, shared.get(), container);
                CreatureCacheGuardService.ensureGuardPresence(
                        creature, shared.get());
                return shared;
            }
        }

        Optional<Blueprint> blueprint = CreatureSettlementBlueprints.INSTANCE
                .byId(outpost.blueprintId());
        if (blueprint.isEmpty()) {
            return Optional.empty();
        }
        Optional<BlockPos> base = findStructurePlacement(
                level, creature, outpost, blueprint.get());
        if (base.isEmpty()) {
            return Optional.empty();
        }
        place(level, base.get(), blueprint.get());
        BlockPos cache = base.get().offset(blueprint.get().cacheOffset());
        Container container = containerAt(level, cache);
        BlockEntity blockEntity = level.getBlockEntity(cache);
        if (container == null || blockEntity == null) {
            return Optional.empty();
        }
        CompoundTag persistent = blockEntity.getPersistentData();
        persistent.putString(CACHE_BLUEPRINT, blueprint.get().id().toString());
        persistent.putLong(CACHE_STRUCTURE_ANCHOR, outpost.anchor().asLong());
        persistent.putString(CACHE_STRUCTURE_ID, outpost.structureId().toString());
        blockEntity.setChanged();
        CreatureCommunityData.markCache(creature, cache);
        prepareCache(level, creature, cache, container);
        CreatureCacheGuardService.ensureGuardPresence(creature, cache);
        level.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                cache.getX() + 0.5D,
                cache.getY() + 0.8D,
                cache.getZ() + 0.5D,
                8,
                0.5D,
                0.35D,
                0.5D,
                0.02D);
        return Optional.of(cache);
    }

    private static Optional<BlockPos> findStructurePlacement(
            ServerLevel level,
            ChangedEntity creature,
            StructureOutpost outpost,
            Blueprint blueprint) {
        BoundingBox bounds = outpost.bounds();
        AABB occupied = new AABB(
                bounds.minX() - 2.0D,
                bounds.minY() - 2.0D,
                bounds.minZ() - 2.0D,
                bounds.maxX() + 3.0D,
                bounds.maxY() + 3.0D,
                bounds.maxZ() + 3.0D);
        if (level.players().stream().anyMatch(player ->
                !player.isSpectator() && occupied.contains(player.position()))) {
            return Optional.empty();
        }
        if (!outpost.outside()) {
            Optional<BlockPos> inside = findInsideStructurePlacement(
                    level, creature, outpost.bounds(), blueprint);
            if (inside.isPresent()) {
                return inside;
            }
        }
        return findBesideStructurePlacement(
                level, creature, outpost.bounds(), blueprint,
                outpost.outside() ? 3 : 1,
                outpost.outside() ? 7 : 4);
    }

    private static Optional<BlockPos> findInsideStructurePlacement(
            ServerLevel level,
            ChangedEntity creature,
            BoundingBox bounds,
            Blueprint blueprint) {
        BlockPos center = new BlockPos(
                (bounds.minX() + bounds.maxX()) / 2,
                (bounds.minY() + bounds.maxY()) / 2,
                (bounds.minZ() + bounds.maxZ()) / 2);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int y = bounds.minY() + 1; y < bounds.maxY(); y++) {
            for (int x = bounds.minX() + 2; x <= bounds.maxX() - 2; x++) {
                for (int z = bounds.minZ() + 2; z <= bounds.maxZ() - 2; z++) {
                    BlockPos base = new BlockPos(x, y, z);
                    double distance = base.distSqr(center);
                    if (distance >= bestDistance
                            || playerIsUsingArea(level, base)
                            || !canPlace(level, base, blueprint, false)) {
                        continue;
                    }
                    best = base.immutable();
                    bestDistance = distance;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static Optional<BlockPos> findBesideStructurePlacement(
            ServerLevel level,
            ChangedEntity creature,
            BoundingBox bounds,
            Blueprint blueprint,
            int minimumRadius,
            int maximumRadius) {
        int centerX = (bounds.minX() + bounds.maxX()) / 2;
        int centerZ = (bounds.minZ() + bounds.maxZ()) / 2;
        int halfWidth = Math.max(1, (bounds.maxX() - bounds.minX()) / 2);
        int halfDepth = Math.max(1, (bounds.maxZ() - bounds.minZ()) / 2);
        for (int radius = minimumRadius; radius <= maximumRadius; radius++) {
            int edgeX = halfWidth + radius;
            int edgeZ = halfDepth + radius;
            for (int dx = -edgeX; dx <= edgeX; dx++) {
                for (int dz = -edgeZ; dz <= edgeZ; dz++) {
                    if (Math.abs(dx) != edgeX && Math.abs(dz) != edgeZ) {
                        continue;
                    }
                    int x = centerX + dx;
                    int z = centerZ + dz;
                    BlockPos column = new BlockPos(x, bounds.minY(), z);
                    if (!level.hasChunkAt(column)) {
                        continue;
                    }
                    BlockPos base = new BlockPos(
                            x,
                            level.getHeight(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    x, z),
                            z);
                    if (!playerIsUsingArea(level, base)
                            && canPlace(level, base, blueprint, false)) {
                        return Optional.of(base.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static boolean playerIsUsingArea(
            ServerLevel level,
            BlockPos position) {
        return level.players().stream().anyMatch(player ->
                !player.isSpectator()
                        && player.distanceToSqr(Vec3.atCenterOf(position))
                                < 6.0D * 6.0D);
    }

    private static Optional<BlockPos> findReusableCache(
            ServerLevel level,
            ChangedEntity creature,
            Blueprint desiredBlueprint) {
        double radius = HunterArchetype.of(creature) == HunterArchetype.AQUATIC
                ? AQUATIC_SHORE_SHARED_CACHE_RADIUS
                : LAND_SHARED_CACHE_RADIUS;
        radius = Math.max(radius, ChangedSynergyConfig.COMMON
                .settlementMinimumSpacing.get());
        return CreatureCommunityData.nearbyCompatibleCaches(
                        creature, radius)
                .stream()
                // An unloaded recorded cache is still authoritative. Returning
                // it makes ensureCachePosition wait for its chunk instead of
                // building a duplicate. Loaded records with no container are
                // stale and may be skipped.
                .filter(position -> !level.hasChunkAt(position)
                        || containerAt(level, position) != null)
                .filter(position -> cacheMatchesProvisionMode(
                        level, creature, position, desiredBlueprint))
                .findFirst();
    }

    private static Optional<BlockPos> findOffshoreStructureCache(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos structureAnchor) {
        Optional<BlockPos> recorded = CreatureCommunityData.compatibleCaches(creature)
                .stream()
                .filter(position -> cacheBelongsToStructure(
                        level, position, structureAnchor))
                .min(Comparator.comparingDouble(position ->
                        position.distSqr(structureAnchor)));
        if (recorded.isPresent()) {
            return recorded;
        }

        // Recover physical caches whose owning community record was lost or
        // rebound. Only loaded chunks are inspected; finding a matching cache
        // here is enough to prevent a second one being built for the structure.
        int chunkRadius = (OFFSHORE_LEGACY_BIND_RADIUS >> 4) + 1;
        int centerChunkX = structureAnchor.getX() >> 4;
        int centerChunkZ = structureAnchor.getZ() >> 4;
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        centerChunkX + dx, centerChunkZ + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    BlockPos position = blockEntity.getBlockPos();
                    if (!(blockEntity instanceof Container)
                            || !cacheBelongsToStructure(
                                    level, position, structureAnchor)) {
                        continue;
                    }
                    double distance = position.distSqr(structureAnchor);
                    if (distance < bestDistance) {
                        best = position.immutable();
                        bestDistance = distance;
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    private static boolean cacheBelongsToStructure(
            ServerLevel level,
            BlockPos cache,
            BlockPos structureAnchor) {
        BlockEntity blockEntity = level.hasChunkAt(cache)
                ? level.getBlockEntity(cache) : null;
        if (blockEntity != null) {
            CompoundTag persistent = blockEntity.getPersistentData();
            if (persistent.contains(CACHE_STRUCTURE_ANCHOR, Tag.TAG_LONG)) {
                return persistent.getLong(CACHE_STRUCTURE_ANCHOR)
                        == structureAnchor.asLong();
            }
            ResourceLocation blueprint = ResourceLocation.tryParse(
                    persistent.getString(CACHE_BLUEPRINT));
            if (!AQUATIC_OFFSHORE_BLUEPRINT.equals(blueprint)) {
                return false;
            }
        }
        return cache.distSqr(structureAnchor)
                <= OFFSHORE_LEGACY_BIND_RADIUS
                        * OFFSHORE_LEGACY_BIND_RADIUS;
    }

    private static void bindCacheToStructure(
            ServerLevel level,
            BlockPos cache,
            BlockPos structureAnchor) {
        BlockEntity blockEntity = level.getBlockEntity(cache);
        if (blockEntity == null) {
            return;
        }
        CompoundTag persistent = blockEntity.getPersistentData();
        if (!persistent.contains(CACHE_STRUCTURE_ANCHOR, Tag.TAG_LONG)
                || persistent.getLong(CACHE_STRUCTURE_ANCHOR)
                        != structureAnchor.asLong()) {
            persistent.putLong(
                    CACHE_STRUCTURE_ANCHOR, structureAnchor.asLong());
            blockEntity.setChanged();
        }
    }

    /**
     * Existing land caches are authoritative for their community branch: a
     * different creature or a datapack-selected decorative layout must not
     * cause another cache to be built beside them. Only the genuinely
     * incompatible nearshore/offshore aquatic work modes stay separate.
     */
    private static boolean cacheMatchesProvisionMode(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos position,
            Blueprint desiredBlueprint) {
        BlockEntity claimedCache = level.hasChunkAt(position)
                ? level.getBlockEntity(position) : null;
        if (claimedCache != null
                && claimedCache.getPersistentData().contains(
                        CACHE_STRUCTURE_ID, Tag.TAG_STRING)) {
            return true;
        }
        if (HunterArchetype.of(creature) != HunterArchetype.AQUATIC) {
            if (!level.hasChunkAt(position)) {
                return true;
            }
            return matchesSettlementHabitat(level, position, creature);
        }
        // Do not force-load a remote aquatic cache merely to inspect its mode.
        // Treat the claim as authoritative until its chunk can be checked.
        if (!level.hasChunkAt(position)) {
            return true;
        }
        boolean desiredOffshore = AQUATIC_OFFSHORE_BLUEPRINT.equals(
                desiredBlueprint.id());
        BlockEntity blockEntity = level.getBlockEntity(position);
        ResourceLocation stored = blockEntity == null ? null
                : ResourceLocation.tryParse(blockEntity.getPersistentData()
                        .getString(CACHE_BLUEPRINT));
        if (AQUATIC_OFFSHORE_BLUEPRINT.equals(stored)) {
            return desiredOffshore;
        }
        if (AQUATIC_SHORE_BLUEPRINT.equals(stored)) {
            return !desiredOffshore;
        }
        boolean existingOffshore = level.getFluidState(position)
                .is(FluidTags.WATER);
        return existingOffshore == desiredOffshore;
    }

    private static ServerLevel levelOf(ChangedEntity creature) {
        return (ServerLevel)creature.level();
    }

    /** Seeds and decorates a cache exactly once, including old empty caches. */
    private static void prepareCache(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos position,
            Container container) {
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            return;
        }
        CompoundTag persistent = blockEntity.getPersistentData();
        Optional<Blueprint> selected = blueprintFor(creature, position);
        ResourceLocation previousId = ResourceLocation.tryParse(
                persistent.getString(CACHE_BLUEPRINT));
        Optional<Blueprint> previous = previousId == null
                ? Optional.empty()
                : CreatureSettlementBlueprints.INSTANCE.byId(previousId);
        selected.ifPresent(blueprint -> {
            if (!blueprint.id().toString().equals(
                    persistent.getString(CACHE_BLUEPRINT))) {
                previous.ifPresent(old -> clearBlueprintCells(
                        level, position, old));
                persistent.putString(CACHE_BLUEPRINT, blueprint.id().toString());
                persistent.putInt(CACHE_DECORATED, 0);
            }
        });
        boolean hasOrangePile = selected
                .map(blueprint -> supportsOrangePile(level, position, blueprint))
                .orElse(persistent.getBoolean(CACHE_HAS_ORANGE_PILE));
        persistent.putBoolean(CACHE_HAS_ORANGE_PILE, hasOrangePile);
        if (!persistent.getBoolean(CACHE_INITIALIZED)) {
            if (container.isEmpty()) {
                seedInitialSupplies(
                        creature,
                        container,
                        selected.map(blueprint ->
                                        AQUATIC_OFFSHORE_BLUEPRINT.equals(blueprint.id()))
                                .orElse(false));
            }
            recordContainerTradeStock(creature, container);
            persistent.putBoolean(CACHE_INITIALIZED, true);
            persistent.putInt(CACHE_TRADE_STOCK_VERSION, TRADE_STOCK_VERSION);
        } else if (persistent.getInt(CACHE_TRADE_STOCK_VERSION)
                < TRADE_STOCK_VERSION) {
            recordContainerTradeStock(creature, container);
            persistent.putInt(CACHE_TRADE_STOCK_VERSION, TRADE_STOCK_VERSION);
        }
        if (selected.isPresent()
                && persistent.getInt(CACHE_DECORATED) < CACHE_DECORATION_VERSION) {
            migrateCacheLayout(level, creature, position, selected.get());
            decorateCache(
                    level, creature, position, selected.get(), hasOrangePile);
            persistent.putInt(CACHE_DECORATED, CACHE_DECORATION_VERSION);
        }
        blockEntity.setChanged();
    }

    /** Removes only blocks that still match the previous generated layout. */
    private static void clearBlueprintCells(
            ServerLevel level,
            BlockPos cache,
            Blueprint previous) {
        BlockPos base = cache.subtract(previous.cacheOffset());
        for (Cell cell : previous.blocks()) {
            BlockPos position = base.offset(cell.offset());
            if (position.equals(cache)) {
                continue;
            }
            if (level.getBlockState(position).equals(cell.state())) {
                level.setBlock(
                        position, Blocks.AIR.defaultBlockState(),
                        Block.UPDATE_ALL);
            }
        }
    }

    private static void seedInitialSupplies(
            ChangedEntity creature,
            Container container,
            boolean offshoreAquatic) {
        if (isCaveCommunity(creature)) {
            addInitial(container, Items.COBBLESTONE, between(creature, 6, 10));
            addInitial(container, Items.COAL, between(creature, 3, 6));
            addInitial(container, Items.RAW_IRON, between(creature, 1, 3));
            addInitial(container, Items.TORCH, between(creature, 2, 4));
            addInitial(container, Items.GLOW_BERRIES, between(creature, 2, 5));
            return;
        }

        if (isTaigaCommunity(creature)) {
            addInitial(container, Items.SWEET_BERRIES, between(creature, 4, 7));
            addInitial(container, Items.SPRUCE_SAPLING, between(creature, 1, 2));
            addInitial(container, Items.STICK, between(creature, 3, 6));
            addInitial(container, Items.STRING, between(creature, 1, 2));
            return;
        }

        if (isSnowyCommunity(creature)) {
            addInitial(container, Items.COD, between(creature, 1, 3));
            addInitial(container, Items.RABBIT, between(creature, 1, 2));
            addInitial(container, Items.MUTTON, between(creature, 1, 2));
            addInitial(container, Items.STRING, between(creature, 1, 3));
            addInitial(container, Items.SPRUCE_LOG, between(creature, 2, 4));
            return;
        }

        if (isDesertCommunity(creature)) {
            addInitial(container, Items.RABBIT, between(creature, 2, 4));
            addInitial(container, Items.COD, between(creature, 1, 2));
            addInitial(container, Items.DEAD_BUSH, between(creature, 1, 2));
            addInitial(container, Items.STRING, between(creature, 1, 3));
            return;
        }

        if (isBadlandsCommunity(creature)) {
            addInitial(container, Items.BREAD, between(creature, 2, 4));
            addInitial(container, Items.RABBIT, between(creature, 1, 3));
            addInitial(container, Items.RAW_GOLD, between(creature, 1, 2));
            addInitial(container, Items.RAIL, between(creature, 2, 5));
            return;
        }

        HunterFaction faction = HunterFaction.of(creature);
        if (faction == HunterFaction.AQUATIC
                || HunterArchetype.of(creature) == HunterArchetype.AQUATIC) {
            addInitial(container, Items.COD, between(creature, 2, 4));
            addInitial(container, Items.SALMON, between(creature, 1, 3));
            if (offshoreAquatic) {
                addInitial(container, Items.TROPICAL_FISH, between(creature, 1, 2));
                addInitial(container, Items.INK_SAC, between(creature, 1, 3));
            }
            addInitial(container, Items.KELP, between(creature, 3, 6));
            addInitial(container, Items.STRING, between(creature, 1, 2));
            return;
        }

        Item orange = ForgeRegistries.ITEMS.getValue(
                ResourceLocation.fromNamespaceAndPath("changed", "orange"));
        addInitial(container, orange, between(creature,
                faction == HunterFaction.DARK ? 2 : 3,
                faction == HunterFaction.DARK ? 4 : 5));
        if (faction == HunterFaction.DARK) {
            addInitial(container, Items.SWEET_BERRIES, between(creature, 2, 4));
            addInitial(container, Items.STRING, between(creature, 2, 3));
            addInitial(container, Items.COAL, between(creature, 1, 3));
        } else {
            addInitial(container, Items.APPLE, between(creature, 1, 2));
            addInitial(container, Items.WHEAT_SEEDS, between(creature, 2, 4));
            addInitial(container, Items.STICK, between(creature, 3, 5));
        }
    }

    private static void recordContainerTradeStock(
            ChangedEntity creature,
            Container container) {
        for (int slot = 0; slot < container.getContainerSize(); slot++) {
            ItemStack stack = container.getItem(slot);
            if (!stack.isEmpty()) {
                CreatureCommunityData.recordTradeDeposit(
                        creature, stack, stack.getCount());
            }
        }
    }

    private static void addInitial(
            Container container,
            @Nullable Item item,
            int count) {
        if (item == null || item == Items.AIR || count <= 0) {
            return;
        }
        insert(container, new ItemStack(item, count));
    }

    private static int between(
            ChangedEntity creature,
            int minimum,
            int maximum) {
        return minimum + creature.getRandom().nextInt(maximum - minimum + 1);
    }

    private static void decorateCache(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos cache,
            Blueprint blueprint,
            boolean hasOrangePile) {
        level.getEntitiesOfClass(
                        ItemFrame.class,
                        new AABB(cache).inflate(7.0D),
                        frame -> frame.getPersistentData()
                                .getBoolean(CACHE_DECORATION_ENTITY))
                .forEach(ItemFrame::discard);
        level.getEntitiesOfClass(
                        MinecartChest.class,
                        new AABB(cache).inflate(7.0D),
                        cart -> cart.getPersistentData()
                                .getBoolean(CACHE_DECORATIVE_MINECART))
                .forEach(MinecartChest::discard);

        updateInitialOrangePile(level, cache.above(), hasOrangePile);

        BlockPos base = cache.subtract(blueprint.cacheOffset());
        for (Display display : blueprint.displays()) {
            Item item = ForgeRegistries.ITEMS.getValue(display.item());
            if (item == null || item == Items.AIR) {
                continue;
            }
            BlockPos position = base.offset(display.offset());
            if (!level.hasChunkAt(position)
                    || !level.getBlockState(position).canBeReplaced()) {
                continue;
            }
            ItemFrame frame = new ItemFrame(level, position, display.facing());
            frame.setInvisible(true);
            frame.setInvulnerable(false);
            frame.setItem(new ItemStack(item), false);
            frame.setRotation(display.rotation());
            CompoundTag frameTag = new CompoundTag();
            frame.saveWithoutId(frameTag);
            frameTag.remove("Fixed");
            frame.load(frameTag);
            frame.getPersistentData().putBoolean(CACHE_DECORATION_ENTITY, true);
            if (frame.survives()) {
                level.addFreshEntity(frame);
            }
        }
        if (BADLANDS_CACHE_BLUEPRINT.equals(blueprint.id())) {
            placeBadlandsDisplayMinecart(level, base);
        }
    }

    /**
     * The badlands prop is a real empty chest minecart on the blueprint rail,
     * not an item-frame icon. Its marker permanently excludes it from the
     * provisioner loot search even if a player later puts an item inside.
     */
    private static void placeBadlandsDisplayMinecart(
            ServerLevel level,
            BlockPos base) {
        BlockPos rail = base.offset(-2, 0, 0);
        if (!level.hasChunkAt(rail)
                || !level.getBlockState(rail).is(BlockTags.RAILS)) {
            return;
        }
        MinecartChest cart = new MinecartChest(
                level,
                rail.getX() + 0.5D,
                rail.getY() + 0.0625D,
                rail.getZ() + 0.5D);
        cart.setInvulnerable(true);
        cart.setDeltaMovement(Vec3.ZERO);
        cart.getPersistentData().putBoolean(
                CACHE_DECORATION_ENTITY, true);
        cart.getPersistentData().putBoolean(
                CACHE_DECORATIVE_MINECART, true);
        level.addFreshEntity(cart);
    }

    private static boolean supportsOrangePile(
            ServerLevel level,
            BlockPos cache,
            Blueprint blueprint) {
        // Claimed structures keep a tangible communal food pile. Outdoor
        // light caches still mirror whether oranges grow in the local biome.
        return DARK_CACHE_BLUEPRINT.equals(blueprint.id())
                || RUIN_CACHE_BLUEPRINT.equals(blueprint.id())
                || DARK_RUIN_CACHE_BLUEPRINT.equals(blueprint.id())
                || WHITE_RUIN_CACHE_BLUEPRINT.equals(blueprint.id())
                || AQUATIC_RUIN_CACHE_BLUEPRINT.equals(blueprint.id())
                || BEE_HIVE_CACHE_BLUEPRINT.equals(blueprint.id())
                || LIGHT_CACHE_BLUEPRINT.equals(blueprint.id())
                        && level.getBiome(cache).is(HAS_ORANGE_TREE);
    }

    private static boolean cacheHasOrangePile(
            ServerLevel level,
            BlockPos cache) {
        BlockEntity blockEntity = level.hasChunkAt(cache)
                ? level.getBlockEntity(cache) : null;
        return blockEntity instanceof Container
                && blockEntity.getPersistentData()
                        .getBoolean(CACHE_HAS_ORANGE_PILE);
    }

    private static boolean isOrangePile(BlockState state) {
        return state.is(ChangedBlocks.DROPPED_ORANGE.get())
                && state.hasProperty(DroppedOrange.ORANGES);
    }

    private static void updateInitialOrangePile(
            ServerLevel level,
            BlockPos position,
            boolean shouldExist) {
        BlockState current = level.getBlockState(position);
        if (!shouldExist) {
            if (isOrangePile(current)) {
                BlockState replacement = current.getValue(DroppedOrange.WATERLOGGED)
                        ? Blocks.WATER.defaultBlockState()
                        : Blocks.AIR.defaultBlockState();
                level.setBlock(position, replacement, Block.UPDATE_ALL);
            }
            return;
        }
        if (isOrangePile(current) || !current.canBeReplaced()) {
            return;
        }
        BlockState pile = ChangedBlocks.DROPPED_ORANGE.get()
                .defaultBlockState()
                .setValue(DroppedOrange.ORANGES, 3)
                .setValue(
                        DroppedOrange.WATERLOGGED,
                        level.getFluidState(position).is(FluidTags.WATER));
        level.setBlock(position, pile, Block.UPDATE_ALL);
    }

    /**
     * Replaces only cells belonging to the former symmetric cache template.
     * The barrel, its inventory and unrelated terrain are deliberately left
     * untouched, so old worlds can adopt the new community-specific style.
     */
    private static void migrateCacheLayout(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos cache,
            Blueprint blueprint) {
        BlockPos base = cache.subtract(blueprint.cacheOffset());
        for (BlockPos offset : LEGACY_CACHE_OFFSETS) {
            BlockPos position = cache.offset(offset);
            if (isLegacyCacheDecoration(level.getBlockState(position))) {
                level.setBlock(position, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        blueprint.blocks().stream()
                .sorted(Comparator.comparingInt(cell -> cell.offset().getY()))
                .forEach(cell -> {
                    BlockPos position = base.offset(cell.offset());
                    if (position.equals(cache)) {
                        return;
                    }
                    BlockState current = level.getBlockState(position);
                    if (current.canBeReplaced()
                            || isLegacyCacheDecoration(current)
                            || current.equals(cell.state())) {
                        placeBlueprintCell(level, position, cell);
                    }
                });
    }

    private static boolean isLegacyCacheDecoration(BlockState state) {
        Block block = state.getBlock();
        if (block == Blocks.BLACK_CARPET || block == Blocks.PURPLE_CARPET
                || block == Blocks.CYAN_CARPET || block == Blocks.LIGHT_BLUE_CARPET
                || block == Blocks.MOSS_CARPET || block == Blocks.YELLOW_CARPET
                || block == Blocks.LANTERN || block == Blocks.SOUL_LANTERN
                || block == Blocks.POTTED_DANDELION
                || block == Blocks.POLISHED_BLACKSTONE
                || block == Blocks.COAL_BLOCK
                || block == Blocks.CRIMSON_PLANKS) {
            return true;
        }
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        return id != null && "changed".equals(id.getNamespace())
                && id.getPath().endsWith("_pillow");
    }

    public static BlockState localLeaves(
            ServerLevel level,
            BlockPos position) {
        Block nearestLeaves = null;
        double nearestDistance = Double.MAX_VALUE;
        for (BlockPos candidate : BlockPos.betweenClosed(
                position.offset(-4, -6, -4),
                position.offset(4, 6, 4))) {
            Block leaves = leavesForLog(level.getBlockState(candidate).getBlock());
            if (leaves == null) {
                continue;
            }
            double distance = candidate.distSqr(position);
            if (distance < nearestDistance) {
                nearestLeaves = leaves;
                nearestDistance = distance;
            }
        }
        BlockState replacement = (nearestLeaves == null
                ? Blocks.OAK_LEAVES : nearestLeaves).defaultBlockState();
        if (replacement.hasProperty(LeavesBlock.PERSISTENT)) {
            replacement = replacement.setValue(LeavesBlock.PERSISTENT, true);
        }
        BlockState harvested = level.getBlockState(position);
        if (replacement.hasProperty(BlockStateProperties.WATERLOGGED)
                && harvested.hasProperty(BlockStateProperties.WATERLOGGED)) {
            replacement = replacement.setValue(
                    BlockStateProperties.WATERLOGGED,
                    harvested.getValue(BlockStateProperties.WATERLOGGED));
        }
        return replacement;
    }

    @Nullable
    private static Block leavesForLog(Block block) {
        if (block == Blocks.OAK_LOG || block == Blocks.OAK_WOOD
                || block == Blocks.STRIPPED_OAK_LOG
                || block == Blocks.STRIPPED_OAK_WOOD) {
            return Blocks.OAK_LEAVES;
        }
        if (block == Blocks.BIRCH_LOG || block == Blocks.BIRCH_WOOD
                || block == Blocks.STRIPPED_BIRCH_LOG
                || block == Blocks.STRIPPED_BIRCH_WOOD) {
            return Blocks.BIRCH_LEAVES;
        }
        if (block == Blocks.SPRUCE_LOG || block == Blocks.SPRUCE_WOOD
                || block == Blocks.STRIPPED_SPRUCE_LOG
                || block == Blocks.STRIPPED_SPRUCE_WOOD) {
            return Blocks.SPRUCE_LEAVES;
        }
        if (block == Blocks.JUNGLE_LOG || block == Blocks.JUNGLE_WOOD
                || block == Blocks.STRIPPED_JUNGLE_LOG
                || block == Blocks.STRIPPED_JUNGLE_WOOD) {
            return Blocks.JUNGLE_LEAVES;
        }
        if (block == Blocks.ACACIA_LOG || block == Blocks.ACACIA_WOOD
                || block == Blocks.STRIPPED_ACACIA_LOG
                || block == Blocks.STRIPPED_ACACIA_WOOD) {
            return Blocks.ACACIA_LEAVES;
        }
        if (block == Blocks.DARK_OAK_LOG || block == Blocks.DARK_OAK_WOOD
                || block == Blocks.STRIPPED_DARK_OAK_LOG
                || block == Blocks.STRIPPED_DARK_OAK_WOOD) {
            return Blocks.DARK_OAK_LEAVES;
        }
        if (block == Blocks.MANGROVE_LOG || block == Blocks.MANGROVE_WOOD
                || block == Blocks.STRIPPED_MANGROVE_LOG
                || block == Blocks.STRIPPED_MANGROVE_WOOD) {
            return Blocks.MANGROVE_LEAVES;
        }
        if (block == Blocks.CHERRY_LOG || block == Blocks.CHERRY_WOOD
                || block == Blocks.STRIPPED_CHERRY_LOG
                || block == Blocks.STRIPPED_CHERRY_WOOD) {
            return Blocks.CHERRY_LEAVES;
        }
        return null;
    }

    private static Optional<BlockPos> findDryShore(
            ServerLevel level,
            BlockPos center,
            int searchRadius) {
        for (int radius = 1; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    BlockPos column = new BlockPos(x, center.getY(), z);
                    if (!level.hasChunkAt(column)) {
                        continue;
                    }
                    BlockPos stand = new BlockPos(
                            x,
                            level.getHeight(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    x, z),
                            z);
                    if (isOpenNaturalShore(level, stand)
                            && isNearWater(level, stand, 5)) {
                        return Optional.of(stand.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findShorePlacement(
            ServerLevel level,
            BlockPos center,
            ChangedEntity creature,
            Blueprint blueprint,
            int searchRadius,
            boolean requireOwnTerritory) {
        for (int radius = 1; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = center.getX() + dx;
                    int z = center.getZ() + dz;
                    BlockPos column = new BlockPos(x, center.getY(), z);
                    if (!level.hasChunkAt(column)) {
                        continue;
                    }
                    BlockPos base = new BlockPos(
                            x,
                            level.getHeight(
                                    Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                    x, z),
                            z);
                    if (isOpenNaturalShore(level, base)
                            && canPlace(level, base, blueprint, true)
                            && (!requireOwnTerritory
                                    || isOwnTerritory(level, base, creature))) {
                        return Optional.of(base.immutable());
                    }
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findOceanFloorPlacement(
            ServerLevel level,
            BlockPos structure,
            ChangedEntity creature,
            Blueprint blueprint,
            int searchRadius,
            boolean requireOwnTerritory) {
        for (int radius = 4; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = structure.getX() + dx;
                    int z = structure.getZ() + dz;
                    BlockPos column = new BlockPos(x, structure.getY(), z);
                    if (!level.hasChunkAt(column)) {
                        continue;
                    }
                    BlockPos base = new BlockPos(
                            x,
                            level.getHeight(Heightmap.Types.OCEAN_FLOOR, x, z),
                            z);
                    if (!level.getFluidState(base).is(FluidTags.WATER)
                            || !level.getFluidState(base.above()).is(FluidTags.WATER)
                            || !canPlace(level, base, blueprint, false)
                            || requireOwnTerritory
                                    && !isOwnTerritory(level, base, creature)) {
                        continue;
                    }
                    return Optional.of(base.immutable());
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> findPlacement(
            ServerLevel level,
            BlockPos center,
            ChangedEntity creature,
            Blueprint blueprint,
            boolean requireShore,
            int searchRadius,
            boolean requireOwnTerritory) {
        for (int radius = 2; radius <= searchRadius; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (radius > 0 && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    for (int dy = 0; dy <= CACHE_VERTICAL_SEARCH; dy++) {
                        BlockPos below = center.offset(dx, -dy, dz);
                        if (matchesSettlementHabitat(level, below, creature)
                                && canPlace(level, below, blueprint, requireShore)
                                && (!requireOwnTerritory
                                        || isOwnTerritory(level, below, creature))) {
                            return Optional.of(below);
                        }
                        if (dy > 0) {
                            BlockPos above = center.offset(dx, dy, dz);
                            if (matchesSettlementHabitat(level, above, creature)
                                    && canPlace(level, above, blueprint, requireShore)
                                    && (!requireOwnTerritory
                                            || isOwnTerritory(level, above, creature))) {
                                return Optional.of(above);
                            }
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The relaxed second placement pass may cross a regional territory edge,
     * but it must never move an underground community's outpost onto the
     * surface (or bury a surface community underground).
     */
    private static boolean matchesSettlementHabitat(
            ServerLevel level,
            BlockPos position,
            ChangedEntity creature) {
        if (HunterFaction.of(creature) != HunterFaction.LIGHT) {
            return true;
        }
        String group = LightFactionGroup.of(creature);
        if (LightFactionGroup.GENERAL.equals(group)) {
            return true;
        }
        return LightFactionGroup.CAVE.equals(group)
                == LightFactionGroup.isCaveHabitat(level, position);
    }

    private static boolean isOwnTerritory(
            ServerLevel level,
            BlockPos position,
            ChangedEntity creature) {
        HunterFaction faction = HunterFaction.of(creature);
        if (LatexTerritory.dominantFactionAt(level, position) != faction) {
            return false;
        }
        return faction != HunterFaction.LIGHT
                || LightFactionGroup.of(creature).equals(
                        LightFactionGroup.at(level, position));
    }

    private static boolean canPlace(
            ServerLevel level,
            BlockPos base,
            Blueprint blueprint,
            boolean requireShore) {
        BlockPos cache = base.offset(blueprint.cacheOffset());
        BlockPos support = cache.below();
        if (!level.hasChunkAt(base)
                || !level.getWorldBorder().isWithinBounds(base)
                || isSettlementAreaOccupied(level, cache)
                || !level.getBlockState(support)
                        .isFaceSturdy(level, support, Direction.UP)
                || requireShore && !isNearWater(level, cache, 5)
                || !level.getEntitiesOfClass(
                        LivingEntity.class,
                        new AABB(cache),
                        LivingEntity::isAlive).isEmpty()) {
            return false;
        }
        for (Cell cell : blueprint.blocks()) {
            BlockPos position = base.offset(cell.offset());
            if (!level.hasChunkAt(position)
                    || !level.getWorldBorder().isWithinBounds(position)) {
                return false;
            }
            BlockState current = level.getBlockState(position);
            BlockEntity blockEntity = level.getBlockEntity(position);
            if (blockEntity != null || !current.canBeReplaced()) {
                return false;
            }
            if (blueprint.requiresDry()
                    && !level.getFluidState(position).isEmpty()) {
                return false;
            }
            BlockState placed = cell.state();
            if (!placed.canSurvive(level, position)
                    && !hasBlueprintSupport(level, base, blueprint, cell)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isSettlementAreaOccupied(
            ServerLevel level,
            BlockPos candidateCache) {
        int minimumSpacing = ChangedSynergyConfig.COMMON
                .settlementMinimumSpacing.get();
        if (CreatureCommunityData.isCacheAreaClaimed(
                level, candidateCache, minimumSpacing)) {
            return true;
        }

        int chunkRadius = (minimumSpacing >> 4) + 1;
        int centerChunkX = candidateCache.getX() >> 4;
        int centerChunkZ = candidateCache.getZ() >> 4;
        double radiusSqr = (double) minimumSpacing * minimumSpacing;
        for (int dx = -chunkRadius; dx <= chunkRadius; dx++) {
            for (int dz = -chunkRadius; dz <= chunkRadius; dz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(
                        centerChunkX + dx, centerChunkZ + dz);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    CompoundTag persistent = blockEntity.getPersistentData();
                    if (persistent.contains(CACHE_BLUEPRINT, Tag.TAG_STRING)
                            && horizontalDistanceSqr(
                                    blockEntity.getBlockPos(), candidateCache)
                                    <= radiusSqr) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private static double horizontalDistanceSqr(
            BlockPos first,
            BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static boolean isDryStand(ServerLevel level, BlockPos stand) {
        BlockPos support = stand.below();
        BlockState feet = level.getBlockState(stand);
        BlockState head = level.getBlockState(stand.above());
        return level.hasChunkAt(stand)
                && level.getFluidState(stand).isEmpty()
                && level.getFluidState(stand.above()).isEmpty()
                && feet.getCollisionShape(level, stand).isEmpty()
                && head.getCollisionShape(level, stand.above()).isEmpty()
                && level.getBlockState(support)
                        .isFaceSturdy(level, support, Direction.UP);
    }

    /**
     * Rejects isolated dry blocks such as shipwreck masts and building roofs.
     * A shore cache needs a small, mostly level patch of natural terrain, not
     * merely one block that happens to sit above the waterline.
     */
    private static boolean isOpenNaturalShore(
            ServerLevel level,
            BlockPos center) {
        if (!isNaturalShoreGround(level.getBlockState(center.below()))) {
            return false;
        }
        int openColumns = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int x = center.getX() + dx;
                int z = center.getZ() + dz;
                BlockPos column = new BlockPos(x, center.getY(), z);
                if (!level.hasChunkAt(column)) {
                    continue;
                }
                BlockPos stand = new BlockPos(
                        x,
                        level.getHeight(
                                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                x, z),
                        z);
                if (Math.abs(stand.getY() - center.getY()) <= 1
                        && isDryStand(level, stand)
                        && isNaturalShoreGround(
                                level.getBlockState(stand.below()))) {
                    openColumns++;
                }
            }
        }
        return openColumns >= 5;
    }

    private static boolean isNaturalShoreGround(BlockState state) {
        return state.is(BlockTags.DIRT)
                || state.is(BlockTags.SAND)
                || state.is(BlockTags.BASE_STONE_OVERWORLD)
                || state.is(Blocks.GRAVEL)
                || state.is(Blocks.CLAY)
                || state.is(Blocks.MUD);
    }

    private static boolean isReachableStand(
            ChangedEntity creature,
            BlockPos stand) {
        if (creature.distanceToSqr(Vec3.atBottomCenterOf(stand)) <= 4.0D) {
            return true;
        }
        Path path = creature.getNavigation().createPath(stand, 0);
        return path != null && path.canReach();
    }

    @Nullable
    private static BlockPos findGlowBerryStand(
            ServerLevel level,
            ChangedEntity creature,
            BlockPos berries,
            int maximumReach) {
        for (int drop = 1; drop <= maximumReach; drop++) {
            for (int radius = 0; radius <= 2; radius++) {
                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        if (radius > 0
                                && Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                            continue;
                        }
                        BlockPos stand = berries.offset(dx, -drop, dz);
                        if (isDryStand(level, stand)
                                && isReachableStand(creature, stand)) {
                            return stand.immutable();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean hasRipeGlowBerries(BlockState state) {
        return (state.is(Blocks.CAVE_VINES)
                        || state.is(Blocks.CAVE_VINES_PLANT))
                && state.hasProperty(CaveVines.BERRIES)
                && state.getValue(CaveVines.BERRIES);
    }

    private static boolean isExposed(ServerLevel level, BlockPos position) {
        for (Direction direction : Direction.values()) {
            BlockPos adjacent = position.relative(direction);
            if (level.isEmptyBlock(adjacent)
                    || !level.getFluidState(adjacent).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isNearWater(
            ServerLevel level,
            BlockPos position,
            int radius) {
        for (BlockPos candidate : BlockPos.betweenClosed(
                position.offset(-radius, -2, -radius),
                position.offset(radius, 2, radius))) {
            if (level.getBlockState(candidate).is(Blocks.WATER)) {
                return true;
            }
        }
        return false;
    }

    private static void place(
            ServerLevel level,
            BlockPos base,
            Blueprint blueprint) {
        blueprint.blocks().stream()
                .sorted(Comparator.comparingInt(cell -> cell.offset().getY()))
                .forEach(cell -> placeBlueprintCell(
                        level, base.offset(cell.offset()), cell));
    }

    /** Lets upper halves and tabletop props validate against cells that the
     * same blueprint will place immediately before them. */
    private static boolean hasBlueprintSupport(
            ServerLevel level,
            BlockPos base,
            Blueprint blueprint,
            Cell cell) {
        BlockPos supportOffset = cell.offset().below();
        Optional<Cell> support = blueprint.blocks().stream()
                .filter(candidate -> candidate.offset().equals(supportOffset))
                .findFirst();
        if (support.isEmpty()) {
            return false;
        }
        BlockState placed = cell.state();
        BlockState supporting = support.get().state();
        if (supporting.is(placed.getBlock())) {
            return true;
        }
        BlockPos supportPosition = base.offset(supportOffset);
        return supporting.isFaceSturdy(level, supportPosition, Direction.UP)
                || !supporting.getCollisionShape(level, supportPosition).isEmpty();
    }

    /** Places one data-driven cell and then initializes any block entity NBT
     * declared by the blueprint (for example a dark-latex-filled tank). */
    private static void placeBlueprintCell(
            ServerLevel level,
            BlockPos position,
            Cell cell) {
        BlockState state = cell.state();
        if (!level.setBlock(position, state, Block.UPDATE_ALL)
                || cell.blockEntityData().isEmpty()) {
            return;
        }
        BlockEntity blockEntity = level.getBlockEntity(position);
        if (blockEntity == null) {
            return;
        }
        CompoundTag merged = blockEntity.saveWithoutMetadata();
        merged.merge(cell.blockEntityData().copy());
        blockEntity.load(merged);
        blockEntity.setChanged();
        level.sendBlockUpdated(position, state, state, Block.UPDATE_CLIENTS);
    }

    @Nullable
    private static Container containerAt(ServerLevel level, BlockPos position) {
        if (!level.hasChunkAt(position)) {
            return null;
        }
        return level.getBlockEntity(position) instanceof Container container
                ? container : null;
    }

    private static ItemStack insert(Container container, ItemStack original) {
        ItemStack remaining = original.copy();
        boolean changed = false;
        for (int slot = 0;
                slot < container.getContainerSize() && !remaining.isEmpty();
                slot++) {
            if (!container.canPlaceItem(slot, remaining)) {
                continue;
            }
            ItemStack existing = container.getItem(slot);
            if (existing.isEmpty()) {
                int moved = Math.min(
                        remaining.getCount(),
                        Math.min(remaining.getMaxStackSize(), container.getMaxStackSize()));
                ItemStack placed = remaining.copy();
                placed.setCount(moved);
                container.setItem(slot, placed);
                remaining.shrink(moved);
                changed = true;
                continue;
            }
            if (!ItemStack.isSameItemSameTags(existing, remaining)) {
                continue;
            }
            int space = Math.min(
                    existing.getMaxStackSize(), container.getMaxStackSize())
                    - existing.getCount();
            if (space <= 0) {
                continue;
            }
            int moved = Math.min(space, remaining.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            changed = true;
        }
        if (changed) {
            container.setChanged();
        }
        return remaining;
    }

    private static void shrinkOne(Container container, int slot) {
        ItemStack stack = container.getItem(slot);
        stack.shrink(1);
        if (stack.isEmpty()) {
            container.setItem(slot, ItemStack.EMPTY);
        }
        container.setChanged();
    }

    @Nullable
    private static ResourceKind classify(
            ChangedEntity creature,
            ItemStack stack) {
        // Cave communities provision themselves with mined materials. They do
        // not opportunistically collect food dropped nearby.
        if (isCaveCommunity(creature)) {
            return stack.is(BUILDING_MATERIALS) ? ResourceKind.MATERIAL : null;
        }
        if (RelationshipFavorService.isDedicatedDietFood(creature, stack)
                || isGeneralFood(stack)) {
            return ResourceKind.FOOD;
        }
        return stack.is(BUILDING_MATERIALS) ? ResourceKind.MATERIAL : null;
    }

    private static boolean isGeneralFood(ItemStack stack) {
        return stack.isEdible() || RelationshipFavorService.isOrange(stack);
    }

    private static ProvisionSource classifyProvisionSource(
            ChangedEntity creature,
            ItemStack stack,
            ResourceKind kind) {
        if (RelationshipFavorService.isOrange(stack)) {
            return ProvisionSource.ORANGE;
        }
        if (stack.is(Items.SWEET_BERRIES)
                && isTaigaCommunity(creature)) {
            return ProvisionSource.SWEET_BERRIES;
        }
        if (stack.is(Items.GLOW_BERRIES)) {
            return ProvisionSource.GLOW_BERRIES;
        }
        if (stack.is(Items.COD) || stack.is(Items.SALMON)
                || stack.is(Items.TROPICAL_FISH)
                || stack.is(Items.PUFFERFISH)) {
            return usesOpenOceanHarvest(creature)
                    ? ProvisionSource.OPEN_OCEAN_FISH
                    : ProvisionSource.NEARSHORE_FISH;
        }
        if (isCaveCommunity(creature) && kind == ResourceKind.MATERIAL) {
            return ProvisionSource.MINERAL;
        }
        return kind == ResourceKind.FOOD
                ? ProvisionSource.FORAGED_FOOD
                : ProvisionSource.MATERIAL;
    }

    private static void setCargo(
            ChangedEntity creature,
            ItemStack stack,
            ResourceKind kind,
            ProvisionSource source) {
        setCargo(creature, stack, kind);
        creature.getPersistentData().putString(
                LAST_PROVISION_SOURCE, source.id);
        ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null) {
            creature.getPersistentData().putString(
                    LAST_PROVISION_ITEM, itemId.toString());
        }
    }

    private static void setCargo(
            ChangedEntity creature,
            ItemStack stack,
            ResourceKind kind) {
        CompoundTag root = new CompoundTag();
        root.put(CARGO_STACK, stack.save(new CompoundTag()));
        root.putString(CARGO_KIND, kind.id);
        creature.getPersistentData().put(CARGO, root);
    }

    private static void clearCargo(ChangedEntity creature) {
        creature.getPersistentData().remove(CARGO);
    }

    private static void showDepositParticles(ServerLevel level, BlockPos position) {
        Vec3 center = Vec3.atCenterOf(position);
        level.sendParticles(
                ParticleTypes.COMPOSTER,
                center.x,
                center.y + 0.25D,
                center.z,
                7,
                0.35D,
                0.25D,
                0.35D,
                0.02D);
    }
}
