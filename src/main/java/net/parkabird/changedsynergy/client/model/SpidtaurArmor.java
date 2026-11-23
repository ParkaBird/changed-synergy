package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.LowerTorsoedModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexCentaurLowerModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleTaurUpperModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorModelSet;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemStack;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Syncs Spidtaur armor directly to the wearer's final DoubleArm upper-body and taur lower-body poses. */
public final class SpidtaurArmor {
    private SpidtaurArmor() {}

    public static final ArmorModelSet<ChangedEntity, Upper> UPPER_SET = ArmorModelSet.of(
            ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "spidtaur_upper_armor"),
            ArmorLatexMaleTaurUpperModel::createArmorLayer,
            Upper::new);

    public static final ArmorModelSet<ChangedEntity, Lower> LOWER_SET = ArmorModelSet.of(
            ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "spidtaur_lower_armor"),
            ArmorLatexCentaurLowerModel::createArmorLayer,
            Lower::new);

    public static final class Upper extends ArmorLatexMaleTaurUpperModel<ChangedEntity> {
        private Upper(ModelPart root, ArmorModel layer) {
            super(root, layer);
        }

        @Override
        public void renderForSlot(ChangedEntity entity, RenderLayerParent<? super ChangedEntity, ?> parent,
                ItemStack stack, EquipmentSlot slot, PoseStack pose, VertexConsumer buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            if (parent.getModel() instanceof AdvancedHumanoidModel<?> body) {
                getHead().copyFrom(body.getHead());
                getTorso().copyFrom(body.getTorso());
                for (HumanoidArm side : HumanoidArm.values()) {
                    getArm(side).copyFrom(body.getArm(side));
                }
            }
            super.renderForSlot(entity, parent, stack, slot, pose, buffer, light, overlay, red, green, blue, alpha);
        }
    }

    public static final class Lower extends ArmorLatexCentaurLowerModel<ChangedEntity> {
        private Lower(ModelPart root, ArmorModel layer) {
            super(root, layer, true);
        }

        @Override
        public void renderForSlot(ChangedEntity entity, RenderLayerParent<? super ChangedEntity, ?> parent,
                ItemStack stack, EquipmentSlot slot, PoseStack pose, VertexConsumer buffer, int light, int overlay,
                float red, float green, float blue, float alpha) {
            if (parent.getModel() instanceof AdvancedHumanoidModel<?> body) {
                ModelPart torso = getTorso();
                if (torso != null) torso.copyFrom(body.getTorso());
                if (body instanceof LowerTorsoedModel lowerBody) {
                    getLowerTorso().copyFrom(lowerBody.getLowerTorso());
                }
            }
            super.renderForSlot(entity, parent, stack, slot, pose, buffer, light, overlay, red, green, blue, alpha);
        }
    }
}
