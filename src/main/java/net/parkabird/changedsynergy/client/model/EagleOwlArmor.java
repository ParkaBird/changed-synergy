package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.*;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The owl shares Crow armor geometry, with its final aerial and perch poses. */
public final class EagleOwlArmor extends ArmorLatexMaleBirdModel<ChangedEntity> {
    public static final ArmorModelSet<ChangedEntity, EagleOwlArmor> SET = ArmorModelSet.of(
            ResourceLocation.parse("changed_synergy:eagle_owl_armor"), ArmorLatexMaleBirdModel::createArmorLayer, EagleOwlArmor::new);
    private EagleOwlArmor(ModelPart root, ArmorModel layer) { super(root, layer); }
    @Override public void renderForSlot(ChangedEntity entity, RenderLayerParent<? super ChangedEntity, ?> parent,
            ItemStack stack, EquipmentSlot slot, PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        if (parent.getModel() instanceof AdvancedHumanoidModel<?> body) ArmorPoseFit.copy(this, body);
        super.renderForSlot(entity, parent, stack, slot, pose, buffer, light, overlay, red, green, blue, alpha);
    }
}
