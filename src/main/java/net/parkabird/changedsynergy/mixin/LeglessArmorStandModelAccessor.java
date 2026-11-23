package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.model.LeglessArmorStandModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = LeglessArmorStandModel.class, remap = false)
public interface LeglessArmorStandModelAccessor {
    @Accessor(value = "Head", remap = false)
    ModelPart getHeadPart();

    @Accessor(value = "Torso", remap = false)
    ModelPart getTorsoPart();

    @Accessor(value = "LeftArm", remap = false)
    ModelPart getLeftArmPart();

    @Accessor(value = "RightArm", remap = false)
    ModelPart getRightArmPart();
}
