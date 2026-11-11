package net.parkabird.changedsynergy.client;

import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.*;
import net.parkabird.changedsynergy.client.model.EagleOwlArmor;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.client.model.LatexEagleOwlModel;
import net.parkabird.changedsynergy.entity.LatexEagleOwl;

public final class LatexEagleOwlRenderer extends AdvancedHumanoidRenderer<LatexEagleOwl,LatexEagleOwlModel> {
    public LatexEagleOwlRenderer(EntityRendererProvider.Context c){super(c,new LatexEagleOwlModel(c.bakeLayer(LatexEagleOwlModel.LAYER)),EagleOwlArmor.SET,.5F);addLayer(new LatexParticlesLayer<>(this,getModel()));addLayer(TransfurCapeLayer.normalCape(this,c.getModelSet()));addLayer(new EagleOwlEyesLayer(this));}
    @Override public ResourceLocation getTextureLocation(LatexEagleOwl owl){return ResourceLocation.parse("changed_synergy:textures/latex_eagle_owl.png");}
}
