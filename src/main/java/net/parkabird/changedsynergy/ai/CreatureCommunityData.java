package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.saveddata.SavedData;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/**
 * Server-wide memory for local creature communities.
 *
 * <p>The record belongs to a group rather than to one loaded entity. It keeps
 * resource deliveries, a shared cache and migration pressure alive across
 * chunk unloads. White latex uses the same record for local consensus, but it
 * never receives a fixed cache or home.</p>
 */
public final class CreatureCommunityData extends SavedData {
    private static final String DATA_NAME = "changed_synergy_communities";
    private static final String RECORDS = "Records";
    private static final String COMMUNITY_ID = "ChangedSynergyCommunityId";
    private static final double JOIN_DISTANCE_SQR = 56.0D * 56.0D;
    private static final double WHITE_REBIND_DISTANCE_SQR = 72.0D * 72.0D;
    private static final long TOUCH_INTERVAL = 200L;
    private static final long MIN_MIGRATION_AGE = 24000L;
    private static final long MIGRATION_COOLDOWN = 48000L;
    private static final int MIGRATION_FAILURE_THRESHOLD = 6;

    private final Map<UUID, Community> communities = new LinkedHashMap<>();

    public record Snapshot(
            UUID id,
            HunterFaction faction,
            String dimension,
            String group,
            BlockPos center,
            Optional<BlockPos> cache,
            int foodDelivered,
            int materialsDelivered,
            int forageFailures,
            int migrations,
            long createdTick,
            long lastSeenTick,
            long lastMigrationTick) {
    }

    private CreatureCommunityData() {
    }

    public static CreatureCommunityData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                CreatureCommunityData::load,
                CreatureCommunityData::new,
                DATA_NAME);
    }

    public static Optional<Snapshot> bind(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || !LatexSocialMemory.isSocialLatex(creature)
                || !ChangedSynergyGameRules.enabled(
                        level, ChangedSynergyGameRules.CREATURE_LIFE)) {
            return Optional.empty();
        }
        return Optional.of(get(level.getServer()).bindInternal(level, creature).snapshot());
    }

    public static Optional<Snapshot> snapshot(ChangedEntity creature) {
        return bind(creature);
    }

    public static boolean sameCommunity(
            ChangedEntity first,
            ChangedEntity second) {
        Optional<Snapshot> left = snapshot(first);
        Optional<Snapshot> right = snapshot(second);
        return left.isPresent()
                && right.isPresent()
                && left.get().id().equals(right.get().id());
    }

    public static void detach(ChangedEntity creature) {
        creature.getPersistentData().remove(COMMUNITY_ID);
    }

    public static void synchronizeActivityCenter(ChangedEntity creature) {
        // Compatibility no-op: individual activity centres were removed.
    }

    public static void markCache(ChangedEntity creature, BlockPos position) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        if (!position.equals(community.cache)) {
            community.cache = position.immutable();
            data.setDirty();
        }
    }

    /**
     * Returns nearby cache records that this creature's local faction branch
     * may share. This deliberately does not merge community identity or its
     * statistics; it only lets several neighbouring communities use one real
     * container instead of placing a row of nearly identical caches.
     */
    public static List<BlockPos> nearbyCompatibleCaches(
            ChangedEntity creature,
            double radius) {
        if (!(creature.level() instanceof ServerLevel level) || radius <= 0.0D) {
            return List.of();
        }
        return compatibleCaches(creature, level, radius * radius, true);
    }

    /**
     * Returns every known cache belonging to this creature's compatible
     * faction branch. Offshore suppliers use this unbounded view so a cache
     * already bound to their underwater structure is authoritative even when
     * another local community recorded it outside the ordinary sharing radius.
     */
    public static List<BlockPos> compatibleCaches(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return List.of();
        }
        return compatibleCaches(creature, level, 0.0D, false);
    }

    /**
     * Treats every surviving community cache as a local settlement claim,
     * regardless of faction. Unloaded claims remain authoritative; loaded
     * records whose container was actually removed are pruned here.
     */
    public static boolean isCacheAreaClaimed(
            ServerLevel level,
            BlockPos position,
            double radius) {
        if (radius <= 0.0D) {
            return false;
        }
        CreatureCommunityData data = get(level.getServer());
        String dimension = level.dimension().location().toString();
        double radiusSqr = radius * radius;
        boolean claimed = false;
        boolean changed = false;
        for (Community community : data.communities.values()) {
            if (community.cache == null
                    || !community.dimension.equals(dimension)
                    || community.cache.distSqr(position) > radiusSqr) {
                continue;
            }
            if (!level.hasChunkAt(community.cache)) {
                claimed = true;
                continue;
            }
            if (level.getBlockEntity(community.cache) instanceof Container) {
                claimed = true;
                continue;
            }
            community.cache = null;
            changed = true;
        }
        if (changed) {
            data.setDirty();
        }
        return claimed;
    }

    private static List<BlockPos> compatibleCaches(
            ChangedEntity creature,
            ServerLevel level,
            double radiusSqr,
            boolean bounded) {
        CreatureCommunityData data = get(level.getServer());
        Community own = data.bindInternal(level, creature);
        String lightGroup = own.faction == HunterFaction.LIGHT
                ? LightFactionGroup.of(creature) : "";
        return data.communities.values().stream()
                .filter(candidate -> candidate.cache != null)
                .filter(candidate -> candidate.faction == own.faction)
                .filter(candidate -> candidate.dimension.equals(own.dimension))
                .filter(candidate -> !bounded
                        || candidate.cache.distSqr(
                                creature.blockPosition()) <= radiusSqr)
                .filter(candidate -> own.faction != HunterFaction.LIGHT
                        || lightGroup.equals(LightFactionGroup.at(
                                level, candidate.center)))
                .map(candidate -> candidate.cache.immutable())
                .distinct()
                .sorted(Comparator.comparingDouble(position ->
                        position.distSqr(creature.blockPosition())))
                .toList();
    }

    public static void clearCache(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        if (community.cache != null) {
            community.cache = null;
            data.setDirty();
        }
    }

    public static void recenter(ChangedEntity creature, BlockPos center) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        community.center = center.immutable();
        community.cache = null;
        community.forageFailures = 0;
        community.lastMigrationTick = level.getGameTime();
        data.setDirty();
    }

    public static void recordDeposit(
            ChangedEntity creature,
            CreatureSettlementService.ResourceKind kind,
            int amount) {
        if (!(creature.level() instanceof ServerLevel level) || amount <= 0) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        if (kind == CreatureSettlementService.ResourceKind.FOOD) {
            community.foodDelivered += amount;
        } else {
            community.materialsDelivered += amount;
        }
        community.forageFailures = 0;
        data.setDirty();
    }

    /** Records resources dissolved into a white-latex consensus instead of a container. */
    public static void recordConsensusDeposit(
            ChangedEntity creature,
            CreatureSettlementService.ResourceKind kind,
            int amount,
            boolean orange) {
        recordDeposit(creature, kind, amount);
        if (!(creature.level() instanceof ServerLevel level)
                || amount <= 0 || !orange) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        community.orangeStock = Math.min(
                Integer.MAX_VALUE - 1,
                community.orangeStock + amount);
        data.setDirty();
    }

    public static boolean hasStoredOrange(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return false;
        }
        return get(level.getServer()).bindInternal(level, creature).orangeStock > 0;
    }

    public static boolean consumeStoredOrange(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return false;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        if (community.orangeStock <= 0) {
            return false;
        }
        community.orangeStock--;
        data.setDirty();
        return true;
    }

    public static void reportForageFailure(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        long now = level.getGameTime();
        if (now - community.lastFailureTick < 200L) {
            return;
        }
        community.lastFailureTick = now;
        community.forageFailures = Math.min(1000, community.forageFailures + 1);
        data.setDirty();
    }

    public static boolean shouldMigrate(ChangedEntity creature) {
        return false;
    }

    public static boolean migrate(ChangedEntity creature, BlockPos newCenter) {
        return false;
    }

    private Community bindInternal(ServerLevel level, ChangedEntity creature) {
        HunterFaction faction = HunterFaction.of(creature);
        String dimension = level.dimension().location().toString();
        String group = groupKey(level, creature, faction);
        BlockPos focus = creature.blockPosition();
        UUID storedId = readCommunityId(creature);
        Community existing = storedId == null ? null : communities.get(storedId);
        if (existing != null
                && compatible(existing, faction, dimension, group)
                && (faction != HunterFaction.WHITE
                        || existing.center.distSqr(focus) <= WHITE_REBIND_DISTANCE_SQR)) {
            touch(existing, level.getGameTime());
            return existing;
        }

        Community nearest = communities.values().stream()
                .filter(candidate -> compatible(candidate, faction, dimension, group))
                .filter(candidate -> candidate.center.distSqr(focus) <= JOIN_DISTANCE_SQR)
                .min(Comparator.comparingDouble(candidate ->
                        candidate.center.distSqr(focus)))
                .orElse(null);
        if (nearest == null) {
            long now = level.getGameTime();
            nearest = new Community(
                    UUID.randomUUID(),
                    faction,
                    dimension,
                    group,
                    focus.immutable(),
                    now,
                    now);
            communities.put(nearest.id, nearest);
            setDirty();
        }
        creature.getPersistentData().putUUID(COMMUNITY_ID, nearest.id);
        touch(nearest, level.getGameTime());
        return nearest;
    }

    private void touch(Community community, long now) {
        if (now - community.lastSeenTick < TOUCH_INTERVAL) {
            return;
        }
        community.lastSeenTick = now;
        setDirty();
    }

    private static boolean compatible(
            Community community,
            HunterFaction faction,
            String dimension,
            String group) {
        return community.faction == faction
                && community.dimension.equals(dimension)
                && community.group.equals(group);
    }

    private static String groupKey(
            ServerLevel level,
            ChangedEntity creature,
            HunterFaction faction) {
        if (faction == HunterFaction.WHITE || faction == HunterFaction.DARK) {
            return faction.id();
        }
        ResourceLocation biome = level.getBiome(creature.blockPosition())
                .unwrapKey()
                .map(ResourceKey::location)
                .orElseGet(() -> {
                    Registry<Biome> registry = level.registryAccess()
                            .registryOrThrow(Registries.BIOME);
                    ResourceLocation id = registry.getKey(
                            level.getBiome(creature.blockPosition()).value());
                    return id == null
                            ? ResourceLocation.fromNamespaceAndPath(
                                    "minecraft", "unknown")
                            : id;
                });
        return faction.id() + ":" + biome;
    }

    @Nullable
    private static UUID readCommunityId(ChangedEntity creature) {
        CompoundTag persistent = creature.getPersistentData();
        return persistent.hasUUID(COMMUNITY_ID)
                ? persistent.getUUID(COMMUNITY_ID) : null;
    }

    private static CreatureCommunityData load(CompoundTag tag) {
        CreatureCommunityData data = new CreatureCommunityData();
        if (!tag.contains(RECORDS, Tag.TAG_LIST)) {
            return data;
        }
        ListTag records = tag.getList(RECORDS, Tag.TAG_COMPOUND);
        for (int i = 0; i < records.size(); i++) {
            Community community = Community.load(records.getCompound(i));
            if (community != null) {
                data.communities.put(community.id, community);
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag records = new ListTag();
        communities.values().forEach(community -> records.add(community.save()));
        tag.put(RECORDS, records);
        return tag;
    }

    private static final class Community {
        private final UUID id;
        private final HunterFaction faction;
        private final String dimension;
        private final String group;
        private BlockPos center;
        @Nullable
        private BlockPos cache;
        private int foodDelivered;
        private int orangeStock;
        private int materialsDelivered;
        private int forageFailures;
        private int migrations;
        private final long createdTick;
        private long lastSeenTick;
        private long lastFailureTick;
        private long lastMigrationTick;

        private Community(
                UUID id,
                HunterFaction faction,
                String dimension,
                String group,
                BlockPos center,
                long createdTick,
                long lastSeenTick) {
            this.id = id;
            this.faction = faction;
            this.dimension = dimension;
            this.group = group;
            this.center = center;
            this.createdTick = createdTick;
            this.lastSeenTick = lastSeenTick;
            this.lastMigrationTick = createdTick - MIGRATION_COOLDOWN;
        }

        private Snapshot snapshot() {
            return new Snapshot(
                    id,
                    faction,
                    dimension,
                    group,
                    center,
                    Optional.ofNullable(cache),
                    foodDelivered,
                    materialsDelivered,
                    forageFailures,
                    migrations,
                    createdTick,
                    lastSeenTick,
                    lastMigrationTick);
        }

        private CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putUUID("Id", id);
            tag.putString("Faction", faction.id());
            tag.putString("Dimension", dimension);
            tag.putString("Group", group);
            tag.putLong("Center", center.asLong());
            if (cache != null) {
                tag.putLong("Cache", cache.asLong());
            }
            tag.putInt("FoodDelivered", foodDelivered);
            tag.putInt("OrangeStock", orangeStock);
            tag.putInt("MaterialsDelivered", materialsDelivered);
            tag.putInt("ForageFailures", forageFailures);
            tag.putInt("Migrations", migrations);
            tag.putLong("Created", createdTick);
            tag.putLong("LastSeen", lastSeenTick);
            tag.putLong("LastFailure", lastFailureTick);
            tag.putLong("LastMigration", lastMigrationTick);
            return tag;
        }

        @Nullable
        private static Community load(CompoundTag tag) {
            if (!tag.hasUUID("Id")
                    || !tag.contains("Faction", Tag.TAG_STRING)
                    || !tag.contains("Dimension", Tag.TAG_STRING)
                    || !tag.contains("Center", Tag.TAG_LONG)) {
                return null;
            }
            HunterFaction faction = HunterFaction.fromId(tag.getString("Faction"));
            if (faction == null) {
                return null;
            }
            Community community = new Community(
                    tag.getUUID("Id"),
                    faction,
                    tag.getString("Dimension"),
                    tag.getString("Group"),
                    BlockPos.of(tag.getLong("Center")),
                    tag.getLong("Created"),
                    tag.getLong("LastSeen"));
            if (tag.contains("Cache", Tag.TAG_LONG)) {
                community.cache = BlockPos.of(tag.getLong("Cache"));
            }
            community.foodDelivered = tag.getInt("FoodDelivered");
            community.orangeStock = tag.getInt("OrangeStock");
            community.materialsDelivered = tag.getInt("MaterialsDelivered");
            community.forageFailures = tag.getInt("ForageFailures");
            community.migrations = tag.getInt("Migrations");
            community.lastFailureTick = tag.getLong("LastFailure");
            community.lastMigrationTick = tag.getLong("LastMigration");
            return community;
        }
    }
}
