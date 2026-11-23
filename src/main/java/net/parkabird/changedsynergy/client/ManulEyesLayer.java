package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.ltxprogrammer.changed.entity.EyeStyle;
import net.ltxprogrammer.changed.util.Color3;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.parkabird.changedsynergy.client.model.LatexManulModel;
import net.parkabird.changedsynergy.entity.AbstractLatexManul;

/**
 * Eagle-owl eye colouring (iris drives the whites, pupils stay near-black) while still
 * accepting every Changed eye style, including the narrow {@link EyeStyle#V2} shape.
 */
public final class ManulEyesLayer<T extends AbstractLatexManul>
        extends RenderLayer<T, LatexManulModel<T>> {
    private static final Color3 PUPIL = Color3.fromInt(0x050505);

    public ManulEyesLayer(RenderLayerParent<T, LatexManulModel<T>> parent) {
        super(parent);
    }

    public static EyeStyle style(EyeStyle requested) {
        if (requested == null) {
            return EyeStyle.V1;
        }
        // Unlike the owl layer, manuls keep V2 (narrow) instead of coercing it to V1.
        return requested;
    }

    @Override
    public void render(
            PoseStack pose,
            MultiBufferSource buffers,
            int light,
            T manul,
            float swing,
            float amount,
            float partial,
            float age,
            float yaw,
            float pitch) {
        if (manul.isInvisible()
                || manul.isSleeping()
                || net.ltxprogrammer.changed.extension.ChangedCompatibility.isFirstPersonRendering()) {
            return;
        }
        var info = manul.getBasicPlayerInfo();
        var settings = net.ltxprogrammer.changed.Changed.config.client;
        EyeStyle style = style(settings.basicPlayerInfo.isOverrideOthersToMatchStyle()
                ? settings.basicPlayerInfo.getEyeStyle()
                : info.getEyeStyle());
        // Always sample the V1 sclera white texel (same as EagleOwl). V2 "narrow" is
        // shape-only (skip the upper row); swapping to V2.getSclera() leaves that UV empty
        // and entityCutout discards the quads entirely.
        var buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(EyeStyle.V1.getSclera()));
        pose.pushPose();
        getParentModel().scaleForHead(pose);
        getParentModel().getHead().translateAndRotate(pose);
        float separation = 1.0F + net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer.getZFightingOffset(manul);
        pose.scale(separation, separation, separation);
        boolean narrow = style == EyeStyle.V2;
        for (boolean left : new boolean[] {false, true}) {
            for (int row = 0; row < 2; row++) {
                if (narrow && row == 0) {
                    continue;
                }
                for (int col = 0; col < 2; col++) {
                    boolean pupil = col == (left ? 0 : 1) && (row == 1 || style == EyeStyle.TALL);
                    Color3 color = pupil
                            ? PUPIL
                            : left ? info.getLeftIrisColor() : info.getRightIrisColor();
                    if (settings.useBlinkingEyes.get() && manul.isBlinking()) {
                        color = Color3.fromInt(0x6D6D6D);
                    }
                    float shade = !pupil && row == 0 ? 0.79F : 1.0F;
                    float x = ((left ? 1 : -3) + col) / 16.0F;
                    float y = (-5 + row) / 16.0F;
                    vertex(buffer, pose.last(), x, y + 1.0F / 16.0F, light, color, shade);
                    vertex(buffer, pose.last(), x + 1.0F / 16.0F, y + 1.0F / 16.0F, light, color, shade);
                    vertex(buffer, pose.last(), x + 1.0F / 16.0F, y, light, color, shade);
                    vertex(buffer, pose.last(), x, y, light, color, shade);
                }
            }
        }
        pose.popPose();
    }

    private static void vertex(
            VertexConsumer buffer,
            PoseStack.Pose pose,
            float x,
            float y,
            int light,
            Color3 color,
            float shade) {
        buffer.vertex(pose.pose(), x, y, -4.04F / 16.0F)
                .color(color.red() * shade, color.green() * shade, color.blue() * shade, 1.0F)
                .uv(9.5F / 32.0F, 11.5F / 32.0F)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(pose.normal(), 0.0F, 0.0F, -1.0F)
                .endVertex();
    }
}
