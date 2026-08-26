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
    private static final String GROUP_DATA =
            "ChangedSynergyLightReputationGroup";
    private static final String LEGACY_GROUP_DATA =
            "ChangedSynergyWildReputationGroup";
    private static final List<String> REGIONS = List.of(
            "cave", "taiga", "swamp", "jungle", "savanna",
            "desert", "badlands", "snowy", "mountain", "beach",
            "river", "ocean", "forest", "plains");

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
        int surface = serverLevel.getHeight(
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                position.getX(), position.getZ());
        boolean underground = position.getY() <= surface - 6
                && !serverLevel.canSeeSky(position.above());
        LatexTerritory.BiomePopulation population =
                LatexTerritory.naturalPopulationAt(
                        serverLevel, position, underground);
        return normalize(population == null
                ? GENERAL
                : population.populationRegion());
    }

    public static String regionForBiome(
            Level level,
            ResourceLocation biomeId) {
        if (biomeId == null) {
            return GENERAL;
        }
        return level.registryAccess().registry(Registries.BIOME)
                .flatMap(registry -> registry.getHolder(ResourceKey.create(
                        Registries.BIOME, biomeId)))
                .map(holder -> normalize(LatexTerritory.populationRegion(
                        holder, biomeId, false)))
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
