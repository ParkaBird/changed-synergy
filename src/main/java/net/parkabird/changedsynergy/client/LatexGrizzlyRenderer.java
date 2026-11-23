package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.model.GrizzlyArmor;
import net.parkabird.changedsynergy.client.model.LatexGrizzlyModel;
import net.parkabird.changedsynergy.entity.LatexGrizzly;

public final class LatexGrizzlyRenderer
        extends AdvancedHumanoidRenderer<LatexGrizzly, LatexGrizzlyModel<LatexGrizzly>> {
    public static final float PROPORTIONAL_SCALE = 1.18F;
    public static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/latex_grizzly.png");

    public LatexGrizzlyRenderer(EntityRendererProvider.Context context) {
        super(context, new LatexGrizzlyModel<>(context.bakeLayer(LatexGrizzlyModel.LAYER_LOCATION)),
                GrizzlyArmor.SET, 0.62F);
        addLayer(new LatexParticlesLayer<>(this, getModel()));
        addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        addLayer(new CustomEyesLayer<>(this, context.getModelSet()));
        addLayer(GasMaskLayer.forSnouted(this, context.getModelSet()));
    }

    @Override
    protected void scale(LatexGrizzly entity, PoseStack pose, float partialTicks) {
        // LivingEntityRenderer translates by (0, -1.501, 0) AFTER scale();
        // uniform scaling here already keeps feet planted on Y=0 without double translation.
        pose.scale(PROPORTIONAL_SCALE, PROPORTIONAL_SCALE, PROPORTIONAL_SCALE);
    }

    @Override
    public ResourceLocation getTextureLocation(LatexGrizzly entity) {
        return TEXTURE;
    }
}
