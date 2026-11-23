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
    private static final Map<ModelPart, float[]> STAND_BOUNDS = new WeakHashMap<>();

    private static final float[] STAND_HEAD = {-4F, -8F, -4F, 4F, 0F, 4F};
    private static final float[] STAND_TORSO = {-4F, 0F, -2F, 4F, 12F, 2F};
    private static final float[] STAND_RIGHT_ARM = {-3F, -2F, -2F, 1F, 10F, 2F};
    private static final float[] STAND_LEFT_ARM = {-1F, -2F, -2F, 3F, 10F, 2F};
    private static final float[] STAND_LEG = {-2F, 0F, -2F, 2F, 12F, 2F};

    private static final float[] BIPED_STAND_HEAD = {-4F, -7F, -4F, 4F, 1F, 4F};
    private static final float[] BIPED_STAND_TORSO = {-4F, 1F, -2F, 4F, 13F, 2F};
    private static final float[] BIPED_STAND_RIGHT_ARM = {-3F, -1F, -2F, 1F, 11F, 2F};
    private static final float[] BIPED_STAND_LEFT_ARM = {-1F, -1F, -2F, 3F, 11F, 2F};
    private static final float[] BIPED_STAND_THIGH = {-2F, 0.5671F, -3.4679F, 2F, 8.267F, 1.9526F};
    private static final float[] BIPED_STAND_RIGHT_CALF = {-1.99F, -2.048F, -0.0098F, 2.01F, 4.8728F, 7.1577F};
    private static final float[] BIPED_STAND_LEFT_CALF = {-2.01F, -2.048F, -0.0098F, 1.99F, 4.8728F, 7.1577F};

    @Nullable
    private static ModelPart withBounds(@Nullable ModelPart part, float[] box) {
        if (part != null) STAND_BOUNDS.put(part, box);
        return part;
    }

    protected static boolean humanBody(net.minecraft.world.entity.LivingEntity wearer) {
        if (wearer instanceof net.minecraft.world.entity.decoration.ArmorStand) {
            return !(wearer instanceof net.ltxprogrammer.changed.entity.decoration.AbstractArmorStand);
        }
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
        if (model instanceof net.parkabird.changedsynergy.mixin.BipedArmorStandModelAccessor biped) {
            return switch (name) {
                case "Head" -> withBounds(biped.getHeadPart(), BIPED_STAND_HEAD);
                case "Torso" -> withBounds(biped.getTorsoPart(), BIPED_STAND_TORSO);
                case "LeftArm" -> withBounds(biped.getLeftArmPart(), BIPED_STAND_LEFT_ARM);
                case "RightArm" -> withBounds(biped.getRightArmPart(), BIPED_STAND_RIGHT_ARM);
                case "LeftLeg" -> {
                    ModelPart leg = withBounds(biped.getLeftLegPart(), BIPED_STAND_THIGH);
                    if (leg != null && leg.hasChild("LeftLowerLeg")) {
                        STAND_BOUNDS.put(leg.getChild("LeftLowerLeg"), BIPED_STAND_LEFT_CALF);
                    }
                    yield leg;
                }
                case "RightLeg" -> {
                    ModelPart leg = withBounds(biped.getRightLegPart(), BIPED_STAND_THIGH);
                    if (leg != null && leg.hasChild("RightLowerLeg")) {
                        STAND_BOUNDS.put(leg.getChild("RightLowerLeg"), BIPED_STAND_RIGHT_CALF);
                    }
                    yield leg;
                }
                default -> null;
            };
        }
        if (model instanceof net.parkabird.changedsynergy.mixin.CentaurArmorStandModelAccessor taur) {
            return switch (name) {
                case "Head" -> withBounds(taur.getHeadPart(), STAND_HEAD);
                case "Torso" -> withBounds(taur.getTorsoPart(), STAND_TORSO);
                case "LeftArm" -> withBounds(taur.getLeftArmPart(), STAND_LEFT_ARM);
                case "RightArm" -> withBounds(taur.getRightArmPart(), STAND_RIGHT_ARM);
                default -> null;
            };
        }
        if (model instanceof net.parkabird.changedsynergy.mixin.LeglessArmorStandModelAccessor mer) {
            return switch (name) {
                case "Head" -> withBounds(mer.getHeadPart(), STAND_HEAD);
                case "Torso" -> withBounds(mer.getTorsoPart(), STAND_TORSO);
                case "LeftArm" -> withBounds(mer.getLeftArmPart(), STAND_LEFT_ARM);
                case "RightArm" -> withBounds(mer.getRightArmPart(), STAND_RIGHT_ARM);
                default -> null;
            };
        }
        if (model instanceof net.minecraft.client.model.ArmorStandModel stand) {
            return switch (name) {
                case "Head" -> withBounds(stand.head, STAND_HEAD);
                case "Torso" -> withBounds(stand.body, STAND_TORSO);
                case "LeftArm" -> withBounds(stand.leftArm, STAND_LEFT_ARM);
                case "RightArm" -> withBounds(stand.rightArm, STAND_RIGHT_ARM);
                case "LeftLeg" -> withBounds(stand.leftLeg, STAND_LEG);
                case "RightLeg" -> withBounds(stand.rightLeg, STAND_LEG);
                default -> null;
            };
        }
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

    private static final PoseStack.Pose IDENTITY_POSE = new PoseStack().last();

    private static float[] effectiveCubeBounds(ModelPart.Cube cube) {
        final float[] poly = {
                Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY
        };
        VertexConsumer collector = new VertexConsumer() {
            private void record(float x, float y, float z) {
                float vx = x * 16.0F;
                float vy = y * 16.0F;
                float vz = z * 16.0F;
                if (vx < poly[0]) poly[0] = vx;
                if (vy < poly[1]) poly[1] = vy;
                if (vz < poly[2]) poly[2] = vz;
                if (vx > poly[3]) poly[3] = vx;
                if (vy > poly[4]) poly[4] = vy;
                if (vz > poly[5]) poly[5] = vz;
            }
            @Override public VertexConsumer vertex(double x, double y, double z) { record((float) x, (float) y, (float) z); return this; }
            @Override public void vertex(float x, float y, float z, float r, float g, float b, float a, float u, float v, int overlay, int light, float nx, float ny, float nz) { record(x, y, z); }
            @Override public VertexConsumer color(int r, int g, int b, int a) { return this; }
            @Override public VertexConsumer uv(float u, float v) { return this; }
            @Override public VertexConsumer overlayCoords(int u, int v) { return this; }
            @Override public VertexConsumer uv2(int u, int v) { return this; }
            @Override public VertexConsumer normal(float x, float y, float z) { return this; }
            @Override public void endVertex() {}
            @Override public void defaultColor(int r, int g, int b, int a) {}
            @Override public void unsetDefaultColor() {}
        };
        cube.compile(IDENTITY_POSE, collector, 0, 0, 1.0F, 1.0F, 1.0F, 1.0F);
        float minX = cube.minX, minY = cube.minY, minZ = cube.minZ;
        float maxX = cube.maxX, maxY = cube.maxY, maxZ = cube.maxZ;
        if (Float.isFinite(poly[0])) {
            // Respect negative CubeDeformation (e.g. slim scaled bodies like Jerboa) without inflating positive fur tufts.
            if (poly[3] - poly[0] < (maxX - minX) - 1.0E-3F) { minX = Math.max(minX, poly[0]); maxX = Math.min(maxX, poly[3]); }
            if (poly[4] - poly[1] < (maxY - minY) - 1.0E-3F) { minY = Math.max(minY, poly[1]); maxY = Math.min(maxY, poly[4]); }
            if (poly[5] - poly[2] < (maxZ - minZ) - 1.0E-3F) { minZ = Math.max(minZ, poly[2]); maxZ = Math.min(maxZ, poly[5]); }
        }
        return new float[]{minX, minY, minZ, maxX, maxY, maxZ};
    }

    protected float[] bounds(ModelPart part) {
        float[] stand = STAND_BOUNDS.get(part);
        if (stand != null) return stand;
        return bounds.computeIfAbsent(part, p -> {
            var cubes = new ArrayList<float[]>();
            float[] largest = {0};
            var rootPose = new PoseStack(); p.translateAndRotate(rootPose);
            var inverse = new Matrix4f(rootPose.last().pose()).invert();
            p.visit(new PoseStack(), (pose, path, index, c) -> {
                // Unified exports put their cubes in static rotation wrappers; omit separate limbs such as tails.
                if (!path.isEmpty() && (path.indexOf('/', 1) >= 0 || !path.matches("/.+_r[0-9]+"))) return;
                float[] cb = effectiveCubeBounds(c);
                float volume = (cb[3] - cb[0]) * (cb[4] - cb[1]) * (cb[5] - cb[2]);
                largest[0] = Math.max(largest[0], volume);
                float[] box = {Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
                        Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, volume};
                var transform = new Matrix4f(inverse).mul(pose.pose());
                for (float x : new float[]{cb[0], cb[3]}) for (float y : new float[]{cb[1], cb[4]}) for (float z : new float[]{cb[2], cb[5]}) {
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
