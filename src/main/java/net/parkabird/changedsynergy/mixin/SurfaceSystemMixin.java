package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.init.ChangedBlocks;
import net.minecraft.world.level.levelgen.SurfaceRules;
import net.minecraft.world.level.levelgen.SurfaceSystem;
import net.parkabird.changedsynergy.ChangedSynergyConfig;
import net.parkabird.changedsynergy.world.LatexTerritoryBiomes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Restores the latex-covered surface used by the 1.18.2 biomes. The old
 * covered-dirt block state no longer exists, so its 1.20.1 equivalent uses the
 * corresponding solid latex block as the exposed biome surface.
 */
@Mixin(SurfaceSystem.class)
public abstract class SurfaceSystemMixin {
    @Unique
    private static volatile SurfaceRules.RuleSource changedSynergy$darkLatexSurface;
    @Unique
    private static volatile SurfaceRules.RuleSource changedSynergy$whiteLatexSurface;

    @ModifyVariable(
            method = {"buildSurface", "m_224648_"},
            at = @At("HEAD"),
            argsOnly = true,
            ordinal = 0,
            remap = false)
    private SurfaceRules.RuleSource changedSynergy$restoreLatexSurfaces(
            SurfaceRules.RuleSource original) {
        if (!ChangedSynergyConfig.COMMON.enableLatexBiomes.get()) {
            return original;
        }
        SurfaceRules.RuleSource darkLatex = changedSynergy$darkLatexSurface;
        if (darkLatex == null) {
            darkLatex = SurfaceRules.ifTrue(
                    SurfaceRules.isBiome(LatexTerritoryBiomes.DARK_LATEX_PLAINS),
                    SurfaceRules.ifTrue(
                            SurfaceRules.ON_FLOOR,
                            SurfaceRules.state(
                                    ChangedBlocks.DARK_LATEX_BLOCK
                                            .get()
                                            .defaultBlockState())));
            changedSynergy$darkLatexSurface = darkLatex;
        }
        SurfaceRules.RuleSource whiteLatex = changedSynergy$whiteLatexSurface;
        if (whiteLatex == null) {
            whiteLatex = SurfaceRules.ifTrue(
                    SurfaceRules.isBiome(LatexTerritoryBiomes.WHITE_LATEX_FOREST),
                    SurfaceRules.ifTrue(
                            SurfaceRules.ON_FLOOR,
                            SurfaceRules.state(
                                    ChangedBlocks.WHITE_LATEX_BLOCK
                                            .get()
                                            .defaultBlockState())));
            changedSynergy$whiteLatexSurface = whiteLatex;
        }
        return SurfaceRules.sequence(darkLatex, whiteLatex, original);
    }
}
