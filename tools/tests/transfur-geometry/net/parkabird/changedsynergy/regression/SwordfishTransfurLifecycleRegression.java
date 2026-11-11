package net.parkabird.changedsynergy.regression;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.client.FormRenderHandler;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.entity.LatexSwordfish;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.init.ChangedSynergyTransfurVariants;

/** Reopens an actual save made during swordfish transfur, using the native first-person renderer. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class SwordfishTransfurLifecycleRegression {
    private static final String WORLD = "swordfish-transfur-reload";
    private static int stage, wait, checks;
    private static CompletableFuture<Void> serverTask;

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("changed_synergy.verifySwordfishLifecycle") || stage == 9
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"), "private fixture only");
                mc.options.pauseOnLostFocus = false;
                mc.options.renderDistance().set(2);
                mc.options.simulationDistance().set(2);
                var rules = new GameRules();
                rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel(WORLD,
                        new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT),
                        new WorldOptions(73451, false, false), r -> r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                stage = 1;
            }
            if (stage == 4 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                mc.createWorldOpenFlows().loadLevel(mc.screen, WORLD);
                stage = 5;
                wait = 0;
            }
            if (mc.level == null || mc.player == null || mc.player.tickCount < 10 || mc.getOverlay() != null) return;
            if (stage == 1) {
                mc.setScreen(null);
                serverTask = CompletableFuture.runAsync(() -> {
                    var owner = mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                    ProcessTransfur.setPlayerTransfurVariant(owner, ChangedSynergyTransfurVariants.LATEX_SWORDFISH.get(), null, .55F, false);
                }, mc.getSingleplayerServer());
                stage = 2;
            }
            if (stage == 2 && serverTask.isDone()) {
                serverTask.join();
                var form = ProcessTransfur.getPlayerTransfurVariant(mc.player);
                if (form == null || !(form.getChangedEntity() instanceof LatexSwordfish)) {
                    if (++wait > 200) throw new AssertionError("swordfish synchronization timed out");
                    return;
                }
                renderArms(mc);
                serverTask = CompletableFuture.runAsync(() -> {
                    var server = mc.getSingleplayerServer();
                    var owner = server.getPlayerList().getPlayer(mc.player.getUUID());
                    var instance = ProcessTransfur.getPlayerTransfurVariant(owner);
                    instance.transfurProgressionO = instance.transfurProgression = .6F;
                    server.getPlayerList().saveAll();
                }, mc.getSingleplayerServer());
                stage = 3;
            }
            if (stage == 3 && serverTask.isDone()) {
                serverTask.join();
                System.out.println("SWORDFISH_TRANSFUR_SAVED: partial swordfish form");
                stage = 4;
                mc.level.disconnect();
                mc.clearLevel();
                mc.setScreen(new TitleScreen());
                return;
            }
            if (stage == 5) {
                var form = ProcessTransfur.getPlayerTransfurVariant(mc.player);
                if (form == null || !(form.getChangedEntity() instanceof LatexSwordfish)) {
                    if (++wait > 200) throw new AssertionError("saved swordfish form did not reload");
                    return;
                }
                check(form.getParent() == ChangedSynergyTransfurVariants.LATEX_SWORDFISH.get(), "saved species retained");
                mc.setScreen(null);
                renderArms(mc);
                stage = 9;
                System.out.println("SWORDFISH_TRANSFUR_LIFECYCLE_PASS: " + checks + " checks; native first-person arms before/after saving and reopening partial transfur");
                mc.stop();
            }
        } catch (Throwable failure) {
            stage = 9;
            System.err.println("SWORDFISH_TRANSFUR_LIFECYCLE_FAIL");
            failure.printStackTrace();
            mc.stop();
        }
    }

    private static void renderArms(Minecraft mc) {
        var form = ProcessTransfur.getPlayerTransfurVariant(mc.player);
        var renderer = (PlayerRenderer)mc.getEntityRenderDispatcher().getRenderer(mc.player);
        var model = renderer.getModel();
        var buffer = mc.renderBuffers().bufferSource();
        for (float progress : new float[]{.1F, .45F, .55F, .65F, .75F, .9F, 1}) {
            form.transfurProgressionO = form.transfurProgression = progress;
            for (boolean right : new boolean[]{true, false}) {
                check(FormRenderHandler.maybeRenderHand(renderer, new PoseStack(), buffer, 15728880, mc.player,
                        right ? model.rightArm : model.leftArm, right ? model.rightSleeve : model.leftSleeve),
                        "native transformed arm renderer handled " + progress + " right=" + right);
                buffer.endBatch();
            }
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        checks++;
    }
}
