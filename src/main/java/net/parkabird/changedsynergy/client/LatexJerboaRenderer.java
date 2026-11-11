package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.*;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexDeerModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.client.model.LatexJerboaModel;
import net.parkabird.changedsynergy.entity.LatexJerboa;

public final class LatexJerboaRenderer extends AdvancedHumanoidRenderer<LatexJerboa,LatexJerboaModel<LatexJerboa>> {
    public LatexJerboaRenderer(EntityRendererProvider.Context context){
        super(context,new LatexJerboaModel<>(context.bakeLayer(LatexJerboaModel.LAYER)),net.parkabird.changedsynergy.client.model.JerboaArmor.SET,.35F);
        addLayer(new LatexParticlesLayer<>(this,getModel()));addLayer(TransfurCapeLayer.normalCape(this,context.getModelSet()));
        addLayer(new CustomEyesLayer<>(this,context.getModelSet()));addLayer(GasMaskLayer.forSnouted(this,context.getModelSet()));
    }
    @Override public ResourceLocation getTextureLocation(LatexJerboa entity){return ResourceLocation.parse("changed_synergy:textures/latex_jerboa.png");}
}
