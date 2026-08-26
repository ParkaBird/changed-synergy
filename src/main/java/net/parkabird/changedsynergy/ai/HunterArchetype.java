package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Broad body-language profiles used to give different Changed creatures a
 * recognisable voice without hard-coding every entity from Changed and its
 * addons. Datapack tags take priority over the registry-name fallback.
 */
public enum HunterArchetype {
    CANINE,
    FELINE,
    AQUATIC,
    DRACONIC,
    AVIAN,
    CRITTER,
    INSECT,
    SOLDIER,
    ROYAL,
    GENERAL;

    private static final TagKey<EntityType<?>> CANINE_HUNTERS = tag("canine_hunters");
    private static final TagKey<EntityType<?>> FELINE_HUNTERS = tag("feline_hunters");
    private static final TagKey<EntityType<?>> AQUATIC_HUNTERS = tag("aquatic_hunters");
    private static final TagKey<EntityType<?>> DRACONIC_HUNTERS = tag("draconic_hunters");
    private static final TagKey<EntityType<?>> AVIAN_HUNTERS = tag("avian_hunters");
    private static final TagKey<EntityType<?>> CRITTER_HUNTERS = tag("critter_hunters");
    private static final TagKey<EntityType<?>> INSECT_HUNTERS = tag("insect_hunters");
    private static final TagKey<EntityType<?>> SOLDIER_HUNTERS = tag("soldier_hunters");
    private static final TagKey<EntityType<?>> ROYAL_HUNTERS = tag("royal_hunters");

    public static HunterArchetype of(ChangedEntity entity) {
        EntityType<?> type = entity.getType();
        if (type.is(ROYAL_HUNTERS)) {
            return ROYAL;
        }
        if (type.is(SOLDIER_HUNTERS)) {
            return SOLDIER;
        }
        if (type.is(AQUATIC_HUNTERS)) {
            return AQUATIC;
        }
        if (type.is(DRACONIC_HUNTERS)) {
            return DRACONIC;
        }
        if (type.is(AVIAN_HUNTERS)) {
            return AVIAN;
        }
        if (type.is(INSECT_HUNTERS)) {
            return INSECT;
        }
        if (type.is(FELINE_HUNTERS)) {
            return FELINE;
        }
        if (type.is(CRITTER_HUNTERS)) {
            return CRITTER;
        }
        if (type.is(CANINE_HUNTERS)) {
            return CANINE;
        }

        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(type);
        String path = id == null ? "" : id.getPath();
        if (containsAny(path, "behemoth", "boss", "royal", "king", "queen", "emperor")) {
            return ROYAL;
        }
        if (containsAny(path, "knight", "sniper", "soldier", "guard", "sentinel", "centaur")) {
            return SOLDIER;
        }
        if (containsAny(path, "shark", "orca", "eel", "manta", "squid", "mermaid",
                "otter", "crocodile", "siren", "dolphin", "axolotl")) {
            return AQUATIC;
        }
        if (containsAny(path, "dragon", "wyvern", "lizard", "snake", "kobold", "reptile")) {
            return DRACONIC;
        }
        if (containsAny(path, "crow", "bird", "avian", "eagle", "hawk", "owl", "raven")) {
            return AVIAN;
        }
        if (containsAny(path, "bee", "moth", "insect", "stiger", "spider", "wasp")) {
            return INSECT;
        }
        if (containsAny(path, "cat", "tiger", "leopard", "lion", "lynx", "panther")) {
            return FELINE;
        }
        if (containsAny(path, "rabbit", "squirrel", "raccoon", "panda", "deer", "skunk", "pudding")) {
            return CRITTER;
        }
        if (containsAny(path, "wolf", "dog", "fox", "gnoll", "cerberus", "coyote", "hyena")) {
            return CANINE;
        }
        return GENERAL;
    }

    private static boolean containsAny(String path, String... needles) {
        for (String needle : needles) {
            if (path.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath("changed_synergy", path));
    }
}
