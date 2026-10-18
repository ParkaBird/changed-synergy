package net.parkabird.changedsynergy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.List;
import net.ltxprogrammer.changed.client.ModelPartStem;
import net.ltxprogrammer.changed.client.renderer.AdvancedHumanoidRenderer;
import net.ltxprogrammer.changed.client.renderer.animate.HumanoidAnimator;
import net.ltxprogrammer.changed.client.renderer.layers.CustomEyesLayer;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.renderer.model.TripleHeadedModel;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.entity.BasicPlayerInfo;
import net.ltxprogrammer.changed.entity.EyeStyle;
import net.ltxprogrammer.changed.init.ChangedEntities;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.ai.PureWhiteWolfAdaptation;

/** Reuses Changed's eye styles, colours and blinking on each original animated head. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class PureWhiteAdaptedEyesLayer<T extends ChangedEntity> extends CustomEyesLayer<AdvancedHumanoidModel<T>, T> {
    private final int headIndex;

    private PureWhiteAdaptedEyesLayer(RenderLayerParent<T, AdvancedHumanoidModel<T>> parent, EntityModelSet models, boolean pup, int headIndex) {
        super(parent, models);
        this.headIndex = headIndex;
        if (pup) setHeadShape(HeadShape.PUP);
    }

    @SubscribeEvent public static void addEyes(EntityRenderersEvent.AddLayers event) {
        addEyes(event, ChangedEntities.PURE_WHITE_LATEX_WOLF.get());
        addEyes(event, ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get());
        addEyes(event, ChangedEntities.PURE_WHITE_LATEX_CERBERUS.get());
        addEyes(event, ChangedEntities.LATEX_MUTANT_BLOODCELL_WOLF.get());
    }

    private static <T extends ChangedEntity> void addEyes(EntityRenderersEvent.AddLayers event, EntityType<T> type) {
        AdvancedHumanoidRenderer<T, AdvancedHumanoidModel<T>> renderer =
                event.<T, AdvancedHumanoidRenderer<T, AdvancedHumanoidModel<T>>>getRenderer(type);
        if (renderer == null) return;
        var model = renderer.getModel();
        var heads = model instanceof TripleHeadedModel<?> triple
                ? List.of(model.getHead(), triple.getCenterHead(), triple.getOtherHead()) : List.of(model.getHead());
        for (int index=0; index<heads.size(); index++) {
            ModelPart head = heads.get(index);
            var stem = model.getAllParts().filter(part -> part.getLeaf() == head).findFirst().orElseThrow();
            AdvancedHumanoidModel<T> view = new HeadView<>(model, stem);
            var parent = new RenderLayerParent<T, AdvancedHumanoidModel<T>>() {
                @Override public AdvancedHumanoidModel<T> getModel() { return view; }
                @Override public ResourceLocation getTextureLocation(T entity) { return renderer.getTextureLocation(entity); }
            };
            renderer.addLayer(new PureWhiteAdaptedEyesLayer<>(parent, event.getEntityModels(),
                    type == ChangedEntities.PURE_WHITE_LATEX_WOLF_PUP.get(), heads.size()==3 ? index : -1));
        }
    }

    /** The centre head keeps the original eyes; side heads derive stable eye-only variations. */
    public BasicPlayerInfo appearanceForHead(BasicPlayerInfo original) {
        if (headIndex < 0 || headIndex == 1) return original;
        long seed = (long)original.getLeftIrisColor().toInt() * 0x9E3779B97F4A7C15L
                ^ (long)original.getRightIrisColor().toInt() * 0xD1B54A32D192ED03L
                ^ original.getHairColor().toInt() ^ (long)headIndex * 0x94D049BB133111EBL;
        var random = net.minecraft.util.RandomSource.create(seed);
        var appearance = new BasicPlayerInfo();
        appearance.copyFrom(original);
        appearance.setLeftIrisColor(BasicPlayerInfo.IRIS_COLORS.get(random.nextInt(BasicPlayerInfo.IRIS_COLORS.size())));
        appearance.setRightIrisColor(random.nextFloat() < .05F
                ? BasicPlayerInfo.IRIS_COLORS.get(random.nextInt(BasicPlayerInfo.IRIS_COLORS.size()))
                : appearance.getLeftIrisColor());
        EyeStyle[] styles = EyeStyle.values();
        appearance.setEyeStyle(styles[(original.getEyeStyle().ordinal()+1+random.nextInt(styles.length-1)) % styles.length]);
        return appearance;
    }

    @Override public void render(PoseStack pose, MultiBufferSource buffers, int light, T wolf,
            float swing, float amount, float partial, float age, float yaw, float pitch) {
        if (PureWhiteWolfAdaptation.isAdaptedForm(wolf))
            super.render(pose, buffers, light, wolf, swing, amount, partial, age, yaw, pitch);
    }

    /** Apply the full animated head pose before the native eye mesh. */
    private static final class HeadView<T extends ChangedEntity> extends AdvancedHumanoidModel<T> {
        private final AdvancedHumanoidModel<T> original;
        private final ModelPartStem stem;
        private final ModelPart eyeOrigin = createNullPart();
        HeadView(AdvancedHumanoidModel<T> original, ModelPartStem stem) {
            super(createNullPart());
            this.original = original;
            this.stem = stem;
        }
        @Override public ModelPart getHead() { return eyeOrigin; }
        @Override public void scaleForHead(PoseStack pose) {
            original.scaleForHead(pose);
            stem.translateAndRotate(pose);
        }
        @Override public HumanoidAnimator<T, ?> getAnimator(T entity) { return original.getAnimator(entity); }
        @Override public ModelPart getArm(HumanoidArm arm) { return original.getArm(arm); }
        @Override public ModelPart getLeg(HumanoidArm arm) { return original.getLeg(arm); }
        @Override public ModelPart getTorso() { return original.getTorso(); }
    }
}
