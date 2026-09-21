package net.parkabird.changedsynergy.ai;

import java.util.Locale;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.latex.LatexType;
import net.ltxprogrammer.changed.init.ChangedLatexTypes;
import net.ltxprogrammer.changed.init.ChangedTags;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import javax.annotation.Nullable;

/**
 * Broad social factions. WHITE is the original pure-white latex opposed to
 * DARK; LIGHT contains its improved, generally neutral variants.
 */
public enum HunterFaction {
    WHITE("white"),
    DARK("dark"),
    AQUATIC("aquatic"),
    LIGHT("light");

    private static final TagKey<EntityType<?>> WHITE_FACTION = tag("white_faction");
    private static final TagKey<EntityType<?>> DARK_FACTION = tag("dark_faction");
    private static final TagKey<EntityType<?>> AQUATIC_FACTION = tag("aquatic_faction");
    private static final TagKey<EntityType<?>> LIGHT_FACTION = tag("light_faction");
    private static final TagKey<EntityType<?>> LEGACY_WILD_FACTION = tag("wild_faction");
    private static final TagKey<EntityType<?>> ORGANIC_LATEX = TagKey.create(
            Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath("changed", "organic_latex"));
    private static final String LEGACY_LIGHT_ID = "wild";
    private final String id;

    HunterFaction(String id) {
        this.id = id;
    }

    /** Stable lowercase ID used by saves, commands, dialogue and integrations. */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "faction.changed_synergy." + id;
    }

    /** Reads canonical IDs and the deprecated pre-Light {@code wild} ID. */
    @Nullable
    public static HunterFaction fromId(String id) {
        if (id == null) {
            return null;
        }
        String normalized = id.trim().toLowerCase(Locale.ROOT);
        if (LEGACY_LIGHT_ID.equals(normalized)) {
            return LIGHT;
        }
        for (HunterFaction faction : values()) {
            if (faction.id.equals(normalized)) {
                return faction;
            }
        }
        return null;
    }

    @Nullable
    public static HunterFaction fromOrdinal(int ordinal) {
        // Ordinal-only saves predate the removal of ORGANIC as a political
        // faction. Keep their old layout so AQUATIC and LIGHT cannot shift.
        return switch (ordinal) {
            case 0 -> WHITE;
            case 1 -> DARK;
            case 3 -> AQUATIC;
            case 4 -> LIGHT;
            default -> null;
        };
    }

    public static HunterFaction of(ChangedEntity entity) {
        if (PureWhiteWolfAdaptation.isAdaptedForm(entity)) {
            return WHITE;
        }
        EntityType<?> type = entity.getType();
        HunterFaction classified = of(type);
        if (isOrganicType(type)) {
            // Affiliation belongs to the species' native spawn region and is
            // encoded by data tags. Never infer it from the individual's
            // current position: commands, teleporting and migration must not
            // rewrite where that species comes from.
            // Types without a natural spawn-region mapping use neutral Light.
            return classified == null ? LIGHT : classified;
        }

        LatexType latexType = entity.getLatexType();
        if (classified == WHITE) {
            return WHITE;
        }
        if (classified == DARK
                || latexType == ChangedLatexTypes.DARK_LATEX.get()) {
            return DARK;
        }
        return classified == null ? LIGHT : classified;
    }

    /** Classifies registry types without creating a temporary entity. */
    @Nullable
    public static HunterFaction of(EntityType<?> type) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String path = typeId == null ? "" : typeId.getPath();
        // Explicit political affiliation wins over biological construction.
        // This is how dark dragons and crystal wolves remain Dark while still
        // using organic combat, visuals and dialogue.
        if (type.is(WHITE_FACTION)) {
            return WHITE;
        }
        if (type.is(DARK_FACTION)) {
            return DARK;
        }
        if (type.is(AQUATIC_FACTION)) {
            return AQUATIC;
        }
        if (type.is(LIGHT_FACTION) || type.is(LEGACY_WILD_FACTION)) {
            return LIGHT;
        }

        // Organic allegiance is supplied by an explicit tag derived from its
        // natural spawn table. An untagged form has no known native region and
        // is resolved to the neutral Light fallback by the entity overload.
        // Registry-only callers return null so Organic is never counted as a
        // fifth faction in biome, facility or settlement calculations.
        if (isOrganicType(type)) {
            return null;
        }
        if (type.is(AQUATIC_FACTION) || isAquaticName(path)) {
            return AQUATIC;
        }
        if (path.contains("pure_white")
                || path.contains("mutant_bloodcell")) {
            return WHITE;
        }
        if (path.contains("dark_latex")
                || path.contains("black_latex")
                || path.contains("latex_pup")
                || path.contains("puro")) {
            return DARK;
        }
        return type.is(ChangedTags.EntityTypes.LATEX) ? LIGHT : null;
    }

    /** Biological aquatic forms take priority over their latex color family. */
    public static boolean isAquatic(ChangedEntity entity) {
        EntityType<?> type = entity.getType();
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String path = typeId == null ? "" : typeId.getPath();
        return type.is(AQUATIC_FACTION) || isAquaticName(path);
    }

    /** Biological classification, deliberately separate from allegiance. */
    public static boolean isOrganic(ChangedEntity entity) {
        return isOrganicType(entity.getType());
    }

    /** Biological classification usable before an entity instance exists. */
    public static boolean isOrganicType(EntityType<?> type) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String path = typeId == null ? "" : typeId.getPath();
        return type.is(ORGANIC_LATEX) || path.contains("organic");
    }

    private static boolean isAquaticName(String path) {
        return path.contains("shark") || path.contains("orca") || path.contains("eel")
                || path.contains("manta") || path.contains("squid") || path.contains("mermaid")
                || path.contains("otter") || path.contains("crocodile") || path.contains("siren");
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("changed_synergy", path));
    }
}
