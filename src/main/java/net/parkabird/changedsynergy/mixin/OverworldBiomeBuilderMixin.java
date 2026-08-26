package net.parkabird.changedsynergy.mixin;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Forge 1.20.1 records additional biome keys but no longer assigns climate
 * points to them. Partition matching vanilla points using the climate ranges
 * from Changed 0.13.1 so the migrated biomes generate reliably.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {
    @ModifyVariable(
            method = {"addBiomes", "m_187175_"},
            at = @At("HEAD"),
            argsOnly = true,
            remap = false)
    private Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>>
            changedSynergy$placeLegacyLatexBiomes(
                    Consumer<Pair<
                                    Climate.ParameterPoint,
                                    ResourceKey<Biome>>>
                            downstream) {
        return entry -> {
            Climate.ParameterPoint point = entry.getFirst();
            ResourceKey<Biome> selected = entry.getSecond();
            if (changedSynergy$matchesSharedRange(point)) {
                if (changedSynergy$inside(
                        point.temperature(), -0.45F, -0.15F)) {
                    selected = LatexTerritoryBiomes.WHITE_LATEX_FOREST;
                } else if (changedSynergy$inside(
                                point.temperature(), -0.15F, 0.2F)
                        || changedSynergy$inside(
                                point.temperature(), 0.2F, 0.55F)) {
                    selected = LatexTerritoryBiomes.DARK_LATEX_PLAINS;
                }
            }
            downstream.accept(Pair.of(point, selected));
        };
    }

    @Unique
    private static boolean changedSynergy$matchesSharedRange(
            Climate.ParameterPoint point) {
        return changedSynergy$inside(
                        point.continentalness(), 0.03F, 1.0F)
                && (changedSynergy$inside(
                                point.erosion(), -1.0F, -0.78F)
                        || changedSynergy$inside(
                                point.erosion(), -0.78F, -0.375F))
                && (changedSynergy$isPoint(point.depth(), 0.0F)
                        || changedSynergy$isPoint(point.depth(), 1.0F))
                && changedSynergy$inside(
                        point.weirdness(), -0.05F, 0.4F);
    }

    @Unique
    private static boolean changedSynergy$inside(
            Climate.Parameter parameter, float minimum, float maximum) {
        return parameter.min() >= Climate.quantizeCoord(minimum) - 1L
                && parameter.max() <= Climate.quantizeCoord(maximum) + 1L;
    }

    @Unique
    private static boolean changedSynergy$isPoint(
            Climate.Parameter parameter, float value) {
        long coordinate = Climate.quantizeCoord(value);
        return parameter.min() == coordinate
                && parameter.max() == coordinate;
    }
}
