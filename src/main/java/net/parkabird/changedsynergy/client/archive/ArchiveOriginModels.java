package net.parkabird.changedsynergy.client.archive;

import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.DoubleHeadedModel;
import net.ltxprogrammer.changed.client.renderer.model.TripleHeadedModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.variant.TransfurVariant;
import net.ltxprogrammer.changed.init.ChangedAttributes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.DefaultAttributes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.ForgeMod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.FactionDiplomacy;
import net.parkabird.changedsynergy.ai.HunterFaction;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog.Entry;
import org.joml.Quaternionf;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;

/** Screen-owned original-model previews. These entities are never added to the world. */
public final class ArchiveOriginModels {
    public record Field(Component label, Component value, float ratio, int color) {}

    private final Map<ResourceLocation, ChangedEntity> previews = new HashMap<>();
    private final Map<ResourceLocation, List<Field>> data = new HashMap<>();
    private final Map<String, Bounds> geometry = new HashMap<>();
    private final Set<ResourceLocation> failed = new HashSet<>();
    private Level level;
    private Object renderers;
    private RemotePlayer human;

    public void clear() {
        previews.clear();
        data.clear();
        geometry.clear();
        failed.clear();
        level = null;
        renderers = null;
        human = null;
    }

    /** Biological archive sections, separate from the political faction below. */
    public static int categoryOf(@Nullable Entry entry) {
        return net.parkabird.changedsynergy.origin.NativeOriginCatalog.categoryOf(entry);
    }

    public static Component name(@Nullable Entry entry) {
        if (entry == null) {
            var player = Minecraft.getInstance().player;
            return player == null ? Component.translatable("origin.changed_synergy.human")
                    : Component.literal(player.getGameProfile().getName());
        }
        var variant = entry.variant();
        Component name = variant == null ? Component.literal(entry.form().getPath())
                : Component.translatable(variant.getEntityType().getDescriptionId());
        if (entry.form().getPath().endsWith("/male")) return name.copy().append(Component.literal(" ♂").withStyle(style -> style.withBold(false)));
        if (entry.form().getPath().endsWith("/female")) return name.copy().append(Component.literal(" ♀").withStyle(style -> style.withBold(false)));
        return name;
    }

    @Nullable
    private LivingEntity entity(@Nullable Entry entry) {
        Minecraft minecraft = Minecraft.getInstance();
        // Public renderer identity also changes after a resource reload; its backing map is private.
        Object currentRenderers = minecraft.player == null ? null
                : minecraft.getEntityRenderDispatcher().getRenderer(minecraft.player);
        if (level != minecraft.level || renderers != currentRenderers) {
            clear();
            level = minecraft.level;
            renderers = currentRenderers;
        }
        if (entry == null) {
            if (minecraft.player == null || minecraft.level == null) return null;
            if (human == null || !human.getUUID().equals(minecraft.player.getUUID())) {
                human = new RemotePlayer(minecraft.level, minecraft.player.getGameProfile()) {
                    @Override public boolean isModelPartShown(PlayerModelPart part) {
                        return Minecraft.getInstance().player != null && Minecraft.getInstance().player.isModelPartShown(part);
                    }
                };
                human.setId(TransfurVariant.getNextEntId());
                human.setSilent(true);
            }
            return human;
        }
        if (level == null || entry.variant() == null) return null;
        if (failed.contains(entry.form())) return null;
        return previews.computeIfAbsent(entry.form(), ignored -> {
            try {
                ChangedEntity preview = entry.variant().getEntityType().create(level);
                if (preview != null) {
                    preview.setId(TransfurVariant.getNextEntId());
                    preview.setNoAi(true);
                    preview.setSilent(true);
                }
                return preview;
            } catch (RuntimeException error) {
                failed.add(entry.form());
                ChangedSynergyMod.LOGGER.warn("Cannot create archive model for {}", entry.form(), error);
                return null;
            }
        });
    }

    public List<Field> fields(@Nullable Entry entry) {
        LivingEntity entity = entity(entry);
        if (entity == null) return List.of();
        if (entry != null && data.containsKey(entry.form())) return data.get(entry.form());
        var fields = new ArrayList<Field>();
        fields.add(textField("faction", entry == null ? label("human_faction") : Component.translatable(entry.factionKey())));
        fields.add(textField("opposition", opposition(entry)));
        addAttribute(fields, entity, entry, Attributes.MAX_HEALTH, 60, 0xFFB65454);
        addAttribute(fields, entity, entry, Attributes.ATTACK_DAMAGE, 16, 0xFFBE884F);
        addAttribute(fields, entity, entry, Attributes.ARMOR, 20, 0xFF4D8D9B);
        addAttribute(fields, entity, entry, Attributes.ARMOR_TOUGHNESS, 12, 0xFF6687AD);
        addAttribute(fields, entity, entry, Attributes.MOVEMENT_SPEED, 0.2, 0xFF71985B);
        addAttribute(fields, entity, entry, ForgeMod.SWIM_SPEED.get(), 5, 0xFF538EBA);
        addAttribute(fields, entity, entry, Attributes.KNOCKBACK_RESISTANCE, 1, 0xFF9173AC);
        addAttribute(fields, entity, entry, ChangedAttributes.JUMP_STRENGTH.get(), 3, 0xFFAC9759);
        addAttribute(fields, entity, entry, ChangedAttributes.FALL_RESISTANCE.get(), 3, 0xFF968A70);
        if (entry != null) addAttribute(fields, entity, entry, Attributes.FOLLOW_RANGE, 48, 0xFF827B69);
        double step = entry == null ? 0.6 : 0.6 + value(entity, ForgeMod.STEP_HEIGHT_ADDITION.get());
        fields.add(new Field(label("step_height"), number(step), Mth.clamp((float)step / 2, 0, 1), 0xFF9B8760));
        fields.add(textField("breathing", entry == null ? Component.translatable("origin.changed_synergy.breath_normal")
                : Component.translatable("origin.changed_synergy.breath_" + entry.variant().breatheMode.name().toLowerCase(Locale.ROOT))));
        fields.add(textField("air_capacity", Component.literal(decimal(entry == null ? 15 : value(entity, ChangedAttributes.AIR_CAPACITY.get())) + " s")));
        if (entry != null) {
            var variant = entry.variant();
            fields.add(new Field(Component.translatable("origin.changed_synergy.gliding"), label(variant.canGlide ? "yes" : "no"), -1, 0));
            fields.add(new Field(Component.translatable("origin.changed_synergy.climbing"), label(variant.canClimb ? "yes" : "no"), -1, 0));
            fields.add(textField("extra_jumps", number(variant.extraJumpCharges)));
        }
        var dimensions = entity.getType().getDimensions();
        fields.add(textField("dimensions", Component.literal(decimal(dimensions.width) + " × " + decimal(dimensions.height) + " m")));
        var result = List.copyOf(fields);
        if (entry != null) data.put(entry.form(), result);
        return result;
    }

    private static Component opposition(@Nullable Entry entry) {
        if (entry == null) return label("none");
        String account = "faction:" + entry.faction() + (entry.faction().equals("light") ? ":" + entry.region() : "");
        for (var rivalry : FactionDiplomacy.RIVALRIES) {
            String other = rivalry.first().equals(account) ? rivalry.second() : rivalry.second().equals(account) ? rivalry.first() : null;
            if (other != null) return Component.translatable("faction.changed_synergy." + other.substring(8).replace(':', '.'));
        }
        return label("none");
    }

    private static void addAttribute(List<Field> fields, LivingEntity entity, @Nullable Entry entry, Attribute attribute, double maximum, int color) {
        AttributeSupplier human = DefaultAttributes.getSupplier(EntityType.PLAYER);
        double value = entry == null ? human.hasAttribute(attribute) ? human.getBaseValue(attribute) : attribute.getDefaultValue()
                : value(entity, attribute);
        // Changed maps these NPC attributes into player units on transfur.
        if (entry != null && attribute == Attributes.MOVEMENT_SPEED) value *= 0.1;
        if (entry != null && attribute == ForgeMod.SWIM_SPEED.get()) value *= Mth.map(value, 1, 5, 1, 0.75);
        fields.add(new Field(Component.translatable(attribute.getDescriptionId()), number(value), Mth.clamp((float)(value / maximum), 0, 1), color));
    }

    private static double value(LivingEntity entity, Attribute attribute) {
        return entity.getAttributes().hasAttribute(attribute) ? entity.getAttributeBaseValue(attribute) : attribute.getDefaultValue();
    }
    private static Component label(String key) { return Component.translatable("origin.changed_synergy.archive." + key); }
    private static Field textField(String key, Component value) { return new Field(label(key), value, -1, 0); }
    private static Component number(double value) { return Component.literal(decimal(value)); }
    private static String decimal(double value) { return String.format(Locale.ROOT, "%.2f", value).replaceAll("0+$", "").replaceAll("\\.$", ""); }

    public void renderPortrait(GuiGraphics graphics, @Nullable Entry entry, int x, int y, int width, int height) {
        render(graphics, entry, x, y, width, height, 0, 0, 1, false, true);
    }
    public void renderDoll(GuiGraphics graphics, @Nullable Entry entry, int x, int y, int width, int height,
            float yaw, float pitch, float zoom, boolean crouch) {
        render(graphics, entry, x, y, width, height, yaw, pitch, zoom, crouch, false);
    }

    private void render(GuiGraphics graphics, @Nullable Entry entry, int x, int y, int width, int height,
            float yaw, float pitch, float zoom, boolean crouch, boolean portrait) {
        LivingEntity entity = entity(entry);
        if (entity == null || width <= 0 || height <= 0) return;
        ResourceLocation form = entry == null ? null : entry.form();
        if (failed.contains(form)) return;
        Minecraft minecraft = Minecraft.getInstance();
        var dispatcher = minecraft.getEntityRenderDispatcher();
        float oldBody = entity.yBodyRot, oldBodyO = entity.yBodyRotO, oldYaw = entity.getYRot(), oldYawO = entity.yRotO;
        float oldPitch = entity.getXRot(), oldPitchO = entity.xRotO, oldHead = entity.yHeadRot, oldHeadO = entity.yHeadRotO;
        Pose oldPose = entity.getPose();
        boolean oldShift = entity.isShiftKeyDown();
        Quaternionf oldCamera = new Quaternionf(dispatcher.cameraOrientation());
        boolean oldHitboxes = dispatcher.shouldRenderHitBoxes();
        Map<ModelPart, Boolean> hiddenPortraitParts = new IdentityHashMap<>();
        Scissor scissor = null;
        try {
            entity.yBodyRot = entity.yBodyRotO = entity.yHeadRot = entity.yHeadRotO = entity.yRotO = 180;
            entity.setYRot(180);
            entity.setXRot(0);
            entity.xRotO = 0;
            entity.setShiftKeyDown(crouch);
            entity.setPose(crouch ? Pose.CROUCHING : Pose.STANDING);
            String geometryKey = String.valueOf(form) + ":" + portrait + ":" + crouch;
            Bounds bounds = geometry.computeIfAbsent(geometryKey, ignored -> modelBounds(entity, portrait));
            float scale = Math.min(width * 0.86f / Math.max(0.1f, portrait ? bounds.width() : Math.max(bounds.width(), bounds.depth())),
                    height * (portrait ? 0.83f : 0.91f) / Math.max(0.1f, bounds.height())) * Mth.clamp(zoom, 0.45f, 2.5f);
            Quaternionf rotation = new Quaternionf().rotateZ((float)Math.PI).rotateX(pitch * Mth.DEG_TO_RAD).rotateY(yaw * Mth.DEG_TO_RAD);
            Vector3f center = rotation.transform(new Vector3f(-bounds.centerX(), 1.501f - bounds.centerY(), bounds.centerZ()));
            float centerX = x + width / 2f - center.x * scale;
            float centerY = y + height / 2f - center.y * scale;
            scissor = Scissor.begin(graphics, x, y, width, height);
            PoseStack pose = new PoseStack();
            pose.last().pose().set(graphics.pose().last().pose());
            // The folder's flat GUI scaling must not reshape model lighting normals.
            pose.last().normal().identity();
            pose.translate(centerX, centerY, 50);
            // Match InventoryScreen: its GUI reflection scales positions, not lighting normals.
            pose.mulPoseMatrix(new Matrix4f().scaling(scale, scale, -scale));
            pose.mulPose(rotation);
            Lighting.setupForEntityInInventory();
            dispatcher.setRenderShadow(false);
            dispatcher.setRenderHitBoxes(false);
            if (portrait && dispatcher.getRenderer(entity) instanceof LivingEntityRenderer<?, ?> living
                    && living.getModel() instanceof HeadedModel headed
                    && living.getModel() instanceof DoubleHeadedModel<?> doubled && doubled.getOtherHead() != null) {
                // prepareMobModel restores visibility; skipDraw keeps the secondary head out of this photo only.
                // Use the vanilla interface so getHead is also remapped in production for multiheaded models.
                List<ModelPart> otherHeads = doubled instanceof TripleHeadedModel<?> triple
                        ? List.of(headed.getHead(), triple.getOtherHead()) : List.of(doubled.getOtherHead());
                otherHeads.stream().flatMap(ModelPart::getAllParts).forEach(part -> {
                    hiddenPortraitParts.put(part, part.skipDraw);
                    part.skipDraw = true;
                });
            }
            RenderSystem.runAsFancy(() -> dispatcher.render(entity, 0, 0, 0, 0, 1, pose, graphics.bufferSource(), 15728880));
            graphics.flush();
        } catch (RuntimeException error) {
            failed.add(form);
            ChangedSynergyMod.LOGGER.warn("Archive model preview failed for {}", entry == null ? "human" : entry.form(), error);
        } finally {
            hiddenPortraitParts.forEach((part, skipDraw) -> part.skipDraw = skipDraw);
            if (scissor != null) scissor.close();
            entity.yBodyRot = oldBody; entity.yBodyRotO = oldBodyO;
            entity.setYRot(oldYaw); entity.yRotO = oldYawO;
            entity.setXRot(oldPitch); entity.xRotO = oldPitchO;
            entity.yHeadRot = oldHead; entity.yHeadRotO = oldHeadO;
            entity.setShiftKeyDown(oldShift); entity.setPose(oldPose);
            dispatcher.overrideCameraOrientation(oldCamera);
            dispatcher.setRenderHitBoxes(oldHitboxes);
            dispatcher.setRenderShadow(true);
            Lighting.setupFor3DItems();
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Bounds modelBounds(LivingEntity entity, boolean headOnly) {
        var renderer = Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity);
        if (!(renderer instanceof LivingEntityRenderer living)) return fallbackBounds(entity, headOnly);
        EntityModel model = living.getModel();
        model.riding = false;
        model.attackTime = 0;
        model.young = entity.isBaby();
        model.prepareMobModel(entity, 0, 0, 1);
        model.setupAnim(entity, 0, 0, 0, 0, 0);
        var bounds = new Bounds();
        ModelPart head = model instanceof TripleHeadedModel<?> triple ? triple.getCenterHead()
                : model instanceof HeadedModel headed ? headed.getHead() : null;
        if (headOnly && head != null) {
            var stack = new PoseStack();
            if (model instanceof AdvancedHumanoidModel<?> advanced) {
                advanced.scaleForHead(stack);
                advanced.getAllParts().filter(stem -> stem.getLeaf() == head).findFirst().ifPresent(stem -> {
                    for (int i = 0; i < stem.stem.size() - 1; i++) stem.stem.get(i).translateAndRotate(stack);
                });
            }
            head.visit(stack, bounds::visit);
        } else if (model instanceof AdvancedHumanoidModel<?> advanced) {
            Set<ModelPart> heads = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            heads.add(((HeadedModel) advanced).getHead());
            if (model instanceof DoubleHeadedModel<?> doubled) heads.add(doubled.getOtherHead());
            if (model instanceof TripleHeadedModel<?> triple) heads.add(triple.getCenterHead());
            advanced.getRootLevelLimbs().filter(part -> part.visible).forEach(part -> {
                var stack = new PoseStack();
                boolean headRoot = part.getAllParts().anyMatch(heads::contains);
                boolean bodyRoot = part.getAllParts().anyMatch(child -> child == advanced.getTorso());
                // The original enlarged models render their head roots and body roots with separate transforms.
                // A whole root containing both stays a body root; visiting it already includes its nested head.
                if (headRoot && !bodyRoot) advanced.scaleForHead(stack);
                else advanced.scaleForBody(stack);
                part.visit(stack, bounds::visit);
            });
        } else {
            return fallbackBounds(entity, headOnly);
        }
        return bounds.valid() ? bounds : fallbackBounds(entity, headOnly);
    }

    private static Bounds fallbackBounds(LivingEntity entity, boolean headOnly) {
        Bounds bounds = new Bounds();
        float height = headOnly ? Math.max(0.3f, entity.getBbHeight() * 0.35f) : Math.max(0.3f, entity.getBbHeight());
        float center = headOnly ? 1.501f - entity.getEyeHeight() : 1.501f - height / 2;
        bounds.include(-entity.getBbWidth() / 2, center - height / 2, -entity.getBbWidth() / 2);
        bounds.include(entity.getBbWidth() / 2, center + height / 2, entity.getBbWidth() / 2);
        return bounds;
    }

    private static final class Bounds {
        float minX = Float.POSITIVE_INFINITY, minY = Float.POSITIVE_INFINITY, minZ = Float.POSITIVE_INFINITY;
        float maxX = Float.NEGATIVE_INFINITY, maxY = Float.NEGATIVE_INFINITY, maxZ = Float.NEGATIVE_INFINITY;
        void include(float x, float y, float z) {
            minX = Math.min(minX, x); minY = Math.min(minY, y); minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x); maxY = Math.max(maxY, y); maxZ = Math.max(maxZ, z);
        }
        void visit(PoseStack.Pose pose, String path, int index, ModelPart.Cube cube) {
            for (int corner = 0; corner < 8; corner++) {
                Vector3f vertex = pose.pose().transformPosition(new Vector3f((corner & 1) == 0 ? cube.minX / 16 : cube.maxX / 16,
                        (corner & 2) == 0 ? cube.minY / 16 : cube.maxY / 16, (corner & 4) == 0 ? cube.minZ / 16 : cube.maxZ / 16));
                include(vertex.x, vertex.y, vertex.z);
            }
        }
        boolean valid() { return Float.isFinite(minX) && height() > 0; }
        float width() { return maxX - minX; }
        float height() { return maxY - minY; }
        float depth() { return maxZ - minZ; }
        float centerX() { return (minX + maxX) / 2; }
        float centerY() { return (minY + maxY) / 2; }
        float centerZ() { return (minZ + maxZ) / 2; }
    }

    /** GuiGraphics scissor ignores its pose matrix; paper captures use a different target size. */
    private record Scissor(boolean enabled, int[] box, boolean depthMask, double clearDepth) implements AutoCloseable {
        static Scissor begin(GuiGraphics graphics, int x, int y, int width, int height) {
            graphics.flush();
            boolean enabled = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            int[] previous = new int[4];
            GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, previous);
            var matrix = graphics.pose().last().pose();
            var bounds = new Bounds();
            for (int corner = 0; corner < 4; corner++) {
                Vector3f point = matrix.transformPosition(new Vector3f(
                        x + ((corner & 1) == 0 ? 0 : width), y + ((corner & 2) == 0 ? 0 : height), 0));
                bounds.include(point.x, point.y, point.z);
            }
            Minecraft minecraft = Minecraft.getInstance();
            double scale = ArchivePaperRenderer.isCapturing() ? 1 : minecraft.getWindow().getGuiScale();
            int targetHeight = ArchivePaperRenderer.isCapturing() ? ArchivePaperRenderer.captureTargetHeight() : minecraft.getWindow().getHeight();
            int left = (int)Math.floor(bounds.minX * scale);
            int bottom = targetHeight - (int)Math.ceil(bounds.maxY * scale);
            int right = (int)Math.ceil(bounds.maxX * scale);
            int top = targetHeight - (int)Math.floor(bounds.minY * scale);
            if (enabled) {
                left = Math.max(left, previous[0]); bottom = Math.max(bottom, previous[1]);
                right = Math.min(right, previous[0] + previous[2]); top = Math.min(top, previous[1] + previous[3]);
            }
            RenderSystem.enableScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
            var scissor = new Scissor(enabled, previous, GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK),
                    GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE));
            scissor.clearPanelDepth();
            return scissor;
        }
        private void clearPanelDepth() {
            // Keep model self-occlusion, without sharing depth with the flat paper or later dialogs.
            // Scissor is still active: no color or depth outside this preview is cleared.
            RenderSystem.depthMask(true);
            RenderSystem.clearDepth(1);
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
        }
        @Override public void close() {
            clearPanelDepth();
            RenderSystem.clearDepth(clearDepth);
            RenderSystem.depthMask(depthMask);
            if (enabled) RenderSystem.enableScissor(box[0], box[1], box[2], box[3]);
            else RenderSystem.disableScissor();
        }
    }
}
