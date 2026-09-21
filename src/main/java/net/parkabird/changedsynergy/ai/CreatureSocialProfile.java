package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.compat.ChangedAddonCompat;

/**
 * Data-driven social capability for Changed creatures.
 *
 * <p>This is deliberately separate from faction, species and combat
 * disposition. Juveniles may retain limited social reactions without forming
 * an adult relationship, while {@link Kind#NONE} opts an entity out of every
 * Synergy social, dialogue and creature-life system.</p>
 */
public final class CreatureSocialProfile {
    private static final java.util.Set<String> EXTERNAL_SYSTEM_ONLY_NAMESPACES =
            java.util.Set.of("furmutage", "changed_survive_protocol");
    private static final TagKey<EntityType<?>> SOCIAL_INTERACTION_EXCLUDED =
            tag("social_interaction_excluded");
    private static final TagKey<EntityType<?>> JUVENILE_SOCIAL =
            tag("juvenile_social");
    private static final TagKey<EntityType<?>> AGGRESSIVE_SOCIAL =
            tag("aggressive_social");

    private CreatureSocialProfile() {
    }

    public enum Kind {
        FULL,
        /** Sentient and negotiable, but never pacified into a friendship. */
        AGGRESSIVE,
        JUVENILE,
        NONE
    }

    public static Kind of(ChangedEntity creature) {
        if (isPermanentlyExcluded(creature)
                || creature.getType().is(SOCIAL_INTERACTION_EXCLUDED)) {
            return Kind.NONE;
        }
        if (creature.getType().is(AGGRESSIVE_SOCIAL)) {
            return Kind.AGGRESSIVE;
        }
        if (creature.getType().is(JUVENILE_SOCIAL)) {
            return Kind.JUVENILE;
        }
        return Kind.FULL;
    }

    public static boolean allowsPersonalRelationship(ChangedEntity creature) {
        // Juveniles can still react, be patted, and use Changed's native pet
        // mechanics, but they cannot acquire Synergy's adult friendship/bond.
        return of(creature) == Kind.FULL;
    }

    public static boolean allowsSocialWheel(ChangedEntity creature) {
        // Juveniles may still react to pats, food, and Changed's native pet
        // interactions, but Synergy's relationship/bond wheels belong to the
        // adult creature they eventually become.
        Kind kind = of(creature);
        return kind == Kind.FULL || kind == Kind.AGGRESSIVE;
    }

    public static boolean allowsPoliteContact(ChangedEntity creature) {
        // Polite first contact can grow into a personal relationship. Profiles
        // that cannot form one must keep their native hostile disposition.
        return of(creature) == Kind.FULL;
    }

    public static boolean allowsSynergySystems(ChangedEntity creature) {
        return of(creature) != Kind.NONE;
    }

    public static boolean allowsDialogue(ChangedEntity creature) {
        return of(creature) != Kind.NONE;
    }

    public static boolean isJuvenile(ChangedEntity creature) {
        return of(creature) == Kind.JUVENILE;
    }

    public static boolean isAggressive(ChangedEntity creature) {
        return of(creature) == Kind.AGGRESSIVE;
    }

    /**
     * Critical exclusions are denied in code as well as data so a missing or
     * replaced datapack tag cannot expose them to Synergy systems.
     */
    public static boolean isPermanentlyExcluded(ChangedEntity creature) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(creature.getType());
        return isPermanentlyExcluded(id)
                || id != null
                        && ChangedAddonCompat.MOD_ID.equals(id.getNamespace())
                        && ("luminarctic_leopard_male".equals(id.getPath())
                            || "luminarctic_leopard_female".equals(id.getPath()))
                        && ChangedAddonCompat.isSocialSystemExcludedBoss(creature);
    }

    public static boolean isPermanentlyExcluded(ResourceLocation id) {
        if (id == null) {
            return false;
        }
        // These mods own their creature behaviour. Synergy intentionally does
        // not attach identity, social, community, AI, or takeover state to
        // their entity namespaces. Namespace checks avoid optional class links.
        if (EXTERNAL_SYSTEM_ONLY_NAMESPACES.contains(id.getNamespace())) {
            return true;
        }
        if (ChangedAddonCompat.MOD_ID.equals(id.getNamespace())) {
            return switch (id.getPath()) {
                case "experiment_009_boss", "experiment_10_boss",
                        "latex_snow_fox_foxyas", "void_fox" -> true;
                default -> false;
            };
        }
        if (!"changed".equals(id.getNamespace())) {
            return false;
        }
        return switch (id.getPath()) {
            case "behemoth", "behemoth_head", "behemoth_hand_left",
                    "behemoth_hand_right",
                    "latex_benign_wolf", "latex_benign_orca" -> true;
            default -> false;
        };
    }

    /**
     * Creatures whose body or lifecycle must never enter Addon's generic arm
     * grab state. This is deliberately narrower than the full social-system
     * exclusion list: benign creatures may still be excluded from social
     * content without losing an ability supplied by their native mod.
     */
    public static boolean isGrabMechanicExcluded(ChangedEntity creature) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(creature.getType());
        return isGrabMechanicExcluded(id);
    }

    public static boolean isGrabMechanicExcluded(ResourceLocation id) {
        if (id == null || !"changed".equals(id.getNamespace())) {
            return false;
        }
        return switch (id.getPath()) {
            case "headless_knight", "latex_shark_feral", "milk_pudding",
                    "dark_latex_wolf_pup", "pure_white_latex_wolf_pup" -> true;
            default -> false;
        };
    }

    private static TagKey<EntityType<?>> tag(String path) {
        return TagKey.create(
                Registries.ENTITY_TYPE,
                ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, path));
    }
}
