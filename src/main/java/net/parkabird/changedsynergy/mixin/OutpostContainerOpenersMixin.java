package net.parkabird.changedsynergy.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.ContainerOpenersCounter;
import net.parkabird.changedsynergy.ai.PlayerOutpostStorageAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ContainerOpenersCounter.class)
public abstract class OutpostContainerOpenersMixin {
    @Inject(method = {"getOpenCount(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I",
            "m_155457_(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)I"},
            at = @At("RETURN"), cancellable = true, remap = false, require = 1)
    private void synergy$includeCampVisitors(Level level, BlockPos pos, CallbackInfoReturnable<Integer> result) {
        result.setReturnValue(result.getReturnValue() + PlayerOutpostStorageAccess.openers(level, pos));
    }
}
