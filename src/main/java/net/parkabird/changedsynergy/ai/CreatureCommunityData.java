package net.parkabird.changedsynergy.ai;

import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.saveddata.SavedData;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.event.TerritoryContextEvents;
import net.parkabird.changedsynergy.event.TerritoryContextEvents.FacilitySnapshot;

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
    private static final String ALIASES = "Aliases";
    private static final String COMMUNITY_ID = "ChangedSynergyCommunityId";
    private static final String FACILITY_AFFINITY =
            "ChangedSynergyFacilityCommunityAffinity";
    private static final double JOIN_DISTANCE_SQR = 56.0D * 56.0D;
    private static final double WHITE_REBIND_DISTANCE_SQR = 72.0D * 72.0D;
    private static final long TOUCH_INTERVAL = 200L;
    private static final long MIN_MIGRATION_AGE = 24000L;
    private static final long MIGRATION_COOLDOWN = 48000L;
    private static final int MIGRATION_FAILURE_THRESHOLD = 6;

    private final Map<UUID, Community> communities = new LinkedHashMap<>();
    /** Retains old entity references after duplicate cache communities merge. */
    private final Map<UUID, UUID> aliases = new LinkedHashMap<>();
    /** Legacy records only need normalizing once after each world load. */
    private final Set<String> migratedDimensions = new HashSet<>();

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
            long lastMigrationTick,
            int memberCount,
            long tradeRevision) {
    }

    /** Exact community-owned item credit from generated stock or real deliveries. */
    public record TradeStock(ItemStack stack, int count) {
        public TradeStock {
            stack = stack.copyWithCount(1);
            count = Math.max(0, count);
        }
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

    /** Finds a Synergy-built community cache without treating arbitrary containers as owned. */
    public static Optional<Snapshot> snapshotAtCache(
            ServerLevel level, BlockPos position) {
        String dimension = level.dimension().location().toString();
        return get(level.getServer()).communities.values().stream()
                .filter(community -> community.cache != null
                        && community.cache.equals(position)
                        && community.dimension.equals(dimension))
                .map(Community::snapshot)
                .findFirst();
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
        creature.getPersistentData().remove(FACILITY_AFFINITY);
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
        community = data.mergeSharedCacheCommunities(community);
        creature.getPersistentData().putUUID(COMMUNITY_ID, community.id);
    }

    /**
     * Returns nearby cache records that this creature's local faction branch
     * may share. Adopting one of these caches subsequently merges the adopting
     * record into its authoritative community, so one physical outpost cannot
     * accumulate several independent member lists and resource counters.
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
                    || horizontalDistanceSqr(community.cache, position)
                            > radiusSqr) {
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

    private static double horizontalDistanceSqr(
            BlockPos first,
            BlockPos second) {
        double dx = first.getX() - second.getX();
        double dz = first.getZ() - second.getZ();
        return dx * dx + dz * dz;
    }

    private static List<BlockPos> compatibleCaches(
            ChangedEntity creature,
            ServerLevel level,
            double radiusSqr,
            boolean bounded) {
        CreatureCommunityData data = get(level.getServer());
        Community own = data.bindInternal(level, creature);
        return data.communities.values().stream()
                .filter(candidate -> candidate.cache != null)
                .filter(candidate -> candidate.faction == own.faction)
                .filter(candidate -> candidate.dimension.equals(own.dimension))
                .filter(candidate -> candidate.group.equals(own.group))
                .filter(candidate -> !bounded
                        || candidate.cache.distSqr(
                                creature.blockPosition()) <= radiusSqr)
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

    /** Adds sellable credit for community-generated stock or actual provisioner deliveries. */
    public static void recordTradeDeposit(
            ChangedEntity creature, ItemStack delivered, int amount) {
        if (!(creature.level() instanceof ServerLevel level)
                || delivered.isEmpty() || amount <= 0) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        community.addTradeStock(delivered, amount);
        community.tradeRevision++;
        data.setDirty();
    }

    public static List<TradeStock> tradeStock(ChangedEntity creature) {
        if (!(creature.level() instanceof ServerLevel level)) {
            return List.of();
        }
        Community community = get(level.getServer()).bindInternal(level, creature);
        return community.tradeStock.stream()
                .filter(entry -> entry.count > 0 && !entry.stack.isEmpty())
                .map(entry -> new TradeStock(entry.stack, entry.count))
                .toList();
    }

    public static int tradeCredit(ChangedEntity creature, ItemStack stack) {
        if (!(creature.level() instanceof ServerLevel level) || stack.isEmpty()) {
            return 0;
        }
        return get(level.getServer()).bindInternal(level, creature).tradeStock.stream()
                .filter(entry -> ItemStack.isSameItemSameTags(entry.stack, stack))
                .mapToInt(entry -> entry.count)
                .sum();
    }

    /** Reconciles delivery credit downward when cache contents were removed elsewhere. */
    public static int capTradeCredit(
            ChangedEntity creature, ItemStack stack, int maximum) {
        if (!(creature.level() instanceof ServerLevel level) || stack.isEmpty()) {
            return 0;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        TradeStockEntry entry = community.findTradeStock(stack);
        if (entry == null) {
            return 0;
        }
        int capped = Math.max(0, Math.min(entry.count, maximum));
        if (capped != entry.count) {
            entry.count = capped;
            if (capped == 0) {
                community.tradeStock.remove(entry);
            }
            community.tradeRevision++;
            data.setDirty();
        }
        return capped;
    }

    public static boolean consumeTradeCredit(
            ChangedEntity creature, ItemStack stack, int amount) {
        if (!(creature.level() instanceof ServerLevel level)
                || stack.isEmpty() || amount <= 0) {
            return false;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        TradeStockEntry entry = community.findTradeStock(stack);
        if (entry == null || entry.count < amount) {
            return false;
        }
        entry.count -= amount;
        if (entry.count == 0) {
            community.tradeStock.remove(entry);
        }
        community.tradeRevision++;
        data.setDirty();
        return true;
    }

    public static void restoreTradeCredit(
            ChangedEntity creature, ItemStack stack, int amount) {
        if (!(creature.level() instanceof ServerLevel level)
                || stack.isEmpty() || amount <= 0) {
            return;
        }
        CreatureCommunityData data = get(level.getServer());
        Community community = data.bindInternal(level, creature);
        community.addTradeStock(stack, amount);
        community.tradeRevision++;
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
        migrateLegacyCommunities(level, dimension);
        BlockPos focus = creature.blockPosition();
        UUID storedId = readCommunityId(creature);
        UUID resolvedId = resolveAlias(storedId);
        Community existing = resolvedId == null ? null : communities.get(resolvedId);
        if (existing != null
                && compatible(existing, faction, dimension, group)
                && (faction != HunterFaction.WHITE
                        || existing.center.distSqr(focus) <= WHITE_REBIND_DISTANCE_SQR)) {
            if (!existing.id.equals(storedId)) {
                creature.getPersistentData().putUUID(COMMUNITY_ID, existing.id);
            }
            touch(existing, level.getGameTime());
            if (existing.members.add(creature.getUUID())) {
                setDirty();
            }
            return existing;
        }

        Community nearest = communities.values().stream()
                .filter(candidate -> compatible(candidate, faction, dimension, group))
                // One Changed facility section is one population even when
                // its generated rooms span more than the outdoor joining
                // radius. Distance still partitions ordinary settlements.
                .filter(candidate -> group.startsWith("facility:")
                        || candidate.center.distSqr(focus) <= JOIN_DISTANCE_SQR)
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
        if (nearest.members.add(creature.getUUID())) {
            setDirty();
        }
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
        FacilitySnapshot facility = TerritoryContextEvents.facilityAt(
                level, creature.blockPosition());
        if (facility != null) {
            String remembered = creature.getPersistentData()
                    .getString(FACILITY_AFFINITY);
            String facilityPrefix = "facility:"
                    + facility.facilityCode() + ":";
            // Crossing a colour boundary during combat is displacement, not a
            // population transfer. Keep the original section until an
            // explicit routine reset; only provisional transition-zone
            // affinities are allowed to upgrade automatically.
            if (remembered.startsWith(facilityPrefix)
                    && !remembered.substring(facilityPrefix.length())
                            .startsWith("zone:")) {
                return remembered;
            }
            String section = TerritoryContextEvents.facilitySectionId(facility);
            if (section.isBlank()) {
                section = "zone:" + facility.piece().zone();
            }
            String affinity = facilityPrefix + section;
            creature.getPersistentData().putString(
                    FACILITY_AFFINITY, affinity);
            return affinity;
        }
        String rememberedFacility = creature.getPersistentData()
                .getString(FACILITY_AFFINITY);
        if (rememberedFacility.startsWith("facility:")) {
            return rememberedFacility;
        }
        if (faction == HunterFaction.WHITE
                || faction == HunterFaction.DARK
                || faction == HunterFaction.AQUATIC) {
            return faction.id();
        }
        if (faction == HunterFaction.LIGHT) {
            return lightGroupKey(LightFactionGroup.of(creature));
        }
        ResourceLocation biome = level.getBiome(creature.blockPosition())
                .unwrapKey()
                .map(key -> key.location())
                .orElse(ResourceLocation.fromNamespaceAndPath(
                        "minecraft", "unknown"));
        return faction.id() + ":" + biome;
    }

    private static String lightGroupKey(String region) {
        return HunterFaction.LIGHT.id() + ":" + LightFactionGroup.normalize(region);
    }

    /** Converts beta-1 biome keys such as light:minecraft:forest to stable regions. */
    private static String normalizeStoredGroup(
            ServerLevel level,
            Community community) {
        if (community.faction != HunterFaction.LIGHT
                || community.group.startsWith("facility:")) {
            return community.group;
        }
        String prefix = HunterFaction.LIGHT.id() + ":";
        if (!community.group.startsWith(prefix)) {
            return lightGroupKey(LightFactionGroup.GENERAL);
        }
        String value = community.group.substring(prefix.length());
        ResourceLocation biome = value.contains(":")
                ? ResourceLocation.tryParse(value) : null;
        String region = biome == null
                ? LightFactionGroup.normalize(value)
                : LightFactionGroup.regionForBiome(level, biome);
        return lightGroupKey(region);
    }

    /**
     * Performs the beta-1 community migration lazily when a dimension is live.
     * Communities that already share one real cache and stable branch become a
     * single authoritative record; aliases keep unloaded entities attached.
     */
    private void migrateLegacyCommunities(ServerLevel level, String dimension) {
        if (!migratedDimensions.add(dimension)) {
            return;
        }
        boolean changed = false;
        for (Community community : communities.values()) {
            if (!community.dimension.equals(dimension)) {
                continue;
            }
            String normalized = normalizeStoredGroup(level, community);
            if (!normalized.equals(community.group)) {
                community.group = normalized;
                changed = true;
            }
        }

        Map<CacheIdentity, Community> owners = new LinkedHashMap<>();
        for (Community community : List.copyOf(communities.values())) {
            if (!community.dimension.equals(dimension) || community.cache == null) {
                continue;
            }
            CacheIdentity key = new CacheIdentity(
                    community.faction,
                    community.dimension,
                    community.group,
                    community.cache);
            Community owner = owners.get(key);
            if (owner == null) {
                owners.put(key, community);
                continue;
            }
            if (community.createdTick < owner.createdTick) {
                mergeCommunities(community, owner);
                owners.put(key, community);
            } else {
                mergeCommunities(owner, community);
            }
            changed = true;
        }
        if (changed) {
            setDirty();
        }
    }

    private Community mergeSharedCacheCommunities(Community community) {
        if (community.cache == null) {
            return community;
        }
        Community owner = communities.values().stream()
                .filter(candidate -> candidate.cache != null)
                .filter(candidate -> candidate.faction == community.faction)
                .filter(candidate -> candidate.dimension.equals(community.dimension))
                .filter(candidate -> candidate.group.equals(community.group))
                .filter(candidate -> candidate.cache.equals(community.cache))
                .min(Comparator.comparingLong(candidate -> candidate.createdTick))
                .orElse(community);
        for (Community duplicate : List.copyOf(communities.values())) {
            if (duplicate != owner
                    && duplicate.cache != null
                    && duplicate.faction == owner.faction
                    && duplicate.dimension.equals(owner.dimension)
                    && duplicate.group.equals(owner.group)
                    && duplicate.cache.equals(owner.cache)) {
                mergeCommunities(owner, duplicate);
            }
        }
        return owner;
    }

    private void mergeCommunities(Community owner, Community duplicate) {
        if (owner == duplicate || !communities.containsKey(duplicate.id)) {
            return;
        }
        owner.foodDelivered = saturatedAdd(
                owner.foodDelivered, duplicate.foodDelivered);
        owner.orangeStock = saturatedAdd(owner.orangeStock, duplicate.orangeStock);
        owner.materialsDelivered = saturatedAdd(
                owner.materialsDelivered, duplicate.materialsDelivered);
        duplicate.tradeStock.forEach(entry ->
                owner.addTradeStock(entry.stack, entry.count));
        owner.members.addAll(duplicate.members);
        owner.tradeRevision = Math.max(owner.tradeRevision, duplicate.tradeRevision) + 1L;
        owner.forageFailures = Math.max(
                owner.forageFailures, duplicate.forageFailures);
        owner.migrations = saturatedAdd(owner.migrations, duplicate.migrations);
        owner.lastSeenTick = Math.max(owner.lastSeenTick, duplicate.lastSeenTick);
        owner.lastFailureTick = Math.max(
                owner.lastFailureTick, duplicate.lastFailureTick);
        owner.lastMigrationTick = Math.max(
                owner.lastMigrationTick, duplicate.lastMigrationTick);
        aliases.replaceAll((old, target) -> target.equals(duplicate.id)
                ? owner.id : target);
        aliases.put(duplicate.id, owner.id);
        communities.remove(duplicate.id);
    }

    private static int saturatedAdd(int first, int second) {
        long total = (long)first + second;
        return (int)Math.min(Integer.MAX_VALUE, Math.max(0L, total));
    }

    @Nullable
    private UUID resolveAlias(@Nullable UUID id) {
        UUID resolved = id;
        for (int depth = 0; resolved != null && depth < 32; depth++) {
            UUID next = aliases.get(resolved);
            if (next == null || next.equals(resolved)) {
                return resolved;
            }
            resolved = next;
        }
        return resolved;
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
        if (tag.contains(ALIASES, Tag.TAG_LIST)) {
            ListTag aliases = tag.getList(ALIASES, Tag.TAG_COMPOUND);
            for (int i = 0; i < aliases.size(); i++) {
                CompoundTag alias = aliases.getCompound(i);
                if (alias.hasUUID("Old") && alias.hasUUID("New")) {
                    data.aliases.put(alias.getUUID("Old"), alias.getUUID("New"));
                }
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag records = new ListTag();
        communities.values().forEach(community -> records.add(community.save()));
        tag.put(RECORDS, records);
        ListTag savedAliases = new ListTag();
        aliases.forEach((oldId, newId) -> {
            CompoundTag alias = new CompoundTag();
            alias.putUUID("Old", oldId);
            alias.putUUID("New", newId);
            savedAliases.add(alias);
        });
        tag.put(ALIASES, savedAliases);
        return tag;
    }

    private record CacheIdentity(
            HunterFaction faction,
            String dimension,
            String group,
            BlockPos cache) {
    }

    private static final class Community {
        private final UUID id;
        private final HunterFaction faction;
        private final String dimension;
        private String group;
        private BlockPos center;
        @Nullable
        private BlockPos cache;
        private int foodDelivered;
        private int orangeStock;
        private int materialsDelivered;
        private final List<TradeStockEntry> tradeStock = new java.util.ArrayList<>();
        private final Set<UUID> members = new HashSet<>();
        private long tradeRevision;
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
                    lastMigrationTick,
                    members.size(),
                    tradeRevision);
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
            tag.putLong("TradeRevision", tradeRevision);
            ListTag stock = new ListTag();
            for (TradeStockEntry entry : tradeStock) {
                if (entry.count <= 0 || entry.stack.isEmpty()) {
                    continue;
                }
                CompoundTag saved = new CompoundTag();
                saved.put("Stack", entry.stack.copyWithCount(1).save(new CompoundTag()));
                saved.putInt("Count", entry.count);
                stock.add(saved);
            }
            tag.put("TradeStock", stock);
            ListTag savedMembers = new ListTag();
            for (UUID member : members) {
                CompoundTag saved = new CompoundTag();
                saved.putUUID("Id", member);
                savedMembers.add(saved);
            }
            tag.put("Members", savedMembers);
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
            community.tradeRevision = tag.getLong("TradeRevision");
            if (tag.contains("TradeStock", Tag.TAG_LIST)) {
                ListTag stock = tag.getList("TradeStock", Tag.TAG_COMPOUND);
                for (int i = 0; i < stock.size(); i++) {
                    CompoundTag saved = stock.getCompound(i);
                    ItemStack stack = ItemStack.of(saved.getCompound("Stack"));
                    int count = saved.getInt("Count");
                    if (!stack.isEmpty() && count > 0) {
                        community.addTradeStock(stack, count);
                    }
                }
            }
            if (tag.contains("Members", Tag.TAG_LIST)) {
                ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
                for (int i = 0; i < members.size(); i++) {
                    CompoundTag saved = members.getCompound(i);
                    if (saved.hasUUID("Id")) {
                        community.members.add(saved.getUUID("Id"));
                    }
                }
            }
            return community;
        }

        @Nullable
        private TradeStockEntry findTradeStock(ItemStack stack) {
            return tradeStock.stream()
                    .filter(entry -> ItemStack.isSameItemSameTags(entry.stack, stack))
                    .findFirst()
                    .orElse(null);
        }

        private void addTradeStock(ItemStack stack, int amount) {
            if (stack.isEmpty() || amount <= 0) {
                return;
            }
            TradeStockEntry entry = findTradeStock(stack);
            if (entry == null) {
                tradeStock.add(new TradeStockEntry(stack.copyWithCount(1), amount));
            } else {
                entry.count = saturatedAdd(entry.count, amount);
            }
        }

    }

    private static final class TradeStockEntry {
        private final ItemStack stack;
        private int count;

        private TradeStockEntry(ItemStack stack, int count) {
            this.stack = stack;
            this.count = Math.max(0, count);
        }
    }
}
