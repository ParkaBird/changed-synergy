package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.layers.GasMaskLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexPartialLayer;
import net.ltxprogrammer.changed.client.renderer.layers.LatexParticlesLayer;
import net.ltxprogrammer.changed.client.renderer.layers.TransfurCapeLayer;
import net.ltxprogrammer.changed.client.renderer.model.armor.ArmorLatexMaleWolfModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;
import net.parkabird.changedsynergy.client.model.WhiteLatexWolfPartialModel;
import net.parkabird.changedsynergy.entity.WhiteLatexWolfPartial;

public class WhiteLatexWolfPartialRenderer
        extends AdvancedHumanoidRenderer<WhiteLatexWolfPartial, WhiteLatexWolfPartialModel> {
    private static final ResourceLocation NORMAL =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "textures/white_latex_wolf_partial.png");
    private static final ResourceLocation NORMAL_SLIM =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "textures/white_latex_wolf_partial_slim.png");
    private static final ResourceLocation OPEN_EYES =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID, "textures/white_latex_wolf_partial_open_eyes.png");
    private static final ResourceLocation OPEN_EYES_SLIM =
            ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID,
                    "textures/white_latex_wolf_partial_open_eyes_slim.png");

    public WhiteLatexWolfPartialRenderer(EntityRendererProvider.Context context, boolean slim) {
        super(
                context,
                WhiteLatexWolfPartialModel.human(context.bakeLayer(
                        slim
                                ? WhiteLatexWolfPartialModel.LAYER_LOCATION_HUMAN_SLIM
                                : WhiteLatexWolfPartialModel.LAYER_LOCATION_HUMAN)),
                ArmorLatexMaleWolfModel.MODEL_SET,
                0.5f);
        var partialModel = new LatexPartialLayer<>(
                this,
                WhiteLatexWolfPartialModel.latex(context.bakeLayer(
                        slim
                                ? WhiteLatexWolfPartialModel.LAYER_LOCATION_LATEX_SLIM
                                : WhiteLatexWolfPartialModel.LAYER_LOCATION_LATEX)),
                entity -> latexTexture(entity, slim));
        this.addLayer(partialModel);
        this.addLayer(new LatexParticlesLayer<>(this)
                .addModel(partialModel.getModel(), partialModel.getTextureFunction()));
        this.addLayer(TransfurCapeLayer.normalCape(this, context.getModelSet()));
        this.addLayer(new GasMaskLayer<>(this, context.getModelSet()));
    }

    public static EntityRendererProvider<WhiteLatexWolfPartial> forModelSize(boolean slim) {
        return context -> new WhiteLatexWolfPartialRenderer(context, slim);
    }

    private static ResourceLocation latexTexture(WhiteLatexWolfPartial entity, boolean slim) {
        boolean adapted = PureWhiteWolfAdaptation.isAdaptedForm(entity);
        if (slim) {
            return adapted ? OPEN_EYES_SLIM : NORMAL_SLIM;
        }
        return adapted ? OPEN_EYES : NORMAL;
    }

    @Override
    public ResourceLocation getTextureLocation(WhiteLatexWolfPartial partial) {
        return partial.getSkinTextureLocation();
    }

    @Override
    public void render(
            WhiteLatexWolfPartial latex,
            float yRot,
            float partialTick,
            PoseStack pose,
            MultiBufferSource bufferSource,
            int packedLight) {
        if (latex.getUnderlyingPlayer() instanceof AbstractClientPlayer clientPlayer) {
            this.model.setModelProperties(clientPlayer);
        } else {
            this.model.defaultModelProperties();
        }
        super.render(latex, yRot, partialTick, pose, bufferSource, packedLight);
    }

    @Override
    protected void scale(WhiteLatexWolfPartial entity, PoseStack pose, float partialTick) {
        pose.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
