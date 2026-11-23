package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleCatModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.LatexManulModel;
import net.parkabird.changedsynergy.client.model.SynergyFemaleArmor;
import net.parkabird.changedsynergy.entity.AbstractLatexManul;

public final class LatexManulRenderer<T extends AbstractLatexManul>
        extends AdvancedHumanoidRenderer<T, LatexManulModel<T>> {
    private static final ResourceLocation GREY_MALE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_manul.png");
    private static final ResourceLocation ORANGE_MALE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_manul_orange.png");
    private static final ResourceLocation GREY_FEMALE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_manul_female.png");
    private static final ResourceLocation ORANGE_FEMALE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_manul_female_orange.png");

    private final boolean female;

    public LatexManulRenderer(EntityRendererProvider.Context context, boolean female) {
        super(
                context,
                new LatexManulModel<>(context.bakeLayer(
                        female ? LatexManulModel.FEMALE_LAYER : LatexManulModel.MALE_LAYER)),
                female ? SynergyFemaleArmor.REGULAR : ArmorLatexMaleCatModel.MODEL_SET,
                0.5F);
        this.female = female;
        addLayer(new LatexParticlesLayer<>(this, getModel()));
        addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        addLayer(new ManulEyesLayer<>(this));
        addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override
    public ResourceLocation getTextureLocation(T entity) {
        boolean orange = entity.isOrangeMorph();
        if (female) {
            return orange ? ORANGE_FEMALE : GREY_FEMALE;
        }
        return orange ? ORANGE_MALE : GREY_MALE;
    }
}
