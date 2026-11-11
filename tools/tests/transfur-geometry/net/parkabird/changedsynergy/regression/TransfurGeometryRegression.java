package net.parkabird.changedsynergy.regression;

import java.lang.reflect.InvocationTargetException;
import java.util.List;
import net.ltxprogrammer.changed.client.animations.Limb;
import net.ltxprogrammer.changed.client.renderer.model.AdvancedHumanoidModel;
import net.ltxprogrammer.changed.client.tfanimations.EntityGeometry;
import net.ltxprogrammer.changed.client.tfanimations.TransfurAnimator;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.client.model.*;

/** Calls Changed's actual geometry interpolation, rather than only drawing the finished form. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class TransfurGeometryRegression {
    private static boolean finished;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("changed_synergy.verifyTransfurGeometry") || finished
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
        if (!(mc.screen instanceof TitleScreen) || mc.getOverlay() != null) return;
        finished = true;
        try {
            var transition = TransfurAnimator.class.getDeclaredMethod("transitionModelPart", ModelPart.class,
                    ModelPart.class, EntityGeometry.class, EntityGeometry.class, float.class, boolean.class, boolean.class);
            transition.setAccessible(true);
            int checks = 0, failures = 0;
            for (boolean slim : new boolean[]{false, true}) {
                var human = new PlayerModel<>(mc.getEntityModels().bakeLayer(slim ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), slim);
                List<AdvancedHumanoidModel<?>> models = List.of(
                        new LatexSwordfishModel(mc.getEntityModels().bakeLayer(LatexSwordfishModel.LAYER_LOCATION)),
                        new LatexMuntjacModel<>(mc.getEntityModels().bakeLayer(LatexMuntjacModel.LAYER_LOCATION)),
                        new LatexMuntjacFemaleModel<>(mc.getEntityModels().bakeLayer(LatexMuntjacFemaleModel.LAYER_LOCATION)),
                        new LatexSilkMothModel<>(mc.getEntityModels().bakeLayer(LatexSilkMothModel.LAYER_LOCATION)));
                for (var model : models) for (var limb : Limb.values()) {
                    if (!limb.isVanillaPart()) continue;
                    var before = limb.getModelPart(human);
                    var after = limb.getModelPart(model);
                    if (before == null || after == null) continue;
                    var helper = model.getTransfurHelperModel(limb);
                    if (helper != null) before = helper.getModelPart();
                    try {
                        for (float progress : new float[]{0, .25F, .5F, .75F, 1}) {
                            var geometry = (EntityGeometry)transition.invoke(null, before, after,
                                    new EntityGeometry(before).collapseSimple(),
                                    new EntityGeometry(after, model::shouldPartTransfur).collapseSimple(),
                                    progress, true, false);
                            geometry.visit(part -> {
                                if (!Float.isFinite(part.x) || !Float.isFinite(part.y) || !Float.isFinite(part.z))
                                    throw new AssertionError("non-finite interpolated geometry");
                            });
                            checks++;
                        }
                        System.out.println("TRANSFUR_GEOMETRY_OK: " + model.getClass().getSimpleName() + " " + limb + " slim=" + slim);
                    } catch (InvocationTargetException failure) {
                        failures++;
                        System.err.println("TRANSFUR_GEOMETRY_BAD: " + model.getClass().getSimpleName() + " " + limb + " slim=" + slim);
                        failure.getCause().printStackTrace();
                    }
                }
            }
            if (failures != 0) throw new AssertionError(failures + " invalid limb transitions; successful samples=" + checks);
            System.out.println("TRANSFUR_GEOMETRY_PASS: " + checks + " native interpolation samples");
        } catch (Throwable failure) {
            System.err.println("TRANSFUR_GEOMETRY_FAIL");
            failure.printStackTrace();
        } finally { mc.stop(); }
    }
}
