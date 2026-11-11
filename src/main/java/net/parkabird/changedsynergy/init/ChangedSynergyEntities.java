package net.parkabird.changedsynergy.init;

import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.ltxprogrammer.changed.init.ChangedItems;
import net.ltxprogrammer.changed.init.ChangedTabs;
import net.ltxprogrammer.changed.item.Syringe;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.entity.CreatureFishingHookVisual;
import net.parkabird.changedsynergy.entity.LatexSilkMoth;
import net.parkabird.changedsynergy.entity.LatexSwordfish;
import net.parkabird.changedsynergy.entity.LatexMuntjac;
import net.parkabird.changedsynergy.entity.LatexMuntjacFemale;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;

/** Entity types owned by Synergy. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ChangedSynergyEntities {
    public static final DeferredRegister<EntityType<?>> REGISTRY =
            DeferredRegister.create(
                    ForgeRegistries.ENTITY_TYPES,
                    ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<EntityType<net.parkabird.changedsynergy.entity.LatexJerboa>> LATEX_JERBOA =
            REGISTRY.register("latex_jerboa",()->EntityType.Builder.<net.parkabird.changedsynergy.entity.LatexJerboa>of(net.parkabird.changedsynergy.entity.LatexJerboa::new,MobCategory.MONSTER)
                    .sized(.55F,1.7F).clientTrackingRange(10).build("changed_synergy:latex_jerboa"));

    public static final RegistryObject<EntityType<net.parkabird.changedsynergy.entity.LatexEagleOwl>> LATEX_EAGLE_OWL =
            REGISTRY.register("latex_eagle_owl",()->EntityType.Builder.<net.parkabird.changedsynergy.entity.LatexEagleOwl>of(
                    net.parkabird.changedsynergy.entity.LatexEagleOwl::new,MobCategory.MONSTER)
                    .sized(.7F,1.93F).clientTrackingRange(10).build(ChangedSynergyMod.MOD_ID+":latex_eagle_owl"));

    public static final RegistryObject<EntityType<LatexSilkMoth>> LATEX_SILK_MOTH =
            REGISTRY.register("latex_silk_moth", () -> EntityType.Builder
                    .<LatexSilkMoth>of(LatexSilkMoth::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.93F)
                    .clientTrackingRange(10)
                    .build(ChangedSynergyMod.MOD_ID + ":latex_silk_moth"));

    public static final RegistryObject<EntityType<LatexSwordfish>> LATEX_SWORDFISH =
            REGISTRY.register("latex_swordfish", () -> EntityType.Builder
                    .<LatexSwordfish>of(LatexSwordfish::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.93F).clientTrackingRange(10)
                    .build(ChangedSynergyMod.MOD_ID + ":latex_swordfish"));

    public static final RegistryObject<EntityType<LatexMuntjac>> LATEX_MUNTJAC =
            REGISTRY.register("latex_muntjac", () -> EntityType.Builder
                    .<LatexMuntjac>of(LatexMuntjac::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.93F).clientTrackingRange(10)
                    .build(ChangedSynergyMod.MOD_ID + ":latex_muntjac"));

    public static final RegistryObject<EntityType<LatexMuntjacFemale>> LATEX_MUNTJAC_FEMALE =
            REGISTRY.register("latex_muntjac_female", () -> EntityType.Builder
                    .<LatexMuntjacFemale>of(LatexMuntjacFemale::new, MobCategory.MONSTER)
                    .sized(0.7F, 1.93F).clientTrackingRange(10)
                    .build(ChangedSynergyMod.MOD_ID + ":latex_muntjac_female"));

    public static final RegistryObject<EntityType<LatexSpottedHyena>> LATEX_SPOTTED_HYENA_MALE =
            REGISTRY.register("latex_spotted_hyena_male", () -> EntityType.Builder
                    .<LatexSpottedHyena>of(LatexSpottedHyena::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.93F).clientTrackingRange(10)
                    .build(ChangedSynergyMod.MOD_ID + ":latex_spotted_hyena_male"));

    public static final RegistryObject<EntityType<LatexSpottedHyena>> LATEX_SPOTTED_HYENA_FEMALE =
            REGISTRY.register("latex_spotted_hyena_female", () -> EntityType.Builder
                    .<LatexSpottedHyena>of(LatexSpottedHyena::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.93F).clientTrackingRange(10)
                    .build(ChangedSynergyMod.MOD_ID + ":latex_spotted_hyena_female"));

    public static final RegistryObject<EntityType<CreatureFishingHookVisual>>
            CREATURE_FISHING_HOOK = REGISTRY.register(
                    "creature_fishing_hook",
                    () -> EntityType.Builder
                            .<CreatureFishingHookVisual>of(
                                    CreatureFishingHookVisual::new,
                                    MobCategory.MISC)
                            .sized(0.25F, 0.25F)
                            .clientTrackingRange(8)
                            .updateInterval(1)
                            .build(ChangedSynergyMod.MOD_ID
                                    + ":creature_fishing_hook"));

    private ChangedSynergyEntities() {
    }

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(LATEX_JERBOA.get(),ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_EAGLE_OWL.get(), ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_SILK_MOTH.get(), ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_SWORDFISH.get(), ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_MUNTJAC.get(), ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_MUNTJAC_FEMALE.get(), ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_SPOTTED_HYENA_MALE.get(), ChangedEntity.createLatexAttributes().build());
        event.put(LATEX_SPOTTED_HYENA_FEMALE.get(), ChangedEntity.createLatexAttributes().build());
    }

    @SubscribeEvent
    public static void registerWeaponAttributes(net.minecraftforge.event.entity.EntityAttributeModificationEvent event) {
        var solvent = ForgeRegistries.ATTRIBUTES.getValue(
                ResourceLocation.fromNamespaceAndPath("changed_addon", "latex_solvent_damage_multiplier"));
        if (solvent == null) return;
        // Addon supplies this weapon attribute only to players. Armed mobs need the same native modifier.
        for (var type : event.getTypes()) if (type != EntityType.PLAYER && !event.has(type, solvent)) event.add(type, solvent);
    }

    @SubscribeEvent
    public static void registerSpawnPlacements(SpawnPlacementRegisterEvent event) {
        event.register(LATEX_JERBOA.get(),SpawnPlacements.Type.ON_GROUND,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,ChangedEntity::checkEntitySpawnRules,SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_EAGLE_OWL.get(),SpawnPlacements.Type.ON_GROUND,Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                ChangedEntity::checkEntitySpawnRules,SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_SPOTTED_HYENA_MALE.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ChangedEntity::checkEntitySpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_SPOTTED_HYENA_FEMALE.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ChangedEntity::checkEntitySpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_MUNTJAC_FEMALE.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ChangedEntity::checkEntitySpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_MUNTJAC.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ChangedEntity::checkEntitySpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_SWORDFISH.get(), SpawnPlacements.Type.IN_WATER,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                LatexSwordfish::checkSwordfishSpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(LATEX_SILK_MOTH.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ChangedEntity::checkEntitySpawnRules,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    @SubscribeEvent
    public static void registerColors(FMLCommonSetupEvent event) {
        event.enqueueWork(()->ChangedEntities.registerEntityColor(LATEX_JERBOA.getId(),0xC6A676,0xEEE6D4));
        event.enqueueWork(()->ChangedEntities.registerEntityColor(LATEX_EAGLE_OWL.getId(),0x9B784E,0xDED0AF));
        event.enqueueWork(() -> ChangedEntities.registerEntityColor(
                ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "latex_silk_moth"),
                0xEADCC6, 0xB19B7A));
        event.enqueueWork(() -> ChangedEntities.registerEntityColor(
                LATEX_SWORDFISH.getId(), 0x294960, 0xB7CAD3));
        event.enqueueWork(() -> ChangedEntities.registerEntityColor(
                LATEX_MUNTJAC.getId(), 0xB78A5D, 0xE6D4B4));
        event.enqueueWork(() -> ChangedEntities.registerEntityColor(
                LATEX_MUNTJAC_FEMALE.getId(), 0xB78A5D, 0xE6D4B4));
        event.enqueueWork(() -> ChangedEntities.registerEntityColor(
                LATEX_SPOTTED_HYENA_MALE.getId(), 0xB18B58, 0x3D302A));
        event.enqueueWork(() -> ChangedEntities.registerEntityColor(
                LATEX_SPOTTED_HYENA_FEMALE.getId(), 0xB18B58, 0x3D302A));
    }

    @SubscribeEvent
    public static void addSpawnEgg(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(ChangedSynergyItems.LATEX_JERBOA_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_EAGLE_OWL_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_SILK_MOTH_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_SWORDFISH_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_MUNTJAC_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_MUNTJAC_FEMALE_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_SPOTTED_HYENA_MALE_SPAWN_EGG);
            event.accept(ChangedSynergyItems.LATEX_SPOTTED_HYENA_FEMALE_SPAWN_EGG);
        }
        if (event.getTabKey().location().equals(ChangedTabs.TAB_CHANGED_CLOTHING.getId())) {
            event.accept(ChangedSynergyItems.DESERT_NOMAD_OUTFIT);
            event.accept(ChangedSynergyItems.EAGLE_OWL_AVIATOR_OUTFIT);
            event.accept(ChangedSynergyItems.FOREST_CANOPY_OUTFIT);
            var outfit = new ItemStack(ChangedSynergyItems.HYENA_TRAILCLOTH_OUTFIT.get());
            ItemStack clothesAnchor = null;
            for (var entry : event.getEntries()) if (entry.getKey().is(ChangedItems.WETSUIT.get())) clothesAnchor = entry.getKey();
            if (clothesAnchor == null) event.accept(outfit);
            else event.getEntries().putAfter(clothesAnchor, outfit, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
        }
        if (event.getTabKey().location().equals(ChangedTabs.TAB_CHANGED_ITEMS.getId())) {
            for (var form : java.util.List.of(ChangedSynergyTransfurVariants.LATEX_JERBOA.getId(),ChangedSynergyTransfurVariants.LATEX_EAGLE_OWL.getId(),ChangedSynergyTransfurVariants.LATEX_SILK_MOTH.getId(),
                    ChangedSynergyTransfurVariants.LATEX_SWORDFISH.getId(),
                    ChangedSynergyTransfurVariants.LATEX_MUNTJAC.getId(),
                    ChangedSynergyTransfurVariants.LATEX_MUNTJAC_FEMALE.getId(),
                    ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_MALE.getId(),
                    ChangedSynergyTransfurVariants.LATEX_SPOTTED_HYENA_FEMALE.getId())) {
                for (var item : java.util.List.of(ChangedItems.LATEX_SYRINGE.get(), ChangedItems.LATEX_FLASK.get(), ChangedItems.LATEX_TIPPED_ARROW.get())) {
                    ItemStack anchor = null;
                    for (var existing : event.getEntries()) if (existing.getKey().is(item)) anchor = existing.getKey();
                    var stack = Syringe.setPureVariant(new ItemStack(item), form);
                    if (anchor == null) event.accept(stack);
                    else event.getEntries().putAfter(anchor, stack, net.minecraft.world.item.CreativeModeTab.TabVisibility.PARENT_AND_SEARCH_TABS);
                }
            }
        }
    }
}
