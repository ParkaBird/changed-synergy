package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.parkabird.changedsynergy.entity.CreatureFishingHookVisual;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Renders the creature float with Minecraft's fishing-hook texture and line. */
public final class CreatureFishingHookRenderer
        extends EntityRenderer<CreatureFishingHookVisual> {
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(
                    "minecraft", "textures/entity/fishing_hook.png");
    private static final RenderType FLOAT_RENDER_TYPE =
            RenderType.entityCutout(TEXTURE);

    public CreatureFishingHookRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(
            CreatureFishingHookVisual hook,
            float entityYaw,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        ChangedEntity fisher = hook.getFisher();
        if (fisher == null) {
            return;
        }

        poseStack.pushPose();
        renderFloat(poseStack, buffers, packedLight);
        renderLine(hook, fisher, partialTick, poseStack, buffers);
        poseStack.popPose();
        super.render(
                hook,
                entityYaw,
                partialTick,
                poseStack,
                buffers,
                packedLight);
    }

    private void renderFloat(
            PoseStack poseStack,
            MultiBufferSource buffers,
            int packedLight) {
        poseStack.pushPose();
        poseStack.scale(0.5F, 0.5F, 0.5F);
        poseStack.mulPose(entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f matrix = pose.pose();
        Matrix3f normal = pose.normal();
        VertexConsumer consumer = buffers.getBuffer(FLOAT_RENDER_TYPE);
        floatVertex(consumer, matrix, normal, packedLight, 0.0F, 0, 0, 1);
        floatVertex(consumer, matrix, normal, packedLight, 1.0F, 0, 1, 1);
        floatVertex(consumer, matrix, normal, packedLight, 1.0F, 1, 1, 0);
        floatVertex(consumer, matrix, normal, packedLight, 0.0F, 1, 0, 0);
        poseStack.popPose();
    }

    private void renderLine(
            CreatureFishingHookVisual hook,
            ChangedEntity fisher,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffers) {
        int hand = fisher.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        float bodyYaw = Mth.lerp(
                partialTick, fisher.yBodyRotO, fisher.yBodyRot)
                * Mth.DEG_TO_RAD;
        double sin = Mth.sin(bodyYaw);
        double cos = Mth.cos(bodyYaw);
        double side = hand * 0.35D;

        double handX = Mth.lerp(
                        partialTick, fisher.xo, fisher.getX())
                - cos * side - sin * 0.8D;
        double handY = Mth.lerp(
                        partialTick, fisher.yo, fisher.getY())
                + fisher.getEyeHeight() - 0.45D;
        double handZ = Mth.lerp(
                        partialTick, fisher.zo, fisher.getZ())
                - sin * side + cos * 0.8D;

        double hookX = Mth.lerp(partialTick, hook.xo, hook.getX());
        double hookY = Mth.lerp(partialTick, hook.yo, hook.getY()) + 0.25D;
        double hookZ = Mth.lerp(partialTick, hook.zo, hook.getZ());
        float dx = (float)(handX - hookX);
        float dy = (float)(handY - hookY);
        float dz = (float)(handZ - hookZ);

        VertexConsumer consumer = buffers.getBuffer(RenderType.lineStrip());
        PoseStack.Pose pose = poseStack.last();
        for (int index = 0; index <= 16; index++) {
            float progress = index / 16.0F;
            float next = (index + 1) / 16.0F;
            lineVertex(dx, dy, dz, consumer, pose, progress, next);
        }
    }

    private static void floatVertex(
            VertexConsumer consumer,
            Matrix4f matrix,
            Matrix3f normal,
            int packedLight,
            float x,
            int y,
            int u,
            int v) {
        consumer.vertex(matrix, x - 0.5F, y - 0.5F, 0.0F)
                .color(255, 255, 255, 255)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(packedLight)
                .normal(normal, 0.0F, 1.0F, 0.0F)
                .endVertex();
    }

    private static void lineVertex(
            float dx,
            float dy,
            float dz,
            VertexConsumer consumer,
            PoseStack.Pose pose,
            float progress,
            float next) {
        float x = dx * progress;
        float y = dy * (progress * progress + progress) * 0.5F + 0.25F;
        float z = dz * progress;
        float nx = dx * next - x;
        float ny = dy * (next * next + next) * 0.5F + 0.25F - y;
        float nz = dz * next - z;
        float length = Mth.sqrt(nx * nx + ny * ny + nz * nz);
        if (length <= 1.0E-5F) {
            length = 1.0F;
        }
        consumer.vertex(pose.pose(), x, y, z)
                .color(0, 0, 0, 255)
                .normal(pose.normal(), nx / length, ny / length, nz / length)
                .endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(
            CreatureFishingHookVisual hook) {
        return TEXTURE;
    }
}
