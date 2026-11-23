package net.parkabird.changedsynergy.client.model;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Dynamic dual-axis camera gimbal head (`camera_yaw` around Y, `camera_pitch` around X) plus tinted `status_led`. */
public final class SurveillanceCameraModel {
    public static final ModelLayerLocation LAYER_LOCATION = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath(ChangedSynergyMod.MOD_ID, "surveillance_camera"), "main");
    public static final ResourceLocation HEAD_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ChangedSynergyMod.MOD_ID, "textures/block/surveillance_camera_head.png");

    private final ModelPart cameraYaw;
    private final ModelPart cameraPitch;
    private final ModelPart statusLed;

    public SurveillanceCameraModel(ModelPart root) {
        this.cameraYaw = root.getChild("camera_yaw");
        this.cameraPitch = this.cameraYaw.getChild("camera_pitch");
        this.statusLed = this.cameraPitch.getChild("status_led");
    }

    public static LayerDefinition createLayer() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition root = mesh.getRoot();

        PartDefinition cameraYaw = root.addOrReplaceChild("camera_yaw",
                CubeListBuilder.create(), PartPose.ZERO);
        cameraYaw.addOrReplaceChild("camera_yaw_mesh", CubeListBuilder.create()
                .texOffs(0, 18).addBox(-2.5F, -1.0F, -2.5F, 5.0F, 1.0F, 5.0F)
                .texOffs(20, 18).addBox(-4.5F, -2.0F, -1.0F, 9.0F, 1.0F, 2.0F)
                .texOffs(0, 24).addBox(3.55F, -7.0F, -1.0F, 1.0F, 5.0F, 2.0F)
                .texOffs(6, 24).addBox(-4.55F, -7.0F, -1.0F, 1.0F, 5.0F, 2.0F), PartPose.ZERO);

        PartDefinition cameraPitch = cameraYaw.addOrReplaceChild("camera_pitch",
                CubeListBuilder.create(), PartPose.offset(0.0F, -6.5F, 0.0F));
        cameraPitch.addOrReplaceChild("camera_pitch_mesh", CubeListBuilder.create()
                .texOffs(0, 0).addBox(-3.5F, -3.0F, -3.0F, 7.0F, 5.0F, 6.0F)
                .texOffs(26, 0).addBox(-2.5F, -2.5F, 3.0F, 5.0F, 4.0F, 2.0F)
                .texOffs(26, 6).addBox(-2.5F, -2.5F, -5.0F, 4.0F, 4.0F, 2.0F)
                .texOffs(0, 11).addBox(-3.5F, -4.0F, -5.0F, 7.0F, 1.0F, 6.0F)
                .texOffs(26, 12).addBox(-2.0F, -4.0F, 1.0F, 4.0F, 1.0F, 2.0F), PartPose.ZERO);

        cameraPitch.addOrReplaceChild("status_led", CubeListBuilder.create()
                .texOffs(26, 15).addBox(2.5F, -3.0F, -4.0F, 1.0F, 1.0F, 1.0F), PartPose.ZERO);

        return LayerDefinition.create(mesh, 64, 64);
    }

    public void render(PoseStack poseStack, MultiBufferSource buffers, int packedLight, int packedOverlay,
                       float yawDeg, float pitchDeg, float ledR, float ledG, float ledB, boolean emissiveLed) {
        cameraYaw.yRot = yawDeg * Mth.DEG_TO_RAD;
        cameraPitch.xRot = pitchDeg * Mth.DEG_TO_RAD;

        statusLed.visible = false;
        VertexConsumer bodyConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(HEAD_TEXTURE));
        cameraYaw.render(poseStack, bodyConsumer, packedLight, packedOverlay);

        poseStack.pushPose();
        cameraYaw.translateAndRotate(poseStack);
        cameraPitch.translateAndRotate(poseStack);
        statusLed.visible = true;
        int ledLight = emissiveLed ? LightTexture.FULL_BRIGHT : packedLight;
        VertexConsumer ledConsumer = buffers.getBuffer(RenderType.entityCutoutNoCull(HEAD_TEXTURE));
        statusLed.render(poseStack, ledConsumer, ledLight, packedOverlay, ledR, ledG, ledB, 1.0F);
        poseStack.popPose();
    }
}
