package net.parkabird.changedsynergy.origin;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedRegistry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.parkabird.changedsynergy.ai.CreatureSocialProfile;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.ai.LightFactionGroup;

/** Playable player forms, classified with the same tags as NPCs. */
public final class NativeOriginCatalog {
    private NativeOriginCatalog() {}

    public record Entry(ResourceLocation form, String faction, String region) {
        public TransfurVariant<?> variant() {
            return ChangedRegistry.TRANSFUR_VARIANT.get().getValue(form);
        }
        public String factionKey() {
            return "light".equals(faction) ? "faction.changed_synergy.light." + region
                    : "faction.changed_synergy." + faction;
        }
    }

    public static boolean allowed(TransfurVariant<?> variant) {
        if (variant == null || variant.getFormId() == null) return false;
        if (ChangedSynergyConfig.COMMON.disabledOriginForms.get().contains(variant.getFormId().toString())) return false;
        String path = variant.getFormId().getPath();
        ResourceLocation entityId = net.minecraftforge.registries.ForgeRegistries.ENTITY_TYPES.getKey(variant.getEntityType());
        if (entityId == null) return false;
        boolean original = "changed".equals(variant.getFormId().getNamespace()) && "changed".equals(entityId.getNamespace());
        return (original || ChangedSynergyConfig.COMMON.includeModdedOriginSpecies.get())
                && !path.contains("partial") && !path.contains("custom")
                && !path.contains("benign") && !path.contains("special") && !path.contains("boss")
                && !entityId.getPath().contains("boss")
                && !variant.getEntityType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("forge:bosses")))
                && !variant.getEntityType().is(TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse("minecraft:bosses")))
                && !Set.of("form_puro", "form_colin", "form_dr_k", "form_special").contains(path)
                && !CreatureSocialProfile.isPermanentlyExcluded(entityId)
                && TransfurVariant.getPublicTransfurVariants().anyMatch(candidate -> candidate == variant);
    }

    public static List<Entry> entries(RegistryAccess registries) {
        return TransfurVariant.getPublicTransfurVariants().filter(NativeOriginCatalog::allowed)
                .map(variant -> describe(registries, variant))
                .sorted(Comparator.comparing(Entry::faction).thenComparing(Entry::region)
                        .thenComparing(entry -> entry.form().toString())).toList();
    }

    /** Also resolves saved birth identities whose form is no longer selectable. */
    public static Entry describe(RegistryAccess registries, TransfurVariant<?> variant) {
        HunterFaction faction = HunterFaction.of(variant.getEntityType());
        if (faction == null) faction = HunterFaction.LIGHT;
        String region = faction == HunterFaction.LIGHT
                ? LightFactionGroup.nativeRegions(registries, variant.getEntityType()).stream()
                        .findFirst().orElse(LightFactionGroup.GENERAL)
                : "";
        return new Entry(variant.getFormId(), faction.id(), region);
    }

    public static Entry find(RegistryAccess registries, ResourceLocation form) {
        return entries(registries).stream().filter(entry -> entry.form().equals(form)).findFirst().orElse(null);
    }

    /** The archive's biological groups also define server-side random choices. */
    public static int categoryOf(Entry entry) {
        if (entry == null) return 0;
        var variant = entry.variant();
        if (variant != null && HunterFaction.isOrganicType(variant.getEntityType())) return 4;
        if (entry.faction().equals("aquatic")) return 5;
        return switch (entry.faction()) {
            case "white" -> 1;
            case "dark" -> 2;
            default -> 3;
        };
    }

    public static ResourceLocation randomRequest(int category) {
        if (category < 0 || category > 5) throw new IllegalArgumentException("Invalid archive category");
        return ResourceLocation.fromNamespaceAndPath("changed_synergy", "random_origin/" + category);
    }

    public static int randomCategory(ResourceLocation request) {
        for (int category = 0; category <= 5; category++)
            if (randomRequest(category).equals(request)) return category;
        return -1;
    }

    /** Null denotes human; callers reject an empty non-human category before drawing. */
    public static ResourceLocation drawRandom(List<Entry> catalog, int category, RandomSource random) {
        if (category < 0 || category > 5) throw new IllegalArgumentException("Invalid archive category");
        var pool = category == 0 ? catalog : catalog.stream().filter(entry -> categoryOf(entry) == category).toList();
        if (pool.isEmpty() && category != 0) throw new IllegalArgumentException("Empty archive category");
        int index = random.nextInt(pool.size() + (category == 0 ? 1 : 0));
        return index == pool.size() ? null : pool.get(index).form();
    }
}
