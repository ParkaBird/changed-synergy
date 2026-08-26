package net.parkabird.changedsynergy.dialogue;

import java.util.EnumMap;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedFacilityZones;
import net.ltxprogrammer.changed.world.data.ChangedGameDataAccessor;
import net.ltxprogrammer.changed.world.features.structures.facility.FacilityZoneEntities;
import net.ltxprogrammer.changed.world.features.structures.facility.Zone;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.parkabird.changedsynergy.ai.HunterFaction;

/** Local environmental context used by white and dark latex encounter dialogue. */
public enum LatexTerritory {
    HOME("home"),
    AWAY("away");

    private static final int HORIZONTAL_RADIUS = 6;
    private static final int VERTICAL_RADIUS = 3;
    private static final int MIN_HOME_BLOCKS = 6;
    private static final int DOMINANCE_MARGIN = 2;
    private static final long MIN_ZONE_SPAWN_SCORE = 500L;
    private static final long SPAWN_DOMINANCE_MARGIN = 100L;
    private static final long MIN_BIOME_SPAWN_SCORE = 20L;
    private static final long BIOME_DOMINANCE_MARGIN = 5L;
    private static final TagKey<Block> WHITE_TERRITORY_BLOCKS = blockTag("white_territory_blocks");
    private static final TagKey<Block> DARK_TERRITORY_BLOCKS = blockTag("dark_territory_blocks");
    private static final TagKey<EntityType<?>> WHITE_TERRITORY_SPAWNS =
            entityTypeTag("white_territory_spawns");
    private static final TagKey<EntityType<?>> DARK_TERRITORY_SPAWNS =
            entityTypeTag("dark_territory_spawns");
    private static final ResourceLocation WHITE_LATEX_FOREST =
            ResourceLocation.fromNamespaceAndPath(
                    "changed", "white_latex_forest");
    private static final ResourceLocation DARK_LATEX_PLAINS =
            ResourceLocation.fromNamespaceAndPath(
                    "changed", "dark_latex_plains");
    private static final TagKey<Biome> TAIGA = biomeTag(
            "minecraft", "is_taiga");
    private static final TagKey<Biome> SWAMP = biomeTag(
            "forge", "is_swamp");
    private static final TagKey<Biome> JUNGLE = biomeTag(
            "minecraft", "is_jungle");
    private static final TagKey<Biome> SAVANNA = biomeTag(
            "minecraft", "is_savanna");
    private static final TagKey<Biome> DESERT = biomeTag(
            "forge", "is_desert");
    private static final TagKey<Biome> BADLANDS = biomeTag(
            "minecraft", "is_badlands");
    private static final TagKey<Biome> MOUNTAIN = biomeTag(
            "minecraft", "is_mountain");
    private static final TagKey<Biome> OCEAN = biomeTag(
            "minecraft", "is_ocean");
    private static final TagKey<Biome> RIVER = biomeTag(
            "minecraft", "is_river");
    private static final TagKey<Biome> FOREST = biomeTag(
            "minecraft", "is_forest");
    private static final TagKey<Biome> PLAINS = biomeTag(
            "forge", "is_plains");
    /** Biomes in which Synergy deliberately permits no natural latex fauna. */
    public static final TagKey<Biome> LATEX_FREE = biomeTag(
            "changed_synergy", "latex_free");

    private final String suffix;

    LatexTerritory(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() {
        return suffix;
    }

    public static LatexTerritory around(ChangedEntity speaker, HunterFaction faction) {
        if (faction != HunterFaction.WHITE && faction != HunterFaction.DARK) {
            return AWAY;
        }

        if (speaker.level() instanceof ServerLevel level
                && dominantFactionAt(level, speaker.blockPosition()) == faction) {
            return HOME;
        }
        return AWAY;
    }

    /** Small loaded-area sample used for territory-edge social responses. */
    public static boolean isInOrNearFactionTerritory(
            ServerLevel level,
            BlockPos origin,
            HunterFaction faction,
            int radius) {
        if (faction == null || radius < 0) {
            return false;
        }
        int diagonal = Math.max(1, (int)Math.round(radius * 0.7071D));
        int[][] offsets = {
            {0, 0}, {radius, 0}, {-radius, 0}, {0, radius}, {0, -radius},
            {diagonal, diagonal}, {diagonal, -diagonal},
            {-diagonal, diagonal}, {-diagonal, -diagonal}
        };
        for (int[] offset : offsets) {
            BlockPos sample = origin.offset(offset[0], 0, offset[1]);
            if (level.hasChunkAt(sample)
                    && dominantFactionAt(level, sample) == faction) {
                return true;
            }
        }
        return false;
    }

    /** Returns the faction whose local blocks, facility spawns or biome spawns dominate. */
    @Nullable
    public static HunterFaction dominantFactionAt(
            ServerLevel level,
            BlockPos origin) {
        HunterFaction facilityFaction = facilityFactionAt(level, origin);
        if (facilityFaction != null) {
            return facilityFaction;
        }

        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int whiteBlocks = 0;
        int darkBlocks = 0;

        for (int y = -VERTICAL_RADIUS; y <= VERTICAL_RADIUS; y++) {
            for (int x = -HORIZONTAL_RADIUS; x <= HORIZONTAL_RADIUS; x++) {
                for (int z = -HORIZONTAL_RADIUS; z <= HORIZONTAL_RADIUS; z++) {
                    cursor.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    if (!level.hasChunkAt(cursor)) {
                        continue;
                    }

                    BlockState state = level.getBlockState(cursor);
                    if (state.is(WHITE_TERRITORY_BLOCKS)) {
                        whiteBlocks++;
                    }
                    if (state.is(DARK_TERRITORY_BLOCKS)) {
                        darkBlocks++;
                    }
                }
            }
        }

        if (whiteBlocks >= MIN_HOME_BLOCKS
                && whiteBlocks >= darkBlocks + DOMINANCE_MARGIN) {
            return HunterFaction.WHITE;
        }
        if (darkBlocks >= MIN_HOME_BLOCKS
                && darkBlocks >= whiteBlocks + DOMINANCE_MARGIN) {
            return HunterFaction.DARK;
        }
        return dominantBiomeFaction(level, origin);
    }

    @Nullable
    public static HunterFaction facilityFactionAt(
            ServerLevel level,
            BlockPos position) {
        if (!(level instanceof ChangedGameDataAccessor accessor)) {
            return null;
        }

        return accessor.getChangedGameData().facilities.stream()
                .flatMap(facility -> facility.getPieceGenerationInfos().stream())
                .filter(piece -> piece.region().isInside(position))
                .map(piece -> dominantFaction(piece.zone()))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    @Nullable
    public static HunterFaction dominantFaction(Zone zone) {
        if (zone == ChangedFacilityZones.WHITE_LATEX_ZONE.get()) {
            return HunterFaction.WHITE;
        }
        if (zone == ChangedFacilityZones.DARK_LATEX_ZONE.get()) {
            return HunterFaction.DARK;
        }

        var spawnDefinitions = FacilityZoneEntities.INSTANCE.getSpawns(zone);
        if (spawnDefinitions == null || spawnDefinitions.isEmpty()) {
            return null;
        }

        EnumMap<HunterFaction, Long> scores = new EnumMap<>(HunterFaction.class);
        for (HunterFaction faction : HunterFaction.values()) {
            scores.put(faction, 0L);
        }
        for (FacilityZoneEntities.ZoneEntitiesDefinition definition : spawnDefinitions) {
            for (FacilityZoneEntities.EntitySpawnDefinition spawn : definition.spawns()) {
                HunterFaction faction = HunterFaction.of(spawn.entityType());
                if (faction != null) {
                    scores.merge(
                            faction,
                            (long)Math.max(0, spawn.weight())
                                    * Math.min(10, Math.max(0, spawn.maximum())),
                            Long::sum);
                }
            }
        }
        return dominantScore(
                scores,
                MIN_ZONE_SPAWN_SCORE,
                SPAWN_DOMINANCE_MARGIN);
    }

    @Nullable
    private static HunterFaction dominantBiomeFaction(
            ServerLevel level,
            BlockPos position) {
        EnumMap<HunterFaction, Long> scores = new EnumMap<>(HunterFaction.class);
        for (HunterFaction faction : HunterFaction.values()) {
            scores.put(faction, 0L);
        }
        var mobSettings = level.getBiome(position).value().getMobSettings();
        for (MobCategory category : MobCategory.values()) {
            for (var spawn : mobSettings.getMobs(category).unwrap()) {
                HunterFaction faction = HunterFaction.of(spawn.type);
                if (faction != null) {
                    scores.merge(
                            faction,
                            (long)spawn.getWeight().asInt()
                                    * Math.max(1, spawn.maxCount),
                            Long::sum);
                }
            }
        }
        return dominantScore(
                scores,
                MIN_BIOME_SPAWN_SCORE,
                BIOME_DOMINANCE_MARGIN);
    }

    /**
     * Describes the naturally spawning Changed population at the player's
     * current biome layer. Region names come from the biome environment rather
     * than the highest-weight entity: Changed's cave spawn modifier is attached
     * to every overworld biome and would otherwise label everything after the
     * traffic-cone dragon.
     */
    @Nullable
    public static BiomePopulation naturalPopulationAt(
            ServerLevel level,
            BlockPos position,
            boolean underground) {
        Holder<Biome> biome = level.getBiome(position);
        ResourceLocation biomeId = biome.unwrapKey()
                .map(ResourceKey::location)
                .orElseGet(() -> level.registryAccess()
                        .registryOrThrow(Registries.BIOME)
                        .getKey(biome.value()));
        String populationRegion =
                populationRegion(biome, biomeId, underground);

        // A biome explicitly known to have no viable latex population stays a
        // single generic latex-free area. Changed's global cave modifier adds
        // entries to the raw table even in Mushroom Fields, but those entries
        // are not a meaningful local population and must not manufacture a
        // biome-specific territory label.
        if ("none".equals(populationRegion)) {
            return new BiomePopulation(null, "none");
        }

        EnumMap<HunterFaction, Long> scores = new EnumMap<>(HunterFaction.class);
        for (HunterFaction faction : HunterFaction.values()) {
            scores.put(faction, 0L);
        }
        var mobSettings = biome.value().getMobSettings();
        for (MobCategory category : MobCategory.values()) {
            for (var spawn : mobSettings.getMobs(category).unwrap()) {
                HunterFaction faction = HunterFaction.of(spawn.type);
                long score = (long)spawn.getWeight().asInt()
                        * Math.max(1, spawn.maxCount);
                if (faction == null || score <= 0L) {
                    continue;
                }
                scores.merge(faction, score, Long::sum);
            }
        }
        HunterFaction faction = dominantScore(scores, 1L, 0L);
        if (faction == null) {
            return new BiomePopulation(null, "none");
        }
        long activeFactions = scores.values().stream()
                .filter(score -> score > 0L)
                .count();
        if (WHITE_LATEX_FOREST.equals(biomeId)
                || activeFactions == 1L && faction == HunterFaction.WHITE) {
            faction = HunterFaction.WHITE;
            populationRegion = "white_forest";
        } else if (DARK_LATEX_PLAINS.equals(biomeId)
                || activeFactions == 1L && faction == HunterFaction.DARK) {
            faction = HunterFaction.DARK;
            populationRegion = "dark_plains";
        }
        return new BiomePopulation(
                faction,
                populationRegion);
    }

    public static String populationRegion(
            Holder<Biome> biome,
            @Nullable ResourceLocation biomeId,
            boolean underground) {
        String path = biomeId == null ? "" : biomeId.getPath();
        if (biome.is(LATEX_FREE)
                || path.contains("mushroom")
                || path.contains("void")) {
            return "none";
        }
        if (WHITE_LATEX_FOREST.equals(biomeId)) {
            return "white_forest";
        }
        if (DARK_LATEX_PLAINS.equals(biomeId)) {
            return "dark_plains";
        }
        if (underground) {
            return "cave";
        }

        if (biome.is(TAIGA) || path.contains("taiga")) {
            return "taiga";
        }
        if (biome.is(SWAMP) || path.contains("swamp")) {
            return "swamp";
        }
        if (biome.is(JUNGLE) || path.contains("jungle")) {
            return "jungle";
        }
        if (biome.is(SAVANNA) || path.contains("savanna")) {
            return "savanna";
        }
        if (biome.is(DESERT) || path.contains("desert")) {
            return "desert";
        }
        if (biome.is(BADLANDS) || path.contains("badlands")) {
            return "badlands";
        }
        if (path.contains("snow")
                || path.contains("frozen")
                || path.contains("ice_spikes")) {
            return "snowy";
        }
        if (biome.is(MOUNTAIN)
                || path.contains("mountain")
                || path.contains("peak")
                || path.contains("slope")
                || path.contains("windswept")
                || path.contains("grove")) {
            return "mountain";
        }
        if (path.contains("beach")) {
            return "beach";
        }
        if (biome.is(RIVER) || path.contains("river")) {
            return "river";
        }
        if (biome.is(OCEAN) || path.contains("ocean")) {
            return "ocean";
        }
        if (biome.is(FOREST) || path.contains("forest")) {
            return "forest";
        }
        if (biome.is(PLAINS) || path.contains("plains")) {
            return "plains";
        }
        return "none";
    }

    @Nullable
    private static HunterFaction dominantScore(
            EnumMap<HunterFaction, Long> scores,
            long minimum,
            long margin) {
        HunterFaction best = null;
        long bestScore = 0L;
        long secondScore = 0L;
        for (var entry : scores.entrySet()) {
            long score = entry.getValue();
            if (score > bestScore) {
                secondScore = bestScore;
                bestScore = score;
                best = entry.getKey();
            } else if (score > secondScore) {
                secondScore = score;
            }
        }
        return best != null
                        && bestScore >= minimum
                        && bestScore >= secondScore + margin
                ? best
                : null;
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK,
                ResourceLocation.fromNamespaceAndPath("changed_synergy", path));
    }

    private static TagKey<EntityType<?>> entityTypeTag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("changed_synergy", path));
    }

    private static TagKey<Biome> biomeTag(
            String namespace,
            String path) {
        return TagKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    public record BiomePopulation(
            HunterFaction faction,
            String populationRegion) {
    }
}
