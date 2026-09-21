package net.parkabird.changedsynergy.ai;

import java.util.Locale;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.TamableLatexEntity;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/**
 * Gives related creatures a stable call name without replacing names assigned
 * by a player. New names follow a species-specific standard while retaining
 * faction, palette and dominant-personality influences. Unrelated creatures
 * remain unnamed and despawn according to Changed's normal rules. The short
 * suffix distinguishes rare collisions.
 */
public final class CreatureIdentity {
    private static final String ROOT = "ChangedSynergyIdentity";
    private static final String VERSION = "Version";
    private static final String GENERATED = "Generated";
    private static final String PLAYER_NAMED = "PlayerNamed";
    private static final String FACTION = "Faction";
    private static final String NAME_INDEX = "NameIndex";
    private static final String FORMAT = "Format";
    private static final String COMPOSITE_FORMAT = "palette_personality_v1";
    private static final String STANDARD_FORMAT = "species_standard_v1";
    private static final String LEGACY_FORMAT = "faction_pool_v1";
    private static final String LOCALIZED_STANDARD_FORMAT = "localized_species_or_legacy_v1";
    private static final String COLOR_FAMILY = "ColorFamily";
    private static final String COLOR_INDEX = "ColorIndex";
    private static final String NAME_STYLE = "NameStyle";
    private static final String DOMINANT_TRAIT = "DominantTrait";
    private static final String SPECIES_PROFILE = "SpeciesProfile";
    private static final String SPECIES_ID = "SpeciesId";
    private static final String SIGNATURE = "Signature";
    private static final int CURRENT_VERSION = 6;
    private static final int NAMES_PER_FACTION = 16;
    private static final int COLOR_NAMES_PER_FAMILY = 3;
    private static final int SIGNATURE_SPACE = 36 * 36;

    private CreatureIdentity() {
    }

    public static void ensure(ChangedEntity mob) {
        boolean recognized = isRecognizedCompanion(mob)
                || CreatureSocialProfile.allowsPersonalRelationship(mob)
                        && CreaturePersonality.hasAnyEstablishedRelationship(mob);
        CompoundTag identity = existingData(mob);
        if (identity != null) {
            migrateLegacyLightFaction(mob, identity);
            migrateLocalizedNameFormat(mob, identity);
        }

        if (identity != null
                && identity.getBoolean(GENERATED)
                && mob.hasCustomName()
                && !matchesGeneratedName(mob, identity)) {
            markPlayerNamed(identity);
        }

        if (!recognized) {
            if (identity != null && identity.getBoolean(GENERATED)) {
                clearLegacyUnrelatedIdentity(mob, identity);
            }
            if (mob.hasCustomName()) {
                mob.setPersistenceRequired();
                markPlayerNamed(identity != null ? identity : data(mob));
            }
            return;
        }

        boolean persistent = ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.CREATURE_LIFE);
        if (persistent) {
            mob.setPersistenceRequired();
        }
        if (identity == null) {
            identity = data(mob);
        }
        if (!identity.contains(VERSION, Tag.TAG_INT)) {
            if (mob.hasCustomName()) {
                markPlayerNamed(identity);
                return;
            }
            generate(mob, identity);
        }

        if (!identity.getBoolean(GENERATED)) {
            identity.putInt(VERSION, CURRENT_VERSION);
            return;
        }

        Component generatedName = generatedName(identity);
        if (mob.hasCustomName() && !matchesGeneratedName(mob, identity)) {
            markPlayerNamed(identity);
            return;
        }

        mob.setCustomName(generatedName);
        mob.setCustomNameVisible(true);
        if (!persistent
                && !(mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame())) {
            // setCustomName marks a Mob persistent. Undo only Synergy's own
            // generated-name side effect; player names and native pets stay safe.
            mob.persistenceRequired = false;
        }
        identity.putInt(VERSION, CURRENT_VERSION);
    }

    public static boolean hasGeneratedName(ChangedEntity mob) {
        CompoundTag identity = existingData(mob);
        return identity != null && identity.getBoolean(GENERATED);
    }

    /**
     * Name used during a first introduction. Generated collision signatures
     * remain visible above the entity, but are not spoken as part of its name.
     */
    public static Component introductionName(ChangedEntity mob) {
        CompoundTag identity = existingData(mob);
        if (identity == null || !identity.getBoolean(GENERATED)
                || identity.getBoolean(PLAYER_NAMED)) {
            return mob.getDisplayName();
        }
        return generatedIntroductionName(identity);
    }

    /** Groups real species by the kinds of sounds their anatomy can make. */
    public static String vocalizationProfile(ChangedEntity mob) {
        if (CreatureSocialProfile.isJuvenile(mob)) {
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            String path = id == null ? "" : id.getPath();
            return path.contains("wolf") ? "canine" : "small_mammal";
        }
        return switch (SpeciesProfile.of(mob)) {
            case WOLF, FOX, DOG, CERBERUS -> "canine";
            case CAT, BIG_CAT -> "feline";
            case SHARK, ORCA, RAY, EEL, SQUID_DOG, SIREN -> "aquatic";
            case DRAGON, WYVERN, YUFENG -> "draconic";
            case AVIAN -> "avian";
            case DEER, RABBIT -> "herbivore";
            case SMALL_MAMMAL -> "small_mammal";
            case INSECT -> "insect";
            case REPTILE -> "reptile";
            case HYBRID -> "hybrid";
            case CENTAUR, KNIGHT, ROYAL, EXPERIMENT, PUDDING,
                    ALIEN, JUVENILE, OTHER -> "humanoid";
        };
    }

    /** Applies a changed persistence gamerule without touching identity or relationship NBT. */
    public static void reconcilePersistence(ChangedEntity mob) {
        CompoundTag identity = existingData(mob);
        boolean synergyIdentity = identity != null
                && identity.getBoolean(GENERATED)
                && mob.hasCustomName()
                && matchesGeneratedName(mob, identity);
        boolean storedBond = !LatexSocialMemory.bondedPlayerUuids(mob).isEmpty();
        boolean pendingNegotiation =
                InvoluntaryTransfurNegotiation.isNegotiationSource(mob);
        if (!synergyIdentity && !storedBond && !pendingNegotiation) {
            return;
        }
        if (pendingNegotiation || ChangedSynergyGameRules.enabled(
                mob.level(), ChangedSynergyGameRules.CREATURE_LIFE)) {
            mob.setPersistenceRequired();
        } else if (!(mob instanceof TamableLatexEntity nativePet
                && nativePet.isTame())) {
            mob.persistenceRequired = false;
        }
    }

    private static void clearLegacyUnrelatedIdentity(
            ChangedEntity mob,
            CompoundTag identity) {
        if (mob.hasCustomName() && matchesGeneratedName(mob, identity)) {
            mob.setCustomName(null);
            mob.setCustomNameVisible(false);
        }
        mob.persistenceRequired = false;
        identity.putInt(VERSION, CURRENT_VERSION);
    }

    private static boolean isRecognizedCompanion(ChangedEntity mob) {
        return InvoluntaryTransfurNegotiation.isNegotiationSource(mob)
                || !LatexSocialMemory.bondedPlayerUuids(mob).isEmpty()
                || LatexSocialMemory.petOwnerUuid(mob).isPresent()
                || mob instanceof TamableLatexEntity nativePet
                        && nativePet.isTame();
    }

    private static boolean matchesGeneratedName(
            ChangedEntity mob,
            CompoundTag identity) {
        return Component.Serializer.toJson(generatedName(identity))
                .equals(Component.Serializer.toJson(mob.getCustomName()));
    }

    private static void markPlayerNamed(CompoundTag identity) {
        identity.putBoolean(PLAYER_NAMED, true);
        identity.putBoolean(GENERATED, false);
        identity.putInt(VERSION, CURRENT_VERSION);
    }

    private static void generate(ChangedEntity mob, CompoundTag identity) {
        HunterFaction faction = HunterFaction.of(mob);
        CreaturePersonality.Trait dominantTrait =
                CreaturePersonality.dominantTrait(mob);
        ColorFamily colorFamily = ColorFamily.of(mob, faction);
        NameStyle nameStyle = NameStyle.of(mob);
        SpeciesProfile speciesProfile = SpeciesProfile.of(mob);
        long seed = identitySeed(mob);
        int colorIndex = (int)Long.remainderUnsigned(
                mix64(seed
                        ^ 0xD1B54A32D192ED03L
                        ^ (long)(faction.ordinal() + 1) * 0x9E3779B97F4A7C15L),
                COLOR_NAMES_PER_FAMILY);
        int signatureIndex = (int)Long.remainderUnsigned(
                mix64(seed ^ 0xA0761D6478BD642FL), SIGNATURE_SPACE);
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());

        identity.putString(FACTION, faction.id());
        identity.putString(
                FORMAT,
                Long.remainderUnsigned(
                                mix64(seed ^ 0xE7037ED1A0B428DBL), 2L)
                                == 0L
                        ? LEGACY_FORMAT
                        : LOCALIZED_STANDARD_FORMAT);
        identity.putString(COLOR_FAMILY, colorFamily.key);
        identity.putInt(COLOR_INDEX, colorIndex);
        identity.putString(NAME_STYLE, nameStyle.key);
        identity.putString(DOMINANT_TRAIT, dominantTrait.dialogueKey());
        identity.putString(SPECIES_PROFILE, speciesProfile.key);
        identity.putString(SPECIES_ID, String.valueOf(typeId));
        identity.putInt(NAME_INDEX, (int)Long.remainderUnsigned(
                mix64(seed ^ speciesProfile.ordinal() * 0x94D049BB133111EBL),
                NAMES_PER_FACTION));
        identity.putString(SIGNATURE, signature(signatureIndex));
        identity.putBoolean(GENERATED, true);
        identity.putBoolean(PLAYER_NAMED, false);
        identity.putInt(VERSION, CURRENT_VERSION);

        mob.setCustomName(generatedName(identity));
        mob.setCustomNameVisible(true);
    }

    private static Component generatedName(CompoundTag identity) {
        String format = identity.getString(FORMAT);
        if (LOCALIZED_STANDARD_FORMAT.equals(format)) {
            return Component.translatable(
                    "name.changed_synergy.generated.localized_choice",
                    legacyName(identity),
                    standardName(identity));
        }
        if (STANDARD_FORMAT.equals(format)) {
            return localizedChoice(identity, standardName(identity));
        }
        if (LEGACY_FORMAT.equals(format)) {
            return legacyName(identity);
        }
        if (COMPOSITE_FORMAT.equals(format)) {
            String colorFamily = identity.getString(COLOR_FAMILY);
            String style = identity.getString(NAME_STYLE);
            String trait = identity.getString(DOMINANT_TRAIT);
            int colorIndex = Math.floorMod(
                    identity.getInt(COLOR_INDEX), COLOR_NAMES_PER_FAMILY);
            Component composite = Component.translatable(
                    "name.changed_synergy.generated.composite",
                    Component.translatable("name.changed_synergy.color."
                            + colorFamily + "." + colorIndex),
                    Component.translatable("name.changed_synergy.style."
                            + style + "." + trait),
                    identity.getString(SIGNATURE));
            return localizedChoice(identity, composite);
        }

        // Identities created before v3 keep their original call name.
        return legacyName(identity);
    }

    private static Component generatedIntroductionName(CompoundTag identity) {
        String format = identity.getString(FORMAT);
        if (LOCALIZED_STANDARD_FORMAT.equals(format)) {
            return Component.translatable(
                    "name.changed_synergy.generated.localized_choice.base",
                    legacyBaseName(identity),
                    standardBaseName(identity));
        }
        if (STANDARD_FORMAT.equals(format)) {
            return Component.translatable(
                    "name.changed_synergy.generated.localized_choice.base",
                    legacyBaseName(identity),
                    standardBaseName(identity));
        }
        if (COMPOSITE_FORMAT.equals(format)) {
            int colorIndex = Math.floorMod(
                    identity.getInt(COLOR_INDEX), COLOR_NAMES_PER_FAMILY);
            Component composite = Component.translatable(
                    "name.changed_synergy.generated.composite.base",
                    Component.translatable("name.changed_synergy.color."
                            + identity.getString(COLOR_FAMILY) + "." + colorIndex),
                    Component.translatable("name.changed_synergy.style."
                            + identity.getString(NAME_STYLE) + "."
                            + identity.getString(DOMINANT_TRAIT)));
            return Component.translatable(
                    "name.changed_synergy.generated.localized_choice.base",
                    legacyBaseName(identity), composite);
        }
        return legacyBaseName(identity);
    }

    private static Component standardName(CompoundTag identity) {
            String faction = identity.getString(FACTION);
            String colorFamily = identity.getString(COLOR_FAMILY);
            String speciesProfile = identity.getString(SPECIES_PROFILE);
            String trait = identity.getString(DOMINANT_TRAIT);
            int colorIndex = Math.floorMod(
                    identity.getInt(COLOR_INDEX), COLOR_NAMES_PER_FAMILY);
            return Component.translatable(
                    "name.changed_synergy.generated.standard." + faction,
                    Component.translatable("name.changed_synergy.color."
                            + colorFamily + "." + colorIndex),
                    Component.translatable("name.changed_synergy.species."
                            + speciesProfile + "." + trait),
                    identity.getString(SIGNATURE));
    }

    private static Component standardBaseName(CompoundTag identity) {
        String faction = identity.getString(FACTION);
        int colorIndex = Math.floorMod(
                identity.getInt(COLOR_INDEX), COLOR_NAMES_PER_FAMILY);
        return Component.translatable(
                "name.changed_synergy.generated.standard." + faction + ".base",
                Component.translatable("name.changed_synergy.color."
                        + identity.getString(COLOR_FAMILY) + "." + colorIndex),
                Component.translatable("name.changed_synergy.species."
                        + identity.getString(SPECIES_PROFILE) + "."
                        + identity.getString(DOMINANT_TRAIT)));
    }

    private static Component localizedChoice(
            CompoundTag identity,
            Component modernName) {
        return Component.translatable(
                "name.changed_synergy.generated.localized_choice",
                legacyName(identity),
                modernName);
    }

    private static Component legacyName(CompoundTag identity) {
        String faction = identity.getString(FACTION);
        int index = Math.floorMod(identity.getInt(NAME_INDEX), NAMES_PER_FACTION);
        return Component.translatable(
                "name.changed_synergy.generated",
                Component.translatable("name.changed_synergy." + faction + "." + index),
                identity.getString(SIGNATURE));
    }

    private static Component legacyBaseName(CompoundTag identity) {
        String faction = identity.getString(FACTION);
        int index = Math.floorMod(identity.getInt(NAME_INDEX), NAMES_PER_FACTION);
        return Component.translatable(
                "name.changed_synergy.generated.base",
                Component.translatable("name.changed_synergy." + faction + "." + index));
    }

    /**
     * Version 6 changes only the translated presentation. Refresh names that
     * still exactly match Synergy's previous generated component, while
     * preserving a name genuinely typed by a player.
     */
    private static void migrateLocalizedNameFormat(
            ChangedEntity mob,
            CompoundTag identity) {
        if (!identity.getBoolean(GENERATED)
                || identity.getInt(VERSION) >= CURRENT_VERSION
                || !mob.hasCustomName()
                || !Component.Serializer.toJson(previousGeneratedName(identity))
                        .equals(Component.Serializer.toJson(mob.getCustomName()))) {
            return;
        }
        mob.setCustomName(generatedName(identity));
        mob.setCustomNameVisible(true);
        identity.putInt(VERSION, CURRENT_VERSION);
    }

    private static Component previousGeneratedName(CompoundTag identity) {
        String format = identity.getString(FORMAT);
        if (STANDARD_FORMAT.equals(format)
                || LOCALIZED_STANDARD_FORMAT.equals(format)) {
            return standardName(identity);
        }
        if (COMPOSITE_FORMAT.equals(format)) {
            String colorFamily = identity.getString(COLOR_FAMILY);
            String style = identity.getString(NAME_STYLE);
            String trait = identity.getString(DOMINANT_TRAIT);
            int colorIndex = Math.floorMod(
                    identity.getInt(COLOR_INDEX), COLOR_NAMES_PER_FAMILY);
            return Component.translatable(
                    "name.changed_synergy.generated.composite",
                    Component.translatable("name.changed_synergy.color."
                            + colorFamily + "." + colorIndex),
                    Component.translatable("name.changed_synergy.style."
                            + style + "." + trait),
                    identity.getString(SIGNATURE));
        }
        return legacyName(identity);
    }

    /**
     * Keeps generated call names continuous while canonicalizing the former
     * wild faction ID. A genuine player-assigned name is never replaced.
     */
    private static void migrateLegacyLightFaction(
            ChangedEntity mob,
            CompoundTag identity) {
        if (!"wild".equalsIgnoreCase(identity.getString(FACTION))) {
            return;
        }
        boolean replaceGeneratedName = identity.getBoolean(GENERATED)
                && mob.hasCustomName()
                && Component.Serializer.toJson(previousGeneratedName(identity))
                        .equals(Component.Serializer.toJson(mob.getCustomName()));
        identity.putString(FACTION, HunterFaction.LIGHT.id());
        identity.putInt(VERSION, CURRENT_VERSION);
        if (replaceGeneratedName) {
            mob.setCustomName(generatedName(identity));
            mob.setCustomNameVisible(true);
        }
    }

    private static String signature(int value) {
        String code = Integer.toString(value, 36).toUpperCase(Locale.ROOT);
        return code.length() < 2 ? "0" + code : code;
    }

    private static long identitySeed(ChangedEntity mob) {
        ResourceLocation typeId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        long typeSalt = String.valueOf(typeId).hashCode() * 0x9E3779B97F4A7C15L;
        long traitSalt = 0L;
        for (CreaturePersonality.Trait trait : CreaturePersonality.traits(mob)) {
            traitSalt ^= Long.rotateLeft(
                    0x94D049BB133111EBL * (trait.ordinal() + 1L),
                    trait.ordinal() * 7);
        }
        traitSalt ^= 0xBF58476D1CE4E5B9L
                * (CreaturePersonality.dominantTrait(mob).ordinal() + 1L);
        return mob.getUUID().getMostSignificantBits()
                ^ Long.rotateLeft(mob.getUUID().getLeastSignificantBits(), 29)
                ^ typeSalt
                ^ traitSalt;
    }

    private static CompoundTag data(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        if (!persistent.contains(ROOT, Tag.TAG_COMPOUND)) {
            persistent.put(ROOT, new CompoundTag());
        }
        return persistent.getCompound(ROOT);
    }

    private static CompoundTag existingData(ChangedEntity mob) {
        CompoundTag persistent = mob.getPersistentData();
        return persistent.contains(ROOT, Tag.TAG_COMPOUND)
                ? persistent.getCompound(ROOT)
                : null;
    }

    private static long mix64(long value) {
        value = (value ^ value >>> 30) * 0xBF58476D1CE4E5B9L;
        value = (value ^ value >>> 27) * 0x94D049BB133111EBL;
        return value ^ value >>> 31;
    }

    private enum NameStyle {
        CANINE("canine"),
        FELINE("feline"),
        AQUATIC("aquatic"),
        DRACONIC("draconic"),
        AVIAN("avian"),
        CRITTER("critter"),
        INSECT("insect"),
        SOLDIER("soldier"),
        ROYAL("royal"),
        CENTAUR("centaur"),
        REPTILE("reptile"),
        JUVENILE("juvenile"),
        GENERAL("general");

        private final String key;

        NameStyle(String key) {
            this.key = key;
        }

        private static NameStyle of(ChangedEntity mob) {
            if (CreatureSocialProfile.isJuvenile(mob)) {
                return JUVENILE;
            }

            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            String path = id == null ? "" : id.getPath();
            if (containsAny(path, "centaur", "_taur", "double_yufeng")) {
                return CENTAUR;
            }
            if (containsAny(path, "lizard", "snake", "reptile", "crocodile")) {
                return REPTILE;
            }

            return switch (HunterArchetype.of(mob)) {
                case CANINE -> CANINE;
                case FELINE -> FELINE;
                case AQUATIC -> AQUATIC;
                case DRACONIC -> DRACONIC;
                case AVIAN -> AVIAN;
                case CRITTER -> CRITTER;
                case INSECT -> INSECT;
                case SOLDIER -> SOLDIER;
                case ROYAL -> ROYAL;
                case GENERAL -> GENERAL;
            };
        }

        private static boolean containsAny(String path, String... needles) {
            for (String needle : needles) {
                if (path.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** A naming dialect describes an actual species, not merely its silhouette. */
    private enum SpeciesProfile {
        WOLF("wolf"),
        FOX("fox"),
        DOG("dog"),
        CERBERUS("cerberus"),
        CAT("cat"),
        BIG_CAT("big_cat"),
        SHARK("shark"),
        ORCA("orca"),
        RAY("ray"),
        EEL("eel"),
        SQUID_DOG("squid_dog"),
        SIREN("siren"),
        DRAGON("dragon"),
        WYVERN("wyvern"),
        YUFENG("yufeng"),
        AVIAN("avian"),
        DEER("deer"),
        RABBIT("rabbit"),
        SMALL_MAMMAL("small_mammal"),
        INSECT("insect"),
        REPTILE("reptile"),
        CENTAUR("centaur"),
        KNIGHT("knight"),
        ROYAL("royal"),
        EXPERIMENT("experiment"),
        HYBRID("hybrid"),
        PUDDING("pudding"),
        ALIEN("alien"),
        JUVENILE("juvenile"),
        OTHER("other");

        private final String key;

        SpeciesProfile(String key) {
            this.key = key;
        }

        private static SpeciesProfile of(ChangedEntity mob) {
            if (CreatureSocialProfile.isJuvenile(mob)) {
                return JUVENILE;
            }
            ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
            String path = id == null ? "" : id.getPath();

            if (containsAny(path, "dragon_snow_leopard_shark",
                    "squid_tiger_shark")) return HYBRID;
            if (containsAny(path, "centaur", "_taur", "double_yufeng")) {
                return CENTAUR;
            }
            if (path.contains("cerberus")) return CERBERUS;
            if (containsAny(path, "squid_dog", "squid_tiger")) return SQUID_DOG;
            if (path.contains("orca")) return ORCA;
            if (path.contains("manta")) return RAY;
            if (path.contains("eel")) return EEL;
            if (path.contains("shark")) return SHARK;
            if (path.contains("siren")) return SIREN;
            if (containsAny(path, "wyvern", "pink_yuin")) return WYVERN;
            if (containsAny(path, "yufeng", "beifeng", "latex_yuin")) return YUFENG;
            if (path.contains("dragon")) return DRAGON;
            if (containsAny(path, "kitsune", "fox", "puro_kind")) return FOX;
            if (containsAny(path, "wolfy", "dog", "collie", "gnoll")) return DOG;
            if (path.contains("wolf")) return WOLF;
            if (containsAny(path, "tiger", "leopard", "cheetah")) return BIG_CAT;
            if (containsAny(path, "cat", "lynx")) return CAT;
            if (containsAny(path, "crow", "avali", "bird")) return AVIAN;
            if (path.contains("deer")) return DEER;
            if (containsAny(path, "rabbit", "buny")) return RABBIT;
            if (containsAny(path, "raccoon", "panda", "skunk", "squirrel",
                    "otter", "fennec")) return SMALL_MAMMAL;
            if (containsAny(path, "bee", "moth", "stiger", "insect")) {
                return INSECT;
            }
            if (containsAny(path, "crocodile", "lizard", "snake", "kobold")) {
                return REPTILE;
            }
            if (path.contains("knight") || path.contains("sniper")) return KNIGHT;
            if (containsAny(path, "queen", "behemoth", "boss", "royal")) {
                return ROYAL;
            }
            if (containsAny(path, "experiment", "exp_", "latex_dazed")) {
                return EXPERIMENT;
            }
            if (path.contains("pudding")) return PUDDING;
            if (path.contains("alien")) return ALIEN;
            return OTHER;
        }

        private static boolean containsAny(String path, String... needles) {
            for (String needle : needles) {
                if (path.contains(needle)) {
                    return true;
                }
            }
            return false;
        }
    }

    private enum ColorFamily {
        BLACK("black"),
        WHITE("white"),
        GRAY("gray"),
        SILVER("silver"),
        RED("red"),
        ORANGE("orange"),
        GOLD("gold"),
        YELLOW("yellow"),
        GREEN("green"),
        CYAN("cyan"),
        BLUE("blue"),
        PURPLE("purple"),
        PINK("pink"),
        BROWN("brown");

        private final String key;

        ColorFamily(String key) {
            this.key = key;
        }

        private static ColorFamily of(
                ChangedEntity mob,
                HunterFaction fallbackFaction) {
            if (mob.getSelfVariant() == null) {
                return fallback(mob, fallbackFaction);
            }

            var colors = mob.getSelfVariant().getColors();
            Color3 primary = colors.getFirst();
            Color3 secondary = colors.getSecond();
            Color3 representative = representative(primary, secondary);
            return classify(representative);
        }

        private static Color3 representative(Color3 primary, Color3 secondary) {
            float primaryValue = value(primary);
            float primarySaturation = saturation(primary);
            float secondarySaturation = saturation(secondary);
            if (primaryValue > 0.22F
                    && primaryValue < 0.78F
                    && primarySaturation < 0.13F
                    && secondarySaturation > primarySaturation + 0.20F) {
                return secondary;
            }
            return primary;
        }

        private static ColorFamily classify(Color3 color) {
            float red = color.red();
            float green = color.green();
            float blue = color.blue();
            float max = Math.max(red, Math.max(green, blue));
            float min = Math.min(red, Math.min(green, blue));
            float delta = max - min;
            float saturation = max <= 0.0001F ? 0.0F : delta / max;

            if (max < 0.18F) {
                return BLACK;
            }
            if (saturation < 0.13F) {
                if (max > 0.86F) return WHITE;
                if (max > 0.62F) return SILVER;
                return GRAY;
            }

            float hue;
            if (max == red) {
                hue = 60.0F * ((green - blue) / delta % 6.0F);
            } else if (max == green) {
                hue = 60.0F * ((blue - red) / delta + 2.0F);
            } else {
                hue = 60.0F * ((red - green) / delta + 4.0F);
            }
            if (hue < 0.0F) hue += 360.0F;

            if (hue < 14.0F || hue >= 345.0F) return RED;
            if (hue < 42.0F) return max < 0.62F ? BROWN : ORANGE;
            if (hue < 58.0F) return GOLD;
            if (hue < 72.0F) return YELLOW;
            if (hue < 168.0F) return GREEN;
            if (hue < 202.0F) return CYAN;
            if (hue < 258.0F) return BLUE;
            if (hue < 326.0F) return PURPLE;
            return max > 0.58F ? PINK : RED;
        }

        private static float saturation(Color3 color) {
            float max = value(color);
            float min = Math.min(color.red(), Math.min(color.green(), color.blue()));
            return max <= 0.0001F ? 0.0F : (max - min) / max;
        }

        private static float value(Color3 color) {
            return Math.max(color.red(), Math.max(color.green(), color.blue()));
        }

        private static ColorFamily fallback(
                ChangedEntity mob,
                HunterFaction faction) {
            if (LatexSocialMemory.isOrganic(mob)) {
                return BROWN;
            }
            return switch (faction) {
                case WHITE -> WHITE;
                case DARK -> BLACK;
                case AQUATIC -> BLUE;
                case LIGHT -> GRAY;
            };
        }
    }
}
