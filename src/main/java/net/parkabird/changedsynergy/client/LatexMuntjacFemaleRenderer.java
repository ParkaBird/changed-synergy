package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.parkabird.changedsynergy.client.model.SynergyFemaleArmor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.LatexMuntjacFemaleModel;
import net.parkabird.changedsynergy.entity.LatexMuntjacFemale;

public final class LatexMuntjacFemaleRenderer
        extends AdvancedHumanoidRenderer<LatexMuntjacFemale, LatexMuntjacFemaleModel<LatexMuntjacFemale>> {
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_muntjac_female.png");

    public LatexMuntjacFemaleRenderer(EntityRendererProvider.Context context) {
        super(context, new LatexMuntjacFemaleModel<>(context.bakeLayer(LatexMuntjacFemaleModel.LAYER_LOCATION)),
                SynergyFemaleArmor.SLIM, 0.5F);
        addLayer(new LatexParticlesLayer<>(this, getModel()));
        addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        addLayer(new CustomEyesLayer<>(this, context.getModelSet()));
        addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override public ResourceLocation getTextureLocation(LatexMuntjacFemale entity) { return TEXTURE; }
}
