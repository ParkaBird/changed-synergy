package net.parkabird.changedsynergy.mixin;

import com.mojang.datafixers.util.Pair;
import java.util.function.Consumer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.OverworldBiomeBuilder;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Injects legacy Changed biomes as discrete climate target points into the Overworld
 * multi-noise biome generator, targeting inland flatlands with low, balanced scale.
 */
@Mixin(OverworldBiomeBuilder.class)
public abstract class OverworldBiomeBuilderMixin {
    @Inject(
            method = {"addBiomes", "m_187175_"},
            at = @At("RETURN"),
            remap = false)
    private void changedSynergy$injectLatexBiomes(
            Consumer<Pair<Climate.ParameterPoint, ResourceKey<Biome>>> consumer,
            CallbackInfo ci) {
        if (!ChangedSynergyConfig.COMMON.enableLatexBiomes.get()) {
            return;
        }

        Climate.Parameter humidity = Climate.Parameter.span(-1.0F, 1.0F);
        Climate.Parameter continentalness = Climate.Parameter.span(0.03F, 1.0F);
        Climate.Parameter erosion = Climate.Parameter.span(0.05F, 0.45F);
        Climate.Parameter depth = Climate.Parameter.point(0.0F);
        Climate.Parameter weirdness = Climate.Parameter.span(0.26F, 0.4F);

        // White Latex Forest (Cool flatlands)
        consumer.accept(Pair.of(
                Climate.parameters(
                        Climate.Parameter.span(-0.45F, -0.15F),
                        humidity, continentalness, erosion, depth, weirdness, 0.0F),
                LatexTerritoryBiomes.WHITE_LATEX_FOREST));

        // Dark Latex Plains (Neutral & Warm flatlands)
        consumer.accept(Pair.of(
                Climate.parameters(
                        Climate.Parameter.span(-0.15F, 0.2F),
                        humidity, continentalness, erosion, depth, weirdness, 0.0F),
                LatexTerritoryBiomes.DARK_LATEX_PLAINS));
        consumer.accept(Pair.of(
                Climate.parameters(
                        Climate.Parameter.span(0.2F, 0.55F),
                        humidity, continentalness, erosion, depth, weirdness, 0.0F),
                LatexTerritoryBiomes.DARK_LATEX_PLAINS));
    }
}
