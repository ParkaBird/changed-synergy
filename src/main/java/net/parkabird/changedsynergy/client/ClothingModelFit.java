package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.*;
import java.util.*;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.*;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import net.minecraft.client.renderer.texture.OverlayTexture;

/** Fits authored garments to the final native limb pose without changing the wearer model. */
public abstract class ClothingModelFit {
    private final Map<ModelPart,float[]> bounds = new WeakHashMap<>();
    protected static boolean humanBody(net.minecraft.world.entity.LivingEntity wearer) {
        var body = net.parkabird.changedsynergy.item.HyenaTrailclothItem.body(wearer);
        return body == null || body instanceof net.ltxprogrammer.changed.entity.beast.LatexHuman;
    }
    @Nullable protected static ModelPart part(EntityModel<?> model, String name) {
        if (model instanceof AdvancedHumanoidModel<?> advanced) return switch (name) {
            case "Head" -> advanced.getHead();
            case "Torso" -> advanced.getTorso();
            case "LeftArm" -> advanced.getArm(HumanoidArm.LEFT);
            case "RightArm" -> advanced.getArm(HumanoidArm.RIGHT);
            case "LeftLeg" -> advanced.getLeg(HumanoidArm.LEFT);
            case "RightLeg" -> advanced.getLeg(HumanoidArm.RIGHT);
            case "LeftArm2", "RightArm2" -> model instanceof net.ltxprogrammer.changed.client.renderer.model.DoubleArmedModel<?> doubleArmed
                    ? doubleArmed.getOtherArm(name.equals("LeftArm2") ? HumanoidArm.LEFT : HumanoidArm.RIGHT) : null;
            default -> null;
        };
        if (model instanceof HumanoidModel<?> human) return switch (name) {
            case "Head" -> human.head;
            case "Torso" -> human.body;
            case "LeftArm" -> human.leftArm;
            case "RightArm" -> human.rightArm;
            case "LeftLeg" -> human.leftLeg;
            case "RightLeg" -> human.rightLeg;
            default -> null;
        };
        return null;
    }

    protected float[] bounds(ModelPart part) {
        return bounds.computeIfAbsent(part, p -> {
            var cubes = new ArrayList<float[]>();
            float[] largest = {0};
            var rootPose = new PoseStack(); p.translateAndRotate(rootPose);
            var inverse = new Matrix4f(rootPose.last().pose()).invert();
            p.visit(new PoseStack(), (pose, path, index, c) -> {
                // Unified exports put their cubes in static rotation wrappers; omit separate limbs such as tails.
                if (!path.isEmpty() && (path.indexOf('/', 1) >= 0 || !path.matches("/.+_r[0-9]+"))) return;
                float volume = (c.maxX - c.minX) * (c.maxY - c.minY) * (c.maxZ - c.minZ);
                largest[0] = Math.max(largest[0], volume);
                float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, volume};
                var transform = new Matrix4f(inverse).mul(pose.pose());
                for (float x : new float[]{c.minX, c.maxX}) for (float y : new float[]{c.minY, c.maxY}) for (float z : new float[]{c.minZ, c.maxZ}) {
                    var point = transform.transformPosition(new Vector3f(x / 16, y / 16, z / 16)).mul(16);
                    box[0] = Math.min(box[0], point.x); box[1] = Math.min(box[1], point.y); box[2] = Math.min(box[2], point.z);
                    box[3] = Math.max(box[3], point.x); box[4] = Math.max(box[4], point.y); box[5] = Math.max(box[5], point.z);
                }
                cubes.add(box);
            });
            float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                    Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY};
            // Fit the structural body, not protruding fur tufts that would pull the cloth inside the skin.
            for (var cube : cubes) if (cube[6] >= largest[0] * .5F) for (int axis = 0; axis < 3; axis++) {
                box[axis] = Math.min(box[axis], cube[axis]); box[axis + 3] = Math.max(box[axis + 3], cube[axis + 3]);
            }
            return Float.isFinite(box[0]) ? box : null;
        });
    }

    protected float waistOffset(EntityModel<?> model, ModelPart reference) {
        var torso = part(model, "Torso"); var leg = part(model, "LeftLeg");
        if (torso == null || leg == null) return 0;
        var from = bounds(reference.getChild("Torso")); var to = bounds(torso);
        if (from == null || to == null) return 0;
        float scale = (to[4] - to[1]) / Math.max(.001F, from[4] - from[1]);
        if (scale < .001F) return 0;
        return (leg.getInitialPose().y - torso.getInitialPose().y) / scale
                - (reference.getChild("LeftLeg").getInitialPose().y - reference.getChild("Torso").getInitialPose().y);
    }

    protected void renderPart(ModelPart clothing, ModelPart reference, @Nullable ModelPart target, PoseStack pose,
            VertexConsumer buffer, int light, @Nullable PartPose firstPerson, float offsetY, float padding) {
        if (target == null) return;
        clothing.resetPose();
        clothing.setPos(0, 0, 0); clothing.xRot = clothing.yRot = clothing.zRot = 0;
        var from = bounds(reference); var to = bounds(target);
        if (from == null) return;
        if (to == null) to = from;
        float sx = (to[3] - to[0] + 2 * padding) / Math.max(.001F, from[3] - from[0]);
        float sy = (to[4] - to[1]) / Math.max(.001F, from[4] - from[1]);
        float sz = (to[5] - to[2] + 2 * padding) / Math.max(.001F, from[5] - from[2]);
        pose.pushPose();
        if (firstPerson == null) target.translateAndRotate(pose);
        else {
            pose.translate(firstPerson.x / 16, firstPerson.y / 16, firstPerson.z / 16);
            pose.mulPose(new Quaternionf().rotationZYX(firstPerson.zRot, firstPerson.yRot, firstPerson.xRot));
            pose.scale(target.xScale, target.yScale, target.zScale);
        }
        pose.translate((to[0] - padding - from[0] * sx) / 16, (to[1] - from[1] * sy) / 16, (to[2] - padding - from[2] * sz) / 16);
        pose.scale(sx, sy, sz);
        pose.translate(0, offsetY / 16, 0);
        clothing.render(pose, buffer, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

}
