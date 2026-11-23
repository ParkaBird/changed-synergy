package net.parkabird.changedsynergy.mixin;

import net.ltxprogrammer.changed.client.renderer.model.CentaurArmorStandModel;
import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = CentaurArmorStandModel.class, remap = false)
public interface CentaurArmorStandModelAccessor {
    @Accessor(value = "Head", remap = false)
    ModelPart getHeadPart();

    @Accessor(value = "Torso", remap = false)
    ModelPart getTorsoPart();

    @Accessor(value = "LeftArm", remap = false)
    ModelPart getLeftArmPart();

    @Accessor(value = "RightArm", remap = false)
    ModelPart getRightArmPart();
}
