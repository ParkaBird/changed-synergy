package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.AdditionalEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.DoubleItemInHandLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TaurChestPackLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexCentaurLowerModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleTaurUpperModel;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorModelPicker;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.layers.SaddleLayer;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.LatexSpidtaurModel;
import net.parkabird.changedsynergy.entity.LatexSpidtaur;

public final class LatexSpidtaurRenderer
        extends AdvancedHumanoidRenderer<LatexSpidtaur, LatexSpidtaurModel> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_spidtaur.png");
    private static final ResourceLocation SADDLE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_spidtaur_saddle.png");

    public LatexSpidtaurRenderer(EntityRendererProvider.Context context) {
        super(
                context,
                new LatexSpidtaurModel(context.bakeLayer(LatexSpidtaurModel.LAYER_LOCATION)),
                ArmorModelPicker.centaur(
                        context.getModelSet(),
                        ArmorLatexMaleTaurUpperModel.MODEL_SET,
                        ArmorLatexCentaurLowerModel.MODEL_SET_WITH_TORSO),
                0.7F);
        addLayer(new DoubleItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new LatexParticlesLayer<>(this, getModel()));
        addLayer(CustomEyesLayer.builder(this, context.getModelSet())
                .withSclera(Color3.fromInt(0x1B1B1B))
                .build());
        // Reuse Stiger side-eye atlas; head UV keeps the same additional-eye reserve.
        addLayer(AdditionalEyesLayer.builder(this, context.getModelSet())
                .withSclera(Color3.fromInt(0x1B1B1B))
                .build(ResourceLocation.fromNamespaceAndPath("changed", "latex_stiger")));
        addLayer(new SaddleLayer<>(this, getModel(), SADDLE_TEXTURE));
        addLayer(new TaurChestPackLayer<>(this, context.getModelSet()));
        addLayer(TransfurCapeLayer.shortCape(this, context.getModelSet()));
        addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(LatexSpidtaur entity) {
        return TEXTURE;
    }
}