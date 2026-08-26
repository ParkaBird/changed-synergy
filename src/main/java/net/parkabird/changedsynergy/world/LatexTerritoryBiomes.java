package net.parkabird.changedsynergy.world;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.BiomeManager;

/** Registers the two legacy Changed overworld territory biomes for 1.20.1. */
public final class LatexTerritoryBiomes {
    public static final ResourceKey<Biome> WHITE_LATEX_FOREST =
            key("white_latex_forest");
    public static final ResourceKey<Biome> DARK_LATEX_PLAINS =
            key("dark_latex_plains");
    private static boolean registered;

    private LatexTerritoryBiomes() {
    }

    public static void register() {
        if (registered) {
            return;
        }
        registered = true;

        // BiomeManager exposes the keys to Forge-aware biome sources. The
        // accompanying mixin restores the climate points used in Changed
        // 0.13.1, which BiomeManager no longer assigns on its own.
        BiomeManager.addAdditionalOverworldBiomes(WHITE_LATEX_FOREST);
        BiomeManager.addAdditionalOverworldBiomes(DARK_LATEX_PLAINS);
    }

    private static ResourceKey<Biome> key(String path) {
        return ResourceKey.create(
                Registries.BIOME,
                ResourceLocation.fromNamespaceAndPath(
                        "changed", path));
    }
}
