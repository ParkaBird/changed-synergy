package net.parkabird.changedsynergy.ai;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.entity.variant.TransfurVariantInstance;
import net.ltxprogrammer.changed.init.ChangedLatexTypes;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.ltxprogrammer.changed.init.ChangedTransfurVariants;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.registries.ForgeRegistries;

/** A more precise social relationship than the combat-oriented HunterRelation. */
public enum LatexSocialRelation {
    HUMAN,
    FORMER_BONDED,
    FORMER_RESPECTED,
    FRIEND_RESPECTED,
    SAME_SPECIES,
    SAME_CATEGORY,
    FRIENDLY_OTHER,
    OUTSIDER,
    RIVAL;

    /**
     * Biome modifiers are fully applied by the time a server world exists.
     * Keying this cache by the live biome registry also makes it naturally
     * expire when a datapack reload replaces that registry.
     */
    private static final Map<Registry<Biome>, Map<EntityType<?>, Set<ResourceKey<Biome>>>>
            NATURAL_SPAWN_BIOMES = new WeakHashMap<>();

    public static LatexSocialRelation between(ChangedEntity mob, ServerPlayer player) {
        if (DarkLatexDisguise.foolsDarkObserver(mob, player)) {
            return SAME_CATEGORY;
        }
        if (DarkLatexDisguise.appearsAsDarkRivalToWhite(mob, player)) {
            return RIVAL;
        }

        TransfurVariantInstance<?> variant = ProcessTransfur.getPlayerTransfurVariant(player);
        if (variant == null) {
            if (LatexSocialMemory.isBonded(mob, player)) {
                return FORMER_BONDED;
            }
            if (LatexSocialMemory.hasBondedEscortFor(mob, player)) {
                return FORMER_RESPECTED;
            }
            if (LatexSocialMemory.hasTrustedEscortFor(mob, player)) {
                return FRIEND_RESPECTED;
            }
            return HUMAN;
        }
        if (LatexSocialMemory.hasTrustedEscortFor(mob, player)) {
            return FRIEND_RESPECTED;
        }

        TransfurVariant<?> mobVariant = mob.getSelfVariant();
        TransfurVariant<?> playerVariant = variant.getParent();
        ChangedEntity playerForm = variant.getChangedEntity();
        String mobSpecies = speciesKey(mob);
        if (mob.getType() == playerForm.getType()
                || mobVariant != null && (mobVariant == playerVariant
                        || ChangedTransfurVariants.Gendered.getOpposite(mobVariant)
                                .map(opposite -> opposite == playerVariant)
                                .orElse(false))
                || !mobSpecies.isEmpty() && mobSpecies.equals(speciesKey(playerForm))) {
            return SAME_SPECIES;
        }

        // Light variants form ecological groups instead of one flat colour
        // faction. A shared natural spawn biome makes them compatriots even if
        // Changed's broad latex-type table would otherwise call them rivals.
        if (sharesLightSpawnBiome(mob, playerForm)) {
            return SAME_CATEGORY;
        }

        LatexType mobType = LatexType.getEntityLatexType(mob);
        // The player's transient overlay entity is not guaranteed to exist on
        // the same tick as a command-spawned creature. The variant-owned form
        // is stable and avoids a one-tick null type being treated as a rival.
        LatexType playerType = LatexType.getEntityLatexType(playerForm);
        if (mobType != null && playerType != null
                && (mobType.isHostileTo(playerType) || playerType.isHostileTo(mobType))) {
            return RIVAL;
        }

        if (sameCategory(mob, playerForm)) {
            return SAME_CATEGORY;
        }

        if (mobType != null && playerType != null
                && (mobType.isFriendlyTo(playerType) || playerType.isFriendlyTo(mobType))) {
            return FRIENDLY_OTHER;
        }

        return switch (HunterRelation.between(mob, player)) {
            case ALLY -> SAME_CATEGORY;
            case RIVAL -> RIVAL;
            case OUTSIDER -> OUTSIDER;
            case HUMAN -> HUMAN;
        };
    }

    public boolean isNormallyNeutral() {
        return this != HUMAN && this != RIVAL;
    }

    public boolean isFriendlyTransformed() {
        return this == SAME_SPECIES || this == SAME_CATEGORY || this == FRIENDLY_OTHER;
    }

    public boolean isFormerMember() {
        return this == FORMER_BONDED || this == FORMER_RESPECTED;
    }

    public String translationKey() {
        return "relation.changed_synergy." + name().toLowerCase(java.util.Locale.ROOT);
    }

    /** Stable category key used for same-clan relations and escort guarantees. */
    public static String categoryKey(ChangedEntity entity) {
        HunterFaction faction = HunterFaction.of(entity);
        if (faction == HunterFaction.LIGHT) {
            String species = speciesKey(entity);
            return species.isEmpty() ? "" : "species:" + species;
        }

        LatexType latexType = LatexType.getEntityLatexType(entity);
        if (latexType != null && latexType != ChangedLatexTypes.NONE.get()) {
            ResourceLocation key = ChangedRegistry.LATEX_TYPE.getKey(latexType);
            if (key != null) {
                return "latex:" + key;
            }
        }

        return "faction:" + faction.id();
    }

    /**
     * Category comparison supports overlapping Light-variant territories, which cannot
     * be represented by one flat category string.
     */
    public static boolean sameCategory(ChangedEntity first, ChangedEntity second) {
        HunterFaction firstFaction = HunterFaction.of(first);
        HunterFaction secondFaction = HunterFaction.of(second);
        if (firstFaction == HunterFaction.LIGHT || secondFaction == HunterFaction.LIGHT) {
            if (firstFaction != HunterFaction.LIGHT || secondFaction != HunterFaction.LIGHT) {
                return false;
            }
            return sameSpecies(first, second)
                    || sharesLightSpawnBiome(first, second);
        }

        String firstCategory = categoryKey(first);
        return !firstCategory.isEmpty() && firstCategory.equals(categoryKey(second));
    }

    /** Exact creature species, including Changed's paired gender variants. */
    public static boolean sameSpecies(ChangedEntity first, ChangedEntity second) {
        if (first.getType() == second.getType()) {
            return true;
        }

        TransfurVariant<?> firstVariant = first.getSelfVariant();
        TransfurVariant<?> secondVariant = second.getSelfVariant();
        if (firstVariant != null && secondVariant != null
                && (firstVariant == secondVariant
                        || ChangedTransfurVariants.Gendered.getOpposite(firstVariant)
                                .map(opposite -> opposite == secondVariant)
                                .orElse(false))) {
            return true;
        }

        String firstSpecies = speciesKey(first);
        return !firstSpecies.isEmpty() && firstSpecies.equals(speciesKey(second));
    }

    public static boolean sharesLightSpawnBiome(ChangedEntity first, ChangedEntity second) {
        if (HunterFaction.of(first) != HunterFaction.LIGHT
                || HunterFaction.of(second) != HunterFaction.LIGHT) {
            return false;
        }
        Registry<Biome> biomes = first.level().registryAccess()
                .registry(Registries.BIOME)
                .orElse(null);
        if (biomes == null) {
            return false;
        }
        Set<ResourceKey<Biome>> firstBiomes = naturalSpawnBiomes(biomes, first.getType());
        if (firstBiomes.isEmpty()) {
            return false;
        }
        Set<ResourceKey<Biome>> secondBiomes = naturalSpawnBiomes(biomes, second.getType());
        return firstBiomes.stream().anyMatch(secondBiomes::contains);
    }

    private static Set<ResourceKey<Biome>> naturalSpawnBiomes(
            Registry<Biome> biomes,
            EntityType<?> entityType) {
        synchronized (NATURAL_SPAWN_BIOMES) {
            return NATURAL_SPAWN_BIOMES
                    .computeIfAbsent(biomes, ignored -> new HashMap<>())
                    .computeIfAbsent(entityType,
                            ignored -> findNaturalSpawnBiomes(biomes, entityType));
        }
    }

    private static Set<ResourceKey<Biome>> findNaturalSpawnBiomes(
            Registry<Biome> biomes,
            EntityType<?> entityType) {
        Set<ResourceKey<Biome>> matches = new HashSet<>();
        for (Map.Entry<ResourceKey<Biome>, Biome> entry : biomes.entrySet()) {
            boolean present = false;
            for (MobCategory category : MobCategory.values()) {
                if (entry.getValue().getMobSettings().getMobs(category).unwrap().stream()
                        .anyMatch(spawn -> spawn.type == entityType)) {
                    present = true;
                    break;
                }
            }
            if (present) {
                matches.add(entry.getKey());
            }
        }
        return Set.copyOf(matches);
    }

    public static String speciesKey(ChangedEntity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return id == null ? "" : id.toString().replaceFirst("_(male|female|pup|partial)$", "");
    }
}
