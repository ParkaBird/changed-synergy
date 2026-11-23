package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.block.SurveillanceCameraBlock;
import net.parkabird.changedsynergy.block.SurveillanceCameraBlockEntity;
import net.parkabird.changedsynergy.client.model.SurveillanceCameraModel;
import net.parkabird.changedsynergy.init.ChangedSynergyBlockEntities;

/** Renders the dynamic gimbal head and state LED mounted atop the static wall bracket. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SurveillanceCameraRenderer implements BlockEntityRenderer<SurveillanceCameraBlockEntity> {
    private final SurveillanceCameraModel model;
    private final float[] ledVisual = new float[4];

    public SurveillanceCameraRenderer(BlockEntityRendererProvider.Context context) {
        this.model = new SurveillanceCameraModel(context.bakeLayer(SurveillanceCameraModel.LAYER_LOCATION));
    }

    @SubscribeEvent
    public static void registerLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(SurveillanceCameraModel.LAYER_LOCATION, SurveillanceCameraModel::createLayer);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ChangedSynergyBlockEntities.SURVEILLANCE_CAMERA.get(), SurveillanceCameraRenderer::new);
    }

    @Override
    public void render(SurveillanceCameraBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffers, int packedLight, int packedOverlay) {
        Direction facing = entity.getBlockState().getValue(SurveillanceCameraBlock.FACING);
        float facingDeg = switch (facing) {
            case EAST -> -90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 90.0F;
            default -> 0.0F;
        };

        entity.fillLedVisual(partialTick, ledVisual);

        poseStack.pushPose();
        poseStack.translate(0.5D, 7.0D / 16.0D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(facingDeg));
        poseStack.translate(0.0D, 0.0D, -1.0D / 16.0D);
        poseStack.scale(-1.0F, -1.0F, 1.0F);
        model.render(
                poseStack,
                buffers,
                packedLight,
                packedOverlay,
                entity.getYawDeg(partialTick),
                entity.getPitchDeg(partialTick),
                ledVisual[0],
                ledVisual[1],
                ledVisual[2],
                ledVisual[3] > 0.5F
        );
        poseStack.popPose();
    }
}
