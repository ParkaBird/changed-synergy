package net.parkabird.changedsynergy.ai;

import java.util.List;
import java.util.Locale;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.dialogue.LatexTerritory;

/**
 * Stable regional identity inside the broad Light-latex family. Reputation
 * and faction aid use this identity instead of one shared Light score.
 */
public final class LightFactionGroup {
    public static final String GENERAL = "general";
    public static final String CAVE = "cave";
    private static final int CAVE_MINIMUM_DEPTH = 12;
    private static final int OPENING_PROBE_RADIUS = 4;
    private static final String GROUP_DATA =
            "ChangedSynergyLightReputationGroup";
    private static final String LEGACY_GROUP_DATA =
            "ChangedSynergyWildReputationGroup";
    private static final List<String> REGIONS = FactionDiplomacy.REGIONS;

    private LightFactionGroup() {
    }

    /** Assigns once, so moving or teleporting an individual cannot change allegiance. */
    public static String of(ChangedEntity creature) {
        if (HunterFaction.of(creature) != HunterFaction.LIGHT) {
            return GENERAL;
        }
        String stored = normalize(
                creature.getPersistentData().getString(GROUP_DATA));
        if (!GENERAL.equals(stored)
                || creature.getPersistentData().contains(GROUP_DATA)) {
            return stored;
        }

        // EntityJoinLevelEvent can run while PersistentEntitySectionManager is
        // still installing the entity's chunk. Asking Level#getHeight from that
        // callback waits for the very same chunk and stalls the integrated
        // server. Leave the identity unassigned until the chunk is fully live.
        if (!canResolveAt(creature.level(), creature.blockPosition())) {
            return GENERAL;
        }

        String legacy = creature.getPersistentData().getString(
                LEGACY_GROUP_DATA);
        String migrated = regionFromLegacyBiome(creature.level(), legacy);
        String current = at(creature.level(), creature.blockPosition());
        List<String> nativeRegions = REGIONS.stream()
                .filter(region -> creature.getType().is(regionTag(region)))
                .toList();

        String resolved;
        if (!nativeRegions.isEmpty()) {
            // Organic and other explicitly mapped species use only regions in
            // their natural spawn table. Multi-region species bind to the
            // actual region in which this individual first appeared.
            resolved = nativeRegions.contains(current)
                    ? current
                    : nativeRegions.contains(migrated)
                            ? migrated
                            : nativeRegions.get(0);
        } else if (!GENERAL.equals(migrated)) {
            resolved = migrated;
        } else {
            resolved = current;
        }
        creature.getPersistentData().putString(GROUP_DATA, resolved);
        return resolved;
    }

    public static boolean isAssigned(ChangedEntity creature) {
        return creature.getPersistentData().contains(GROUP_DATA);
    }

    public static boolean canResolveAt(Level level, BlockPos position) {
        return level instanceof ServerLevel serverLevel
                && serverLevel.getChunkSource().getChunkNow(
                        position.getX() >> 4,
                        position.getZ() >> 4) != null;
    }

    /** Regional Light account used by territory HUD and environmental benefits. */
    public static String at(Level level, BlockPos position) {
        if (!(level instanceof ServerLevel serverLevel)
                || !canResolveAt(level, position)) {
            return GENERAL;
        }
        boolean underground = isCaveHabitat(serverLevel, position);
        LatexTerritory.BiomePopulation population =
                LatexTerritory.naturalPopulationAt(
                        serverLevel, position, underground);
        return normalize(population == null
                ? GENERAL
                : population.populationRegion());
    }

    /**
     * A regional cave is enclosed and meaningfully below its local surface.
     * A shallow roof, overhang, open pit or ravine should keep the surrounding
     * surface identity even when the exact spawn block cannot see the sky.
     */
    public static boolean isCaveHabitat(
            ServerLevel level,
            BlockPos position) {
        if (!canResolveAt(level, position)) {
            return false;
        }
        int surface = level.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                position.getX(), position.getZ());
        if (position.getY() > surface - CAVE_MINIMUM_DEPTH
                || level.canSeeSky(position.above())) {
            return false;
        }

        for (int dx = -OPENING_PROBE_RADIUS;
                dx <= OPENING_PROBE_RADIUS;
                dx += OPENING_PROBE_RADIUS) {
            for (int dz = -OPENING_PROBE_RADIUS;
                    dz <= OPENING_PROBE_RADIUS;
                    dz += OPENING_PROBE_RADIUS) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                BlockPos probe = position.offset(dx, 1, dz);
                if (canResolveAt(level, probe) && level.canSeeSky(probe)) {
                    return false;
                }
            }
        }
        return true;
    }

    public static String regionForBiome(
            Level level,
            ResourceLocation biomeId) {
        if (biomeId == null) {
            return GENERAL;
        }
        String path = biomeId.getPath();
        boolean underground = path.contains("cave")
                || path.equals("deep_dark");
        return level.registryAccess().registry(Registries.BIOME)
                .flatMap(registry -> registry.getHolder(ResourceKey.create(
                        Registries.BIOME, biomeId)))
                .map(holder -> normalize(LatexTerritory.populationRegion(
                        holder, biomeId, underground)))
                .orElse(GENERAL);
    }

    public static String normalize(String region) {
        if (region == null) {
            return GENERAL;
        }
        String normalized = region.trim().toLowerCase(Locale.ROOT);
        return REGIONS.contains(normalized) ? normalized : GENERAL;
    }

    public static String translationKey(String region) {
        return "faction.changed_synergy.light." + normalize(region);
    }

    private static String regionFromLegacyBiome(Level level, String key) {
        if (key == null || !key.startsWith("wild:")) {
            return GENERAL;
        }
        ResourceLocation biome = ResourceLocation.tryParse(
                key.substring("wild:".length()));
        return regionForBiome(level, biome);
    }

    private static TagKey<EntityType<?>> regionTag(String region) {
        return TagKey.create(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(
                        ChangedSynergyMod.MOD_ID,
                        "light_reputation_" + region));
    }
}
