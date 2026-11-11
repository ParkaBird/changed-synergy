package net.parkabird.changedsynergy.mixin;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.block.FenceGateBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(FenceGateBlock.class)
public interface FenceGateSoundAccessor {
    @Accessor(value="openSound",remap=false) SoundEvent changedSynergy$openSound();
    @Accessor(value="closeSound",remap=false) SoundEvent changedSynergy$closeSound();
}
