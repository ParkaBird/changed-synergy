package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.model.BipedArmorStandModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BipedArmorStandModel.class, remap = false)
public interface BipedArmorStandModelAccessor {
    @Accessor(value = "Head", remap = false)
    ModelPart getHeadPart();

    @Accessor(value = "Torso", remap = false)
    ModelPart getTorsoPart();

    @Accessor(value = "LeftArm", remap = false)
    ModelPart getLeftArmPart();

    @Accessor(value = "RightArm", remap = false)
    ModelPart getRightArmPart();

    @Accessor(value = "LeftLeg", remap = false)
    ModelPart getLeftLegPart();

    @Accessor(value = "RightLeg", remap = false)
    ModelPart getRightLegPart();
}
