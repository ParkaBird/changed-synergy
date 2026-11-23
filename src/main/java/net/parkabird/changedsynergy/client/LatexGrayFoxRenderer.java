package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleWolfModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.LatexGrayFoxModel;
import net.parkabird.changedsynergy.client.model.SynergyFemaleArmor;
import net.parkabird.changedsynergy.entity.AbstractLatexGrayFox;

public final class LatexGrayFoxRenderer<T extends AbstractLatexGrayFox>
        extends AdvancedHumanoidRenderer<T, LatexGrayFoxModel<T>> {
    private static final ResourceLocation MALE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_gray_fox_male.png");
    private static final ResourceLocation FEMALE_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_gray_fox_female.png");

    private final boolean female;

    public LatexGrayFoxRenderer(EntityRendererProvider.Context context, boolean female) {
        super(
                context,
                new LatexGrayFoxModel<>(context.bakeLayer(
                        female ? LatexGrayFoxModel.FEMALE_LAYER : LatexGrayFoxModel.MALE_LAYER)),
                female ? SynergyFemaleArmor.SLIM : ArmorLatexMaleWolfModel.MODEL_SET,
                0.5F);
        this.female = female;
        addLayer(new LatexParticlesLayer<>(this, getModel()));
        addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        addLayer(new CustomEyesLayer<>(this, context.getModelSet()));
        addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        return female ? FEMALE_TEXTURE : MALE_TEXTURE;
    }
}
