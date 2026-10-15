package net.parkabird.changedsynergy.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;

/** Supplies the mapped-name alias used by JEI 221's input dispatcher. */
@Pseudo
@Mixin(targets = "mezz.jei.common.input.IMouseOverable", remap = false)
public interface JeiMouseOverInterfaceMixin {}
