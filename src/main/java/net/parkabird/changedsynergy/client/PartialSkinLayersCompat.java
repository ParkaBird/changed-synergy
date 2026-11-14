package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraftforge.fml.ModList;
import net.parkabird.changedsynergy.ChangedSynergyMod;

/** Uses the installed mod's meshes without requiring or redistributing it. */
public final class PartialSkinLayersCompat {
    private static final String[] PARENTS = {"Head", "Torso", "LeftArm", "RightArm", "LeftLeg", "RightLeg"};
    private static final String[] PARTS = {"Hat", "Jacket", "LeftSleeve", "RightSleeve", "LeftPants", "RightPants"};
    private static final String[] MESHES = {
        "getHeadMesh", "getTorsoMesh", "getLeftArmMesh", "getRightArmMesh", "getLeftLegMesh", "getRightLegMesh"
    };
    private static final String[] OPTIONS = {
        "enableHat", "enableJacket", "enableLeftSleeve", "enableRightSleeve", "enableLeftPants", "enableRightPants"
    };
    private static final String[] OFFSETS = {"HEAD", "BODY", "LEFT_ARM", "RIGHT_ARM", "LEFT_LEG", "RIGHT_LEG"};
    private static final Map<AbstractClientPlayer, Map<Boolean, CroppedSkin>> CROPPED = new WeakHashMap<>();
    private static final Map<Object, OverlayState> STATES = new WeakHashMap<>();
    private static Api api;
    private static boolean unavailable;

    private PartialSkinLayersCompat() {}

    public static boolean isSupportedModel(Object model) {
        if (model == null) return false;
        return switch (model.getClass().getSimpleName()) {
            case "LatexHumanModel", "DarkLatexWolfPartialModel", "SnowLeopardPartialModel", "WhiteLatexWolfPartialModel" -> true;
            default -> false;
        };
    }

    public static void syncFirstPersonArm(Object model, AbstractClientPlayer player) {
        if (!isSupportedModel(model)) return;
        OverlayState state = stateFor(model);
        if (state != null && !state.isLatexLayer) {
            state.layers[2].visible = player == null || player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE);
            state.layers[3].visible = player == null || player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE);
        }
        apply(model, player);
    }

    public static void apply(Object model, AbstractClientPlayer player) {
        if (unavailable || !ModList.get().isLoaded("skinlayers3d") || !isSupportedModel(model)) return;
        OverlayState state = stateFor(model);
        if (state == null || state.isLatexLayer) return;
        state.clear();
        if (player == null || player.isInvisible()) return;
        try {
            if (api == null) api = new Api();
            Object config = api.config.get(null);
            int distance = config.getClass().getField("renderDistanceLOD").getInt(config);
            if (player.distanceToSqr(Minecraft.getInstance().gameRenderer.getMainCamera().getPosition())
                    > (double) distance * distance) {
                return;
            }
            boolean slim = player.getModelName().equals("slim");
            if (!(boolean) api.setup.invoke(null, player, player, slim)) return;
            boolean partial = !model.getClass().getSimpleName().equals("LatexHumanModel");
            Object[] cropped = partial ? api.cropped(player, slim) : null;
            boolean anyActive = false;
            for (int i = 0; i < PARTS.length; i++) {
                if (state.targets[i] == null || !config.getClass().getField(OPTIONS[i]).getBoolean(config)) continue;
                if (i == 0 && ((java.util.Set<?>) api.hiddenHats.get(null))
                        .contains(player.getItemBySlot(EquipmentSlot.HEAD).getItem())) {
                    continue;
                }
                Object mesh = partial && i >= 2
                        ? (cropped == null ? null : cropped[i - 2])
                        : api.getters[i].invoke(player);
                if (mesh != null) {
                    state.meshes[i] = mesh;
                    state.offsets[i] = api.offsets
                            .getField(OFFSETS[i] + (slim && (i == 2 || i == 3) ? "_SLIM" : ""))
                            .get(null);
                    anyActive = true;
                }
            }
            state.slim = slim;
            state.anyActive = anyActive;
        } catch (ReflectiveOperationException | LinkageError exception) {
            state.clear();
            unavailable = true;
            ChangedSynergyMod.LOGGER.warn("3D skin layer compatibility unavailable; retaining native skin rendering", exception);
        }
    }

    /**
     * Suppresses the flat 2D skin overlay parts for active 3D meshes before {@code renderToBuffer}
     * runs, so Embeddium/Oculus's child-part traversal does not draw flat layers on top of or
     * instead of the 3D meshes.
     */
    public static void beforeRenderToBuffer(Object model) {
        if (unavailable || model == null) return;
        OverlayState state = STATES.get(model);
        if (state == null || !state.anyActive || state.isLatexLayer) return;
        for (int i = 0; i < PARTS.length; i++) {
            if (state.meshes[i] != null && state.layers[i].visible) {
                state.layers[i].visible = false;
                state.suppressed[i] = true;
            }
        }
    }

    /** Restores flat overlay visibility and renders the active 3D skin-layer meshes directly. */
    public static void afterRenderToBuffer(
            Object model,
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) {
        if (unavailable || model == null) return;
        OverlayState state = STATES.get(model);
        if (state == null || state.isLatexLayer) return;
        try {
            if (api == null) api = new Api();
            for (int i = 0; i < PARTS.length; i++) {
                if (!state.suppressed[i]) continue;
                state.suppressed[i] = false;
                ModelPart layer = state.layers[i];
                layer.visible = true;
                Object mesh = state.meshes[i];
                Object offset = state.offsets[i];
                ModelPart target = state.targets[i];
                if (mesh == null || offset == null || target == null) continue;
                renderPartMesh(state.parents[i], layer, target, offset, mesh, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
            }
        } catch (ReflectiveOperationException | LinkageError exception) {
            state.restoreSuppressed();
            unavailable = true;
            ChangedSynergyMod.LOGGER.warn("3D skin layer compatibility unavailable; retaining native skin rendering", exception);
        }
    }

    public static void prepareFirstPersonArm(Object model, AbstractClientPlayer player, HumanoidArm arm) {
        syncFirstPersonArm(model, player);
        if (unavailable || model == null || arm == null) return;
        OverlayState state = STATES.get(model);
        if (state == null || state.isLatexLayer) return;
        int index = arm == HumanoidArm.LEFT ? 2 : 3;
        if (state.meshes[index] != null && state.layers[index].visible) {
            state.layers[index].visible = false;
            state.suppressedFirstPersonArm = arm;
        }
    }

    public static void finishFirstPersonArm(
            Object model,
            ResourceLocation texture,
            HumanoidArm arm,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight) {
        if (unavailable || model == null || arm == null) return;
        OverlayState state = STATES.get(model);
        if (state == null || state.isLatexLayer || state.suppressedFirstPersonArm != arm) return;
        int index = arm == HumanoidArm.LEFT ? 2 : 3;
        ModelPart sleeve = state.layers[index];
        state.suppressedFirstPersonArm = null;
        sleeve.visible = true;
        if (texture == null || poseStack == null || bufferSource == null) return;
        Object mesh = state.meshes[index];
        if (mesh == null) return;
        try {
            if (api == null) api = new Api();
            String offsetName = (arm == HumanoidArm.LEFT ? "FIRSTPERSON_LEFT_ARM" : "FIRSTPERSON_RIGHT_ARM")
                    + (state.slim ? "_SLIM" : "");
            Object offset = api.offsets.getField(offsetName).get(null);
            VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityTranslucent(texture));
            renderPartMesh(
                    state.parents[index],
                    sleeve,
                    state.targets[index],
                    offset,
                    mesh,
                    poseStack,
                    buffer,
                    packedLight,
                    OverlayTexture.NO_OVERLAY,
                    1.0F,
                    1.0F,
                    1.0F,
                    1.0F);
        } catch (ReflectiveOperationException | LinkageError exception) {
            unavailable = true;
            ChangedSynergyMod.LOGGER.warn("3D skin layer compatibility unavailable; retaining native skin rendering", exception);
        }
    }

    public static void restoreFirstPersonArm(Object model) {
        if (model == null) return;
        OverlayState state = STATES.get(model);
        if (state == null || state.suppressedFirstPersonArm == null) return;
        int index = state.suppressedFirstPersonArm == HumanoidArm.LEFT ? 2 : 3;
        state.suppressedFirstPersonArm = null;
        if (state.layers[index] != null) {
            state.layers[index].visible = true;
        }
    }

    private static void renderPartMesh(
            ModelPart parent,
            ModelPart layer,
            ModelPart target,
            Object offset,
            Object mesh,
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            float red,
            float green,
            float blue,
            float alpha) throws ReflectiveOperationException {
        poseStack.pushPose();
        parent.translateAndRotate(poseStack);
        if (layer != target) {
            layer.translateAndRotate(poseStack);
        }
        target.translateAndRotate(poseStack);
        api.applyOffset.invoke(offset, poseStack, mesh);
        api.renderMesh.invoke(mesh, target, poseStack, buffer, packedLight, packedOverlay, red, green, blue, alpha);
        poseStack.popPose();
    }

    private static OverlayState stateFor(Object model) {
        OverlayState existing = STATES.get(model);
        if (existing != null) return existing;
        try {
            OverlayState created = resolveState(model);
            if (created != null) {
                STATES.put(model, created);
            }
            return created;
        } catch (ReflectiveOperationException exception) {
            unavailable = true;
            ChangedSynergyMod.LOGGER.warn("3D skin layer compatibility unavailable; retaining native skin rendering", exception);
            return null;
        }
    }

    private static OverlayState resolveState(Object model) throws ReflectiveOperationException {
        Class<?> type = model.getClass();
        boolean partial = !type.getSimpleName().equals("LatexHumanModel");
        ModelPart[] parents = new ModelPart[PARTS.length];
        ModelPart[] layers = new ModelPart[PARTS.length];
        ModelPart[] targets = new ModelPart[PARTS.length];
        for (int i = 0; i < PARTS.length; i++) {
            Field parentField = type.getDeclaredField(PARENTS[i]);
            parentField.setAccessible(true);
            parents[i] = (ModelPart) parentField.get(model);
            Field layerField = type.getDeclaredField(PARTS[i]);
            layerField.setAccessible(true);
            ModelPart layer = (ModelPart) layerField.get(model);
            layers[i] = layer;
            if (partial && i >= 4) {
                String child = i == 4 ? "LeftThighLayer_r1" : "RightThighLayer_r1";
                targets[i] = layer != null && layer.hasChild(child) ? layer.getChild(child) : null;
            } else {
                targets[i] = layer;
            }
        }
        // Partial models pass latexLayer = true for the latex overlay model, where Hat/Jacket/etc. all alias NULL_PART.
        boolean isLatexLayer = layers[0] == layers[1];
        return new OverlayState(parents, layers, targets, isLatexLayer);
    }

    private record CroppedSkin(ResourceLocation skin, Object[] meshes) {}

    private static final class OverlayState {
        final ModelPart[] parents;
        final ModelPart[] layers;
        final ModelPart[] targets;
        final boolean isLatexLayer;
        final Object[] meshes = new Object[PARTS.length];
        final Object[] offsets = new Object[PARTS.length];
        final boolean[] suppressed = new boolean[PARTS.length];
        boolean anyActive;
        boolean slim;
        HumanoidArm suppressedFirstPersonArm;

        OverlayState(ModelPart[] parents, ModelPart[] layers, ModelPart[] targets, boolean isLatexLayer) {
            this.parents = parents;
            this.layers = layers;
            this.targets = targets;
            this.isLatexLayer = isLatexLayer;
        }

        void clear() {
            restoreSuppressed();
            for (int i = 0; i < PARTS.length; i++) {
                meshes[i] = null;
                offsets[i] = null;
            }
            anyActive = false;
        }

        void restoreSuppressed() {
            for (int i = 0; i < PARTS.length; i++) {
                if (suppressed[i]) {
                    suppressed[i] = false;
                    if (layers[i] != null) {
                        layers[i].visible = true;
                    }
                }
            }
        }
    }

    private static final class Api {
        final Class<?> offsets = Class.forName("dev.tr7zw.skinlayers.api.OffsetProvider");
        final Class<?> mesh = Class.forName("dev.tr7zw.skinlayers.api.Mesh");
        final Class<?> settings = Class.forName("dev.tr7zw.skinlayers.accessor.PlayerSettings");
        final Class<?> util = Class.forName("dev.tr7zw.skinlayers.SkinUtil");
        final Class<?> base = Class.forName("dev.tr7zw.skinlayers.SkinLayersModBase");
        final Field config = base.getField("config");
        final Field hiddenHats = base.getField("hideHeadLayers");
        final Method setup = util.getMethod("setup3dLayers", AbstractClientPlayer.class, settings, boolean.class);
        final Method texture = util.getMethod("getTexture", ResourceLocation.class, Class.forName("dev.tr7zw.skinlayers.accessor.SkullSettings"));
        final Object helper = Class.forName("dev.tr7zw.skinlayers.api.SkinLayersAPI").getMethod("getMeshHelper").invoke(null);
        final Method create = Class.forName("dev.tr7zw.skinlayers.api.MeshHelper").getMethod(
                "create3DMesh",
                NativeImage.class,
                int.class,
                int.class,
                int.class,
                int.class,
                int.class,
                boolean.class,
                float.class);
        final Method applyOffset = offsets.getMethod("applyOffset", PoseStack.class, mesh);
        final Method renderMesh = mesh.getMethod(
                "render",
                ModelPart.class,
                PoseStack.class,
                VertexConsumer.class,
                int.class,
                int.class,
                float.class,
                float.class,
                float.class,
                float.class);
        final Method[] getters = new Method[MESHES.length];

        Api() throws ReflectiveOperationException {
            Class<?> data = Class.forName("dev.tr7zw.skinlayers.api.PlayerData");
            for (int i = 0; i < getters.length; i++) getters[i] = data.getMethod(MESHES[i]);
        }

        Object[] cropped(AbstractClientPlayer player, boolean slim) throws ReflectiveOperationException {
            ResourceLocation skin = player.getSkinTextureLocation();
            var cache = CROPPED.computeIfAbsent(player, ignored -> new java.util.HashMap<>());
            var previous = cache.get(slim);
            if (previous != null && previous.skin.equals(skin)) return previous.meshes;
            NativeImage source = (NativeImage) texture.invoke(null, skin, null);
            if (source == null || source.getWidth() != 64 || source.getHeight() != 64) return null;
            Object[] meshes = new Object[4];
            int[][] dimensions = {{slim ? 3 : 4, 9, 48, 48}, {slim ? 3 : 4, 9, 40, 32}, {4, 7, 0, 48}, {4, 7, 0, 32}};
            for (int i = 0; i < meshes.length; i++) {
                int[] size = dimensions[i];
                try (NativeImage image = new NativeImage(64, 64, true)) {
                    image.copyFrom(source);
                    // The native partial models omit the lower cap at the latex boundary.
                    for (int x = size[2] + 4 + size[0]; x < size[2] + 4 + 2 * size[0]; x++) {
                        for (int y = size[3]; y < size[3] + 4; y++) {
                            image.setPixelRGBA(x, y, 0);
                        }
                    }
                    meshes[i] = create.invoke(helper, image, size[0], size[1], 4, size[2], size[3], true, i < 2 ? -2F : 0F);
                }
            }
            cache.put(slim, new CroppedSkin(skin, meshes));
            return meshes;
        }
    }
}
