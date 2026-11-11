package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleWolfModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.client.model.LatexSpottedHyenaModel;
import net.parkabird.changedsynergy.entity.LatexSpottedHyena;

public final class LatexSpottedHyenaRenderer
        extends AdvancedHumanoidRenderer<LatexSpottedHyena, LatexSpottedHyenaModel<LatexSpottedHyena>> {
    private final ResourceLocation texture;

    public LatexSpottedHyenaRenderer(EntityRendererProvider.Context context, boolean female) {
        super(context, new LatexSpottedHyenaModel<>(context.bakeLayer(female
                        ? LatexSpottedHyenaModel.FEMALE_LAYER : LatexSpottedHyenaModel.MALE_LAYER), female),
                female ? net.parkabird.changedsynergy.client.model.SynergyFemaleArmor.HYENA : ArmorLatexMaleWolfModel.MODEL_SET, 0.5F);
        texture = ResourceLocation.fromNamespaceAndPath("changed_synergy", "textures/latex_spotted_hyena_" + (female ? "female" : "male") + ".png");
        addLayer(new LatexParticlesLayer<>(this, getModel()));
        addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        addLayer(new CustomEyesLayer<>(this, context.getModelSet()));
        addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override public ResourceLocation getTextureLocation(LatexSpottedHyena entity) { return texture; }
}
