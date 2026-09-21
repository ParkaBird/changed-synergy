package net.parkabird.changedsynergy.mixin;

import javax.annotation.Nullable;
import net.ltxprogrammer.changed.world.features.structures.SurfaceNBTPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Seats the Changed beehive one block deeper so its doorway meets the ground. */
@Mixin(value = SurfaceNBTPiece.class, remap = false)
public abstract class BeehivePlacementMixin {
    private static final ResourceLocation BEEHIVE =
            ResourceLocation.fromNamespaceAndPath("changed", "beehive1");

    @Shadow
    @Final
    private ResourceLocation templateName;

    @Shadow
    @Final
    @Mutable
    private BlockPos generationPosition;

    @Inject(
            method = "<init>(Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/resources/ResourceLocation;"
                    + "Lnet/minecraft/world/level/levelgen/structure/"
                    + "Structure$GenerationContext;)V",
            at = @At("RETURN"),
            remap = false)
    private void changedSynergy$lowerBeehive(
            ResourceLocation structureNBT,
            @Nullable ResourceLocation lootTable,
            Structure.GenerationContext context,
            CallbackInfo callback) {
        if (!BEEHIVE.equals(templateName)) {
            return;
        }
        generationPosition = generationPosition.below();
        ((StructurePiece)(Object)this).move(0, -1, 0);
    }
}
