package net.parkabird.changedsynergy.ai;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared species identity, dialogue key and screen color for hypnosis users. */
public enum HypnosisProfile {
    HYPNO_CAT("hypno_cat", 0xC18BFF),
    LUMINARCTIC_LEOPARD("luminarctic_leopard", 0x86E4FF),
    KAYLA_SHARK("kayla_shark", 0x55D9D0),
    EXPERIMENT_10_BOSS("experiment_10_boss", 0xC355E8),
    EXPERIMENT_10("experiment_10", 0xA969D6),
    GENERIC("generic", 0xB98AF3);

    private static final TagKey<EntityType<?>> HYPNOTIC_CREATURES = TagKey.create(
            Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "hypnotic_creatures"));

    private final String dialogueKey;
    private final int color;

    HypnosisProfile(String dialogueKey, int color) {
        this.dialogueKey = dialogueKey;
        this.color = color;
    }

    public String dialogueKey() {
        return dialogueKey;
    }

    public int color() {
        return color;
    }

    public static HypnosisProfile of(Entity entity) {
        ResourceLocation id = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        if (id == null) {
            return GENERIC;
        }
        String path = id.getPath();
        if (path.equals("latex_hypno_cat")) {
            return HYPNO_CAT;
        }
        if (path.equals("luminarctic_leopard_male")
                || path.equals("luminarctic_leopard_female")) {
            return LUMINARCTIC_LEOPARD;
        }
        if (path.equals("latex_kayla_shark")) {
            return KAYLA_SHARK;
        }
        if (path.equals("experiment_10_boss")) {
            return EXPERIMENT_10_BOSS;
        }
        if (path.equals("experiment_10")) {
            return EXPERIMENT_10;
        }
        return GENERIC;
    }

    public static boolean isHypnoticCreature(Entity entity) {
        if (entity instanceof ChangedEntity creature
                && !CreatureSocialProfile.allowsSynergySystems(creature)) {
            return false;
        }
        return entity.getType().is(HYPNOTIC_CREATURES) || of(entity) != GENERIC;
    }

    /** Only the hypno cat currently initiates Synergy's hypnosis/QTE flow. */
    public static boolean activelyUsesHypnosis(Entity entity) {
        return of(entity) == HYPNO_CAT;
    }
}
