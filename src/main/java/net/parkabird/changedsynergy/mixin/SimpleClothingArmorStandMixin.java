package net.parkabird.changedsynergy.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.ltxprogrammer.changed.client.renderer.accessory.SimpleClothingRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.ArmorStandArmorLayer;
import net.ltxprogrammer.changed.client.renderer.model.ArmorStandArmorModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorModel;
import net.ltxprogrammer.changed.data.AccessorySlotContext;
import net.ltxprogrammer.changed.entity.decoration.AbstractArmorStand;
import net.ltxprogrammer.changed.item.Clothing;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Renders main-mod SimpleClothingRenderer outfits on Changed and vanilla ArmorStands. */
@Mixin(value = SimpleClothingRenderer.class, remap = false)
public abstract class SimpleClothingArmorStandMixin {
    @Unique
    private static final Field CHANGED_SYNERGY$RENDERER_LAYERS_FIELD =
            ObfuscationReflectionHelper.findField(LivingEntityRenderer.class, "f_115291_");

    @Shadow @Final protected HumanoidModel<?> clothingModel;
    @Shadow @Final protected Set<SimpleClothingRenderer.ModelComponent> components;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <T extends LivingEntity, M extends EntityModel<T>> void changedSynergy$renderOnArmorStand(
            AccessorySlotContext<T> context,
            PoseStack poseStack,
            RenderLayerParent<T, M> parent,
            MultiBufferSource buffers,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch,
            CallbackInfo ci) {
        ItemStack stack = context.stack();
        if (!(stack.getItem() instanceof Clothing clothing)) {
            return;
        }
        LivingEntity wearer = context.wearer();
        if (!(wearer instanceof ArmorStand)) {
            return;
        }
        ResourceLocation texture = clothing.getTexture(stack, wearer);
        if (texture == null) {
            ci.cancel();
            return;
        }

        if (wearer instanceof AbstractArmorStand stand && parent instanceof LivingEntityRenderer<?, ?> livingParent) {
            ArmorStandArmorLayer<?, ?> armorLayer = findArmorStandLayer(livingParent);
            if (armorLayer != null) {
                renderOnChangedStand(
                        armorLayer, stand, parent, stack, texture,
                        poseStack, buffers, packedLight,
                        limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
                ci.cancel();
            }
            return;
        }

        if (parent.getModel() instanceof HumanoidModel<?> humanModel) {
            renderOnVanillaStand(humanModel, stack, texture, poseStack, buffers, packedLight);
            ci.cancel();
        }
    }

    @Unique
    @SuppressWarnings("unchecked")
    private static ArmorStandArmorLayer<?, ?> findArmorStandLayer(LivingEntityRenderer<?, ?> renderer) {
        List<RenderLayer<?, ?>> layers;
        try {
            Object raw = CHANGED_SYNERGY$RENDERER_LAYERS_FIELD.get(renderer);
            layers = raw instanceof List<?> list ? (List<RenderLayer<?, ?>>) (List<?>) list : Collections.emptyList();
        } catch (IllegalAccessException e) {
            return null;
        }
        for (RenderLayer<?, ?> layer : layers) {
            if (layer instanceof ArmorStandArmorLayer<?, ?> armorLayer) {
                return armorLayer;
            }
        }
        return null;
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private <S extends AbstractArmorStand> void renderOnChangedStand(
            ArmorStandArmorLayer<S, ?> armorLayer,
            AbstractArmorStand rawStand,
            RenderLayerParent parent,
            ItemStack stack,
            ResourceLocation texture,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight,
            float limbSwing,
            float limbSwingAmount,
            float partialTicks,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        S stand = (S) rawStand;
        armorLayer.modelPicker.prepareAndSetupModels(
                stand, limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        for (SimpleClothingRenderer.ModelComponent component : this.components) {
            EquipmentSlot slot = component.renderAs();
            Map<ArmorModel, ? extends ArmorStandArmorModel<S>> modelSet =
                    armorLayer.modelPicker.getModelSetForSlot(stand, slot);
            ArmorStandArmorModel<S> model = modelSet != null ? modelSet.get(component.armorModel()) : null;
            if (model == null) {
                model = armorLayer.modelPicker.getModelForSlot(stand, slot);
            }
            if (model == null) continue;
            model.prepareVisibility(slot, stack);
            VertexConsumer buffer = ItemRenderer.getArmorFoilBuffer(
                    buffers, RenderType.armorCutoutNoCull(texture), false, stack.hasFoil());
            model.renderForSlot(
                    stand, parent, stack, slot, poseStack, buffer,
                    packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    @Unique
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderOnVanillaStand(
            HumanoidModel<?> humanModel,
            ItemStack stack,
            ResourceLocation texture,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        try {
            ((HumanoidModel) humanModel).copyPropertiesTo(this.clothingModel);
            this.clothingModel.setAllVisible(false);
            for (SimpleClothingRenderer.ModelComponent component : this.components) {
                switch (component.renderAs()) {
                    case HEAD -> {
                        this.clothingModel.head.visible = true;
                        this.clothingModel.hat.visible = true;
                    }
                    case CHEST -> {
                        this.clothingModel.body.visible = true;
                        this.clothingModel.rightArm.visible = true;
                        this.clothingModel.leftArm.visible = true;
                    }
                    case LEGS -> {
                        this.clothingModel.body.visible = true;
                        this.clothingModel.rightLeg.visible = true;
                        this.clothingModel.leftLeg.visible = true;
                    }
                    case FEET -> {
                        this.clothingModel.rightLeg.visible = true;
                        this.clothingModel.leftLeg.visible = true;
                    }
                    default -> this.clothingModel.setAllVisible(true);
                }
            }
            VertexConsumer buffer = ItemRenderer.getArmorFoilBuffer(
                    buffers, RenderType.armorCutoutNoCull(texture), false, stack.hasFoil());
            this.clothingModel.renderToBuffer(
                    poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY,
                    1.0F, 1.0F, 1.0F, 1.0F);
        } finally {
            this.clothingModel.setAllVisible(true);
        }
    }
}
