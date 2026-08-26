package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.client.renderer.layers.FirstPersonLayer;
import net.ltxprogrammer.changed.util.Color3;
import net.parkabird.changedsynergy.ChangedSynergyClientConfig;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;

/** Ten-stage player-skin mask for latex coating; organic change is screen-only. */
public final class LegacyTransfurProgressLayer
        extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>
        implements FirstPersonLayer<AbstractClientPlayer> {
    private static final ResourceLocation[] LATEX_STAGES = new ResourceLocation[10];

    static {
        for (int index = 0; index < LATEX_STAGES.length; index++) {
            LATEX_STAGES[index] = ResourceLocation.fromNamespaceAndPath(
                    ChangedSynergyMod.MOD_ID,
                    "textures/models/latex_coat/" + (index + 1) + ".png");
        }
    }

    public LegacyTransfurProgressLayer(
            RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        float progress = visualProgress(player);
        if (progress <= 0.0F) {
            return;
        }

        Color3 color = LegacyTransfurVisualState.colorFor(player);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(
                textureFor(player, progress)));
        getParentModel().renderToBuffer(
                poseStack,
                consumer,
                packedLight,
                LivingEntityRenderer.getOverlayCoords(player, 0.0F),
                color.red(), color.green(), color.blue(), 1.0F);
    }

    @Override
    public void renderFirstPersonOnArms(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            HumanoidArm arm,
            PartPose armPose,
            float partialTick) {
        float progress = visualProgress(player);
        if (progress <= 0.0F) {
            return;
        }

        PlayerModel<AbstractClientPlayer> model = getParentModel();
        ModelPart armPart = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        ModelPart sleevePart = arm == HumanoidArm.RIGHT ? model.rightSleeve : model.leftSleeve;
        Color3 color = LegacyTransfurVisualState.colorFor(player);
        VertexConsumer consumer = buffer.getBuffer(RenderType.entityTranslucent(
                textureFor(player, progress)));

        poseStack.pushPose();
        poseStack.scale(FirstPersonLayer.ZFIGHT_OFFSET, FirstPersonLayer.ZFIGHT_OFFSET, FirstPersonLayer.ZFIGHT_OFFSET);
        armPart.loadPose(armPose);
        sleevePart.loadPose(armPose);
        armPart.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                color.red(), color.green(), color.blue(), 1.0F);
        sleevePart.render(poseStack, consumer, packedLight, OverlayTexture.NO_OVERLAY,
                color.red(), color.green(), color.blue(), 1.0F);
        poseStack.popPose();
    }

    private static float visualProgress(AbstractClientPlayer player) {
        return ChangedSynergyClientConfig.CLIENT.legacyTransfurSkinEffect.get()
                && !LegacyTransfurVisualState.isOrganicFor(player)
                ? LegacyTransfurVisualState.progressFor(player)
                : 0.0F;
    }

    private static ResourceLocation textureFor(AbstractClientPlayer player, float progress) {
        int stage = Mth.clamp((int)Math.floor(progress * 10.0F), 1, 10);
        return LATEX_STAGES[stage - 1];
    }
}
