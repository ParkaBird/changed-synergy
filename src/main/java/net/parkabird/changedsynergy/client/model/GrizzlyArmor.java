package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.*;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** Fits heavy-wolf armor geometry to the Grizzly's live humanoid pose. */
public final class GrizzlyArmor extends ArmorLatexHeavyWolfModel<ChangedEntity> {
    public static final ArmorModelSet<ChangedEntity, GrizzlyArmor> SET = ArmorModelSet.of(
            ResourceLocation.fromNamespaceAndPath("changed_synergy", "grizzly_armor"),
            ArmorLatexHeavyWolfModel::createArmorLayer, GrizzlyArmor::new);

    private GrizzlyArmor(ModelPart root, ArmorModel layer) {
        super(root, layer);
    }

    @Override
    public void renderForSlot(ChangedEntity entity, RenderLayerParent<? super ChangedEntity, ?> parent,
            ItemStack stack, EquipmentSlot slot, PoseStack pose, VertexConsumer buffer, int light, int overlay,
            float red, float green, float blue, float alpha) {
        if (parent.getModel() instanceof AdvancedHumanoidModel<?> body) ArmorPoseFit.copy(this, body);
        super.renderForSlot(entity, parent, stack, slot, pose, buffer, light, overlay, red, green, blue, alpha);
    }
}
