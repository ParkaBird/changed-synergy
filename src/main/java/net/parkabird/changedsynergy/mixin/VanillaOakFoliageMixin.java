package net.parkabird.changedsynergy.mixin;

import net.minecraft.data.worldgen.features.TreeFeatures;
import net.minecraft.util.valueproviders.ConstantInt;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.feature.configurations.TreeConfiguration;
import net.minecraft.world.level.levelgen.feature.featuresize.TwoLayersFeatureSize;
import net.minecraft.world.level.levelgen.feature.foliageplacers.BlobFoliagePlacer;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.levelgen.feature.trunkplacers.StraightTrunkPlacer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keeps Changed's sparse fruit leaves out of ordinary oak canopies. */
@Mixin(value = TreeFeatures.class, priority = 1500)
public abstract class VanillaOakFoliageMixin {
    @Inject(
            method = {"createStraightBlobTree", "m_195146_"},
            at = @At("RETURN"),
            cancellable = true,
            remap = false)
    private static void changedSynergy$restoreOrdinaryOak(
            Block log,
            Block leaves,
            int baseHeight,
            int heightRandomA,
            int heightRandomB,
            int foliageRadius,
            CallbackInfoReturnable<TreeConfiguration.TreeConfigurationBuilder> callback) {
        if (log != Blocks.OAK_LOG || leaves != Blocks.OAK_LEAVES) {
            return;
        }
        callback.setReturnValue(new TreeConfiguration.TreeConfigurationBuilder(
                BlockStateProvider.simple(log),
                new StraightTrunkPlacer(
                        baseHeight, heightRandomA, heightRandomB),
                BlockStateProvider.simple(leaves),
                new BlobFoliagePlacer(
                        ConstantInt.of(foliageRadius), ConstantInt.of(0), 3),
                new TwoLayersFeatureSize(1, 0, 1)));
    }
}
