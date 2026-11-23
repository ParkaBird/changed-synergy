package net.parkabird.changedsynergy.init;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.item.BrokenDarkLatexMaskItem;
import net.parkabird.changedsynergy.item.RepairedDarkLatexMaskItem;
import net.parkabird.changedsynergy.item.HyenaTrailclothItem;

/** Items owned by Synergy. */
public final class ChangedSynergyItems {
    public static final DeferredRegister<Item> REGISTRY = DeferredRegister.create(
            ForgeRegistries.ITEMS, ChangedSynergyMod.MOD_ID);

    public static final RegistryObject<Item> LATEX_JERBOA_SPAWN_EGG=REGISTRY.register("latex_jerboa_spawn_egg",()->new ForgeSpawnEggItem(ChangedSynergyEntities.LATEX_JERBOA,0xC6A676,0xEEE6D4,new Item.Properties()));
    public static final RegistryObject<net.parkabird.changedsynergy.item.DesertOutfitItem> DESERT_NOMAD_OUTFIT=REGISTRY.register("desert_nomad_outfit",()->new net.parkabird.changedsynergy.item.DesertOutfitItem());

    public static final RegistryObject<Item> LATEX_EAGLE_OWL_SPAWN_EGG=REGISTRY.register("latex_eagle_owl_spawn_egg",
            ()->new ForgeSpawnEggItem(ChangedSynergyEntities.LATEX_EAGLE_OWL,0x9B784E,0xDED0AF,new Item.Properties()));
    public static final RegistryObject<net.parkabird.changedsynergy.item.EagleOwlAviatorItem> EAGLE_OWL_AVIATOR_OUTFIT=
            REGISTRY.register("eagle_owl_aviator_outfit",net.parkabird.changedsynergy.item.EagleOwlAviatorItem::new);
    public static final RegistryObject<Item> EAGLE_OWL_PERCH_STAND=REGISTRY.register("eagle_owl_perch_stand",()->new net.minecraft.world.item.BlockItem(ChangedSynergyBlocks.EAGLE_OWL_PERCH_STAND.get(),new Item.Properties()));
    public static final RegistryObject<Item> EAGLE_OWL_PERCH_RAIL=REGISTRY.register("eagle_owl_perch_rail",()->new net.minecraft.world.item.BlockItem(ChangedSynergyBlocks.EAGLE_OWL_PERCH_RAIL.get(),new Item.Properties()));
    public static final RegistryObject<Item> SURVEILLANCE_CAMERA = REGISTRY.register("surveillance_camera",
            () -> new net.minecraft.world.item.BlockItem(ChangedSynergyBlocks.SURVEILLANCE_CAMERA.get(), new Item.Properties()));

    public static final RegistryObject<HyenaTrailclothItem> HYENA_TRAILCLOTH_OUTFIT =
            REGISTRY.register("hyena_trailcloth_outfit", HyenaTrailclothItem::new);
    public static final RegistryObject<net.parkabird.changedsynergy.item.ForestCanopyItem> FOREST_CANOPY_OUTFIT =
            REGISTRY.register("forest_canopy_outfit", net.parkabird.changedsynergy.item.ForestCanopyItem::new);
    public static final RegistryObject<net.parkabird.changedsynergy.item.CaveMinerOutfitItem> CAVE_MINER_OUTFIT =
            REGISTRY.register("cave_miner_outfit", net.parkabird.changedsynergy.item.CaveMinerOutfitItem::new);
    public static final RegistryObject<net.parkabird.changedsynergy.item.FieldCoatItem> FIELD_COAT =
            REGISTRY.register("field_coat", net.parkabird.changedsynergy.item.FieldCoatItem::new);
    public static final RegistryObject<net.parkabird.changedsynergy.item.BurrowShawlItem> BURROW_SHAWL =
            REGISTRY.register("burrow_shawl", net.parkabird.changedsynergy.item.BurrowShawlItem::new);

    public static final RegistryObject<Item> LATEX_SILK_MOTH_SPAWN_EGG = REGISTRY.register(
            "latex_silk_moth_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_SILK_MOTH, 0xEADCC6, 0xB19B7A, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_SWORDFISH_SPAWN_EGG = REGISTRY.register(
            "latex_swordfish_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_SWORDFISH, 0x294960, 0xB7CAD3, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_MUNTJAC_SPAWN_EGG = REGISTRY.register(
            "latex_muntjac_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_MUNTJAC, 0xB78A5D, 0xE6D4B4, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_MUNTJAC_FEMALE_SPAWN_EGG = REGISTRY.register(
            "latex_muntjac_female_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE, 0xB78A5D, 0xE6D4B4, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_SPOTTED_HYENA_MALE_SPAWN_EGG = REGISTRY.register(
            "latex_spotted_hyena_male_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_SPOTTED_HYENA_MALE, 0xB18B58, 0x3D302A, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_SPOTTED_HYENA_FEMALE_SPAWN_EGG = REGISTRY.register(
            "latex_spotted_hyena_female_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_SPOTTED_HYENA_FEMALE, 0xB18B58, 0x3D302A, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_SPIDTAUR_SPAWN_EGG = REGISTRY.register(
            "latex_spidtaur_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_SPIDTAUR, 0x5A534C, 0xE8E2D8, new Item.Properties()));

    public static final RegistryObject<Item> PURE_WHITE_LATEX_WOLF_PARTIAL_SPAWN_EGG = REGISTRY.register(
            "pure_white_latex_wolf_partial_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.PURE_WHITE_LATEX_WOLF_PARTIAL, 0xFFFFFF, 0xFAFAFA, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_GRIZZLY_SPAWN_EGG = REGISTRY.register(
            "latex_grizzly_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_GRIZZLY, 0x6E4931, 0xA8825E, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_MANUL_SPAWN_EGG = REGISTRY.register(
            "latex_manul_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_MANUL, 0x8E8E8E, 0xC9C3B4, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_MANUL_FEMALE_SPAWN_EGG = REGISTRY.register(
            "latex_manul_female_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_MANUL_FEMALE, 0x8E8E8E, 0xC9C3B4, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_GRAY_FOX_SPAWN_EGG = REGISTRY.register(
            "latex_gray_fox_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_GRAY_FOX, 0x7A7E85, 0xC7C9CC, new Item.Properties()));

    public static final RegistryObject<Item> LATEX_GRAY_FOX_FEMALE_SPAWN_EGG = REGISTRY.register(
            "latex_gray_fox_female_spawn_egg", () -> new ForgeSpawnEggItem(
                    ChangedSynergyEntities.LATEX_GRAY_FOX_FEMALE, 0x7A7E85, 0xC7C9CC, new Item.Properties()));

    public static final RegistryObject<Item> BROKEN_DARK_LATEX_MASK = REGISTRY.register(
            "broken_dark_latex_mask",
            () -> new BrokenDarkLatexMaskItem(new Item.Properties()
                    .stacksTo(1)
                    .fireResistant()));

    public static final RegistryObject<Item> REPAIRED_DARK_LATEX_MASK = REGISTRY.register(
            "repaired_dark_latex_mask",
            RepairedDarkLatexMaskItem::new);

    private ChangedSynergyItems() {
    }
}
