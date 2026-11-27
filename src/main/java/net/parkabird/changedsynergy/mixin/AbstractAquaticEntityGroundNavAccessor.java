package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.entity.beast.AbstractAquaticEntity;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractAquaticEntity.class, remap = false)
public interface AbstractAquaticEntityGroundNavAccessor {
    @Accessor("groundNavigation")
    GroundPathNavigation changedSynergy$groundNavigation();
}
