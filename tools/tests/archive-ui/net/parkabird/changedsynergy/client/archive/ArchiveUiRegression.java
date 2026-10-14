package net.parkabird.changedsynergy.client.archive;

import com.mojang.blaze3d.systems.RenderSystem;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.ltxprogrammer.changed.client.gui.ContentWarningScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ChangedSynergyMod;
import net.parkabird.changedsynergy.client.NativeOriginScreen;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import net.parkabird.changedsynergy.network.NativeOriginStatePacket;
import net.parkabird.changedsynergy.origin.NativeOriginCatalog;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/** Input-driven original-model gallery, compiled only by the isolated init script. */
@Mod.EventBusSubscriber(modid = ChangedSynergyMod.MOD_ID, value = Dist.CLIENT)
public final class ArchiveUiRegression {
    private static final String WORLD = "archive-ui-regression";
    private static final ArrayDeque<Step> steps = new ArrayDeque<>();
    private static boolean loading, opened, finished;
    private static boolean hidden;
    private static long started = System.nanoTime(), previous;
    private static int assertions, rememberedAquatic, reloadSettledFrames;
    private static int firstLeftColor;
    private static int randomSlipColor;
    private static NativeOriginScreen screen;
    private static String lastScreen;
    private static CompletableFuture<Void> reload;
    private static ArchiveGeometry.Point hoverPaper;
    private record Step(String label, long delay, Runnable action) {}
    private record EntityState(net.minecraft.world.entity.Pose pose, boolean shift, float yaw, float pitch,
            float body, float head) {
        static EntityState of(net.minecraft.world.entity.LivingEntity entity) {
            return new EntityState(entity.getPose(), entity.isShiftKeyDown(), entity.getYRot(), entity.getXRot(), entity.yBodyRot, entity.yHeadRot);
        }
    }
    private record RenderState(int draw, int read, List<Integer> viewport, List<Integer> box,
            boolean scissor, boolean depth, boolean cull, boolean blend, boolean depthMask, int depthFunction,
            List<Float> color, org.joml.Matrix4f projection, Object sorting, Object shader) {
        static RenderState capture() {
            int[] viewport = new int[4], box = new int[4];
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport); GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, box);
            float[] color = RenderSystem.getShaderColor();
            return new RenderState(GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING), GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING),
                    java.util.Arrays.stream(viewport).boxed().toList(), java.util.Arrays.stream(box).boxed().toList(),
                    GL11.glIsEnabled(GL11.GL_SCISSOR_TEST), GL11.glIsEnabled(GL11.GL_DEPTH_TEST), GL11.glIsEnabled(GL11.GL_CULL_FACE),
                    GL11.glIsEnabled(GL11.GL_BLEND), GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK), GL11.glGetInteger(GL11.GL_DEPTH_FUNC),
                    List.of(color[0], color[1], color[2], color[3]),
                    new org.joml.Matrix4f(RenderSystem.getProjectionMatrix()), RenderSystem.getVertexSorting(), RenderSystem.getShader());
        }
    }

    private static boolean enabled() { return Boolean.getBoolean("archive.ui.regression"); }
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
        System.out.println("ARCHIVE_CHECK_PASS: " + message);
    }
    private static void step(String label, long delay, Runnable action) { steps.add(new Step(label, delay, action)); }
    private static Object value(Object object, String name) {
        try {
            Field field = object.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(object);
        } catch (ReflectiveOperationException failure) { throw new AssertionError("Read field " + name, failure); }
    }
    private static int integer(String name) { return ((Number)value(screen, name)).intValue(); }
    private static boolean flag(String name) { return (Boolean)value(screen, name); }
    private static String state(String name) { return String.valueOf(value(screen, name)); }
    private static int pageValue(String name) { return ((Number)value(value(screen, "paper"), name)).intValue(); }
    private static NativeOriginCatalog.Entry detail() { return (NativeOriginCatalog.Entry)value(value(screen, "paper"), "detail"); }
    private static ArchiveGeometry.Point paperPoint(double x, double y) {
        Object pose = value(screen, "pose");
        var center = new ArchiveGeometry.Point(((Number)value(pose, "x")).doubleValue(), ((Number)value(pose, "y")).doubleValue());
        var point = ArchiveGeometry.paperToCanvas(new ArchiveGeometry.Point(x, y), center,
                Math.toRadians(((Number)value(pose, "angle")).doubleValue()), ((Number)value(pose, "scale")).doubleValue());
        return ArchiveGeometry.fit(screen.width, screen.height).screen(point);
    }
    private static void paperClick(double x, double y, int button) {
        var point = paperPoint(x, y); click(point.x(), point.y(), button);
    }
    private static void tab(int category) {
        double[] bounds = ArchivePaperRenderer.tab(category);
        double x = category < pageValue("category") ? -ArchiveGeometry.PAPER_W - bounds[2] / 2 : bounds[0] + bounds[2] / 2;
        paperClick(x, bounds[1] + bounds[3] / 2, 0);
    }

    @SubscribeEvent public static void fixtureStarted(ServerStartedEvent event) {
        if (!enabled() || !event.getServer().getWorldData().getLevelName().equals(WORLD)) return;
        event.getServer().overworld().getGameRules().getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, event.getServer());
    }

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (!enabled() || finished || event.phase != TickEvent.Phase.END) return;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (!hidden && Boolean.getBoolean("archive.ui.regression.hidden")) {
                check(minecraft.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of(".codex-tmp", "archive-ui-client", "run")),
                        "Hidden renderer fixture uses its own game directory");
                org.lwjgl.glfw.GLFW.glfwHideWindow(minecraft.getWindow().getWindow()); hidden = true;
            }
            if (System.nanoTime() - started > 300_000_000_000L) throw new AssertionError("UI fixture timed out");
            String currentScreen = minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName();
            if (!currentScreen.equals(lastScreen)) { lastScreen = currentScreen; System.out.println("ARCHIVE_FIXTURE_SCREEN: " + currentScreen); }
            if (!loading && minecraft.getOverlay() == null && minecraft.screen instanceof AccessibilityOnboardingScreen onboarding) {
                check(minecraft.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of(".codex-tmp", "archive-ui-client", "run")),
                        "Private onboarding directory");
                onboarding.onClose();
                return;
            }
            if (!loading && minecraft.getOverlay() == null && minecraft.screen instanceof ContentWarningScreen warning) {
                check(minecraft.gameDirectory.toPath().toAbsolutePath().normalize().endsWith(Path.of(".codex-tmp", "archive-ui-client", "run")),
                        "Private Changed content-warning directory");
                // Changed's single Proceed button saves acknowledgement to this fixture's client config.
                warning.mouseClicked(warning.width / 2.0, warning.height / 4 + 130, 0);
                warning.mouseReleased(warning.width / 2.0, warning.height / 4 + 130, 0);
                return;
            }
            if (!loading && minecraft.screen instanceof TitleScreen && minecraft.getOverlay() == null) {
                Path directory = minecraft.gameDirectory.toPath().toAbsolutePath().normalize();
                check(directory.endsWith(Path.of(".codex-tmp", "archive-ui-client", "run")), "Private fixture directory");
                loading = true;
                minecraft.options.pauseOnLostFocus = false;
                minecraft.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                minecraft.options.save();
                minecraft.options.renderDistance().set(4);
                minecraft.options.simulationDistance().set(4);
                minecraft.options.guiScale().set(2);
                if (Files.isRegularFile(directory.resolve("saves").resolve(WORLD).resolve("level.dat"))) {
                    minecraft.createWorldOpenFlows().loadLevel(minecraft.screen, WORLD);
                } else {
                    GameRules rules = new GameRules();
                    rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, null);
                    rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                    rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
                    var settings = new LevelSettings(WORLD, GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                            rules, WorldDataConfiguration.DEFAULT);
                    minecraft.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(73451L, false, false),
                            registry -> registry.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                }
            }
            if (!opened && minecraft.level != null && minecraft.player != null && minecraft.player.isAlive()) {
                check(minecraft.hasSingleplayerServer(), "Private integrated server");
                check(!minecraft.getSingleplayerServer().overworld().getGameRules().getBoolean(ChangedSynergyGameRules.NATIVE_ORIGINS), "Origin gameplay off in fixture");
                opened = true;
                if (Boolean.getBoolean("native.roleplay.ui.only")) {
                    net.parkabird.changedsynergy.regression.InstinctBodyClientRegression.run(minecraft);
                    net.parkabird.changedsynergy.regression.TakeoverGuiClientRegression.run(minecraft);
                    var settings=net.parkabird.changedsynergy.client.SettingsClientRegression.prepare();
                    var previews=net.parkabird.changedsynergy.regression.NativeRoleplayClientRegression.prepare(minecraft);
                    for(var preview:previews.entrySet()) {
                        step("native roleplay open "+preview.getKey(),150,()->minecraft.setScreen(preview.getValue()));
                        if(preview.getKey().equals("chat-command"))step("show command candidates",300,()->minecraft.screen.keyPressed(258,0,0));
                        step("native roleplay capture "+preview.getKey(),1000,()->{
                            check(minecraft.screen==preview.getValue(),"Roleplay screen remains open while its body is synchronized");
                            capture("native-roleplay-"+preview.getKey());
                        });
                    }
                    step("native roleplay complete",1000,()->{
                        net.parkabird.changedsynergy.client.SettingsClientRegression.verifyWorldChange();
                        finished=true;minecraft.stop();
                    });
                    previous=System.nanoTime();return;
                }
                if (Boolean.getBoolean("camp.social.ui.only")) {
                    net.parkabird.changedsynergy.regression.TakeoverGuiClientRegression.run(minecraft);
                    net.parkabird.changedsynergy.regression.CampSocialClientRegression.run(minecraft);
                    net.parkabird.changedsynergy.regression.RadialSpectatorClientRegression.run(minecraft);
                    finished = true; minecraft.stop();
                    return;
                }
                if (Boolean.getBoolean("grab.qte.only")) {
                    net.parkabird.changedsynergy.regression.GrabQteClientRegression.run(minecraft.player);
                    finished = true; minecraft.stop();
                    return;
                }
                if (!Boolean.getBoolean("archive.ui.localization.only")) {
                    net.parkabird.changedsynergy.regression.PlayerVisualClientRegression.run(minecraft.player);
                    net.parkabird.changedsynergy.regression.HypnosisClientRegression.run(minecraft.player);
                    net.parkabird.changedsynergy.regression.OriginConfigClientRegression.run();
                }
                if (Boolean.getBoolean("settings.ui.only")) {
                    var previews = net.parkabird.changedsynergy.client.SettingsClientRegression.prepare();
                    for (var preview : previews.entrySet()) {
                        step("settings open " + preview.getKey(), 150, () -> minecraft.setScreen(preview.getValue()));
                        step("settings capture " + preview.getKey(), 1200, () -> capture("settings-" + preview.getKey()));
                    }
                    step("settings client complete", 1000, () -> {
                        net.parkabird.changedsynergy.client.SettingsClientRegression.verifyWorldChange();
                        finished = true; minecraft.stop();
                    });
                    previous = System.nanoTime();
                    return;
                }
                if (Boolean.getBoolean("camp.ui.only")) {
                    var previews = net.parkabird.changedsynergy.regression.CampUiClientRegression.prepare();
                    for (var preview : previews.entrySet()) {
                        step("camp open " + preview.getKey(), 150, () -> minecraft.setScreen(preview.getValue()));
                        step("camp capture " + preview.getKey(), 1200,
                                () -> net.parkabird.changedsynergy.regression.CampUiClientRegression.capture(preview.getValue(), preview.getKey()));
                    }
                    step("camp client complete", 150, () -> {
                        System.out.println("CAMP_UI_CLIENT_REGRESSION_PASS");
                        finished = true; minecraft.stop();
                    });
                    previous = System.nanoTime();
                    return;
                }
                screen = new NativeOriginScreen(new NativeOriginStatePacket(0, UUID.randomUUID(), true, false, Component.empty(), NativeOriginCatalog.entries(registries())));
                minecraft.setScreen(screen);
                configureGallery();
                previous = System.nanoTime();
            }
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void frame(TickEvent.RenderTickEvent event) {
        if (!enabled() || finished || !opened || event.phase != TickEvent.Phase.END) return;
        try {
            if (reload != null) {
                if (!reload.isDone() || Minecraft.getInstance().getOverlay() != null) { reloadSettledFrames = 0; return; }
                if (reloadSettledFrames++ < 2) return;
            }
            Step step = steps.peek();
            if (step == null || System.nanoTime() - previous < step.delay * 1_000_000L) return;
            steps.remove();
            System.out.println("ARCHIVE_STEP: " + step.label);
            step.action.run();
            previous = System.nanoTime();
        } catch (Throwable failure) { fail(failure); }
    }

    @SubscribeEvent public static void hoverFrame(ScreenEvent.Render.Pre event) {
        if (!enabled() || finished || hoverPaper == null || event.getScreen() != screen) return;
        // Fixture-only pointer coordinates enter the same public render path; UI state remains read-only.
        event.setCanceled(true);
        try {
            var pointer = paperPoint(hoverPaper.x(), hoverPaper.y());
            screen.render(event.getGuiGraphics(), (int)pointer.x(), (int)pointer.y(), event.getPartialTick());
        } catch (Throwable failure) { fail(failure); }
    }

    private static void capture(String name) {
        Minecraft minecraft = Minecraft.getInstance();
        Screenshot.grab(minecraft.gameDirectory, "archive-" + name + ".png", minecraft.getMainRenderTarget(),
                message -> System.out.println("ARCHIVE_SCREENSHOT: " + name + " " + message.getString()));
    }
    private static int paperPixel(double x, double y) {
        Minecraft minecraft = Minecraft.getInstance();
        var point = paperPoint(x, y);
        try (var image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget())) {
            double scale = minecraft.getWindow().getGuiScale();
            return image.getPixelRGBA((int)(point.x() * scale), (int)(point.y() * scale));
        }
    }
    private static void curlCapture(String name) {
        Object turn = value(screen, "turn"), papers = value(screen, "papers");
        check(turn != null && (Boolean)value(turn, "captured") && (Boolean)value(papers, "turnCaptured")
                && value(papers, "snapshot") != null && !ArchivePaperRenderer.isCapturing(), "Visible curl has captured source sheet: " + name);
        capture(name);
    }
    private static void key(int key) { screen.keyPressed(key, 0, 0); screen.keyReleased(key, 0, 0); }
    private static void click(double x, double y, int button) { screen.mouseClicked(x, y, button); screen.mouseReleased(x, y, button); }

    private static void configureGallery() {
        if (Boolean.getBoolean("archive.ui.localization.only")) {
            step("Russian resources", 200, () -> changeLanguage("ru_ru"));
            step("Russian contribution active", 200, () -> {
                check(Component.translatable("origin.changed_synergy.native_confirm").getString().startsWith("Это тело"), "Russian origin paragraph is loaded");
                check(Component.translatable("name.changed_synergy.generated.localized_choice", "Имя").getString().equals("Имя"), "Russian generated-name argument resolves");
                capture("ru-welcome"); key(257);
            });
            step("Russian open folder", 1100, () -> key(257));
            step("Russian human file", 1900, () -> { capture("ru-human"); paperClick(380, 1040, 0); });
            step("Russian human confirmation", 250, () -> { capture("ru-human-confirm"); key(256); });
            appendDetail("changed:form_dark_latex_wolf/male", "ru-wolf");
            step("Russian native confirmation", 200, () -> paperClick(380, 1040, 0));
            step("Russian native overflow", 250, () -> {
                check(flag("confirmation") && number("maxDialogScroll") > 0, "Long Russian confirmation has a scroll range");
                capture("ru-native-confirm-top");
                float zoom = ((Number)value(screen, "zoom")).floatValue();
                var p = paperPoint(200, 500); screen.mouseScrolled(p.x(), p.y(), -1);
                check(number("dialogScroll") > 0 && ((Number)value(screen, "zoom")).floatValue() == zoom,
                        "Wheel scrolls modal text without zooming the covered paper doll");
                key(268); check(number("dialogScroll") == 0, "Home reaches the start");
                key(264); check(number("dialogScroll") == 1, "Down moves one line");
                key(265); check(number("dialogScroll") == 0, "Up restores the first line");
                key(267); check(number("dialogScroll") > 0, "Page Down advances the paragraph");
                key(266); check(number("dialogScroll") == 0, "Page Up restores the first page");
                key(269); check(number("dialogScroll") == number("maxDialogScroll"), "End exposes the final paragraph lines");
            });
            step("Russian final confirmation lines", 200, () -> {
                capture("ru-native-confirm-end");
                int x = number("dialogTextX") + number("dialogTextWidth") - 3;
                var start = paperPoint(x, number("dialogTextY") + 2);
                var end = paperPoint(x, number("dialogTextBottom") - 1);
                screen.mouseClicked(start.x(), start.y(), 0);
                check(flag("draggingDialogScroll"), "Scrollbar can be dragged");
                screen.mouseDragged(end.x(), end.y(), 0, end.x() - start.x(), end.y() - start.y());
                screen.mouseReleased(end.x(), end.y(), 0);
                check(number("dialogScroll") == number("maxDialogScroll") && !flag("draggingDialogScroll"), "Dragging reaches the end and releases the scrollbar");
                Minecraft minecraft = Minecraft.getInstance(); minecraft.options.guiScale().set(3); minecraft.resizeDisplay();
            });
            step("Russian scale-three confirmation", 250, () -> {
                check(flag("confirmation") && number("dialogScroll") == number("maxDialogScroll"), "GUI resizing preserves confirmation and reading position");
                capture("ru-native-confirm-scale3"); Minecraft.getInstance().getWindow().setWindowed(854, 480);
            });
            step("Russian small window", 600, () -> {
                capture("ru-native-confirm-854x480"); paperClick(200, 775, 0);
                check(!flag("confirmation") && detail() != null, "Fixed Back button remains clickable in a small window");
                paperClick(380, 1040, 0);
                check(flag("confirmation") && number("dialogScroll") == 0, "Reopening a confirmation resets its reading position");
                Minecraft minecraft = Minecraft.getInstance(); minecraft.getWindow().setWindowed(1280, 720);
                minecraft.options.guiScale().set(2); minecraft.resizeDisplay(); key(256);
                screen.receive(new NativeOriginStatePacket(2, UUID.randomUUID(), true, false,
                        Component.translatable("origin.changed_synergy.no_habitat"), NativeOriginCatalog.entries(registries())));
            });
            step("Russian birthplace notice", 300, () -> {
                check(number("maxDialogScroll") > 0, "Long birthplace notice is scrollable");
                capture("ru-no-habitat-top"); key(269);
                check(number("dialogScroll") == number("maxDialogScroll"), "Birthplace notice reaches its final warning");
            });
            step("Russian birthplace final lines", 250, () -> {
                capture("ru-no-habitat-end");
                screen.receive(new NativeOriginStatePacket(0, UUID.randomUUID(), true, false, Component.empty(), NativeOriginCatalog.entries(registries())));
                paperClick(380, 1040, 0); key(269);
            });
            step("Russian confirmation Back", 250, () -> {
                paperClick(200, 775, 0);
                check(!flag("confirmation") && Minecraft.getInstance().getConnection().getConnection().isConnected(), "Back preserves the connected selection session");
                changeLanguage("en_us");
            });
            step("English confirmation", 250, () -> { paperClick(380, 1040, 0); });
            step("English confirmation end", 250, () -> { key(269); capture("en-native-confirm-end"); key(256); changeLanguage("zh_cn"); });
            step("Chinese confirmation", 250, () -> paperClick(380, 1040, 0));
            step("Chinese confirmation end", 250, () -> {
                key(269); capture("zh-native-confirm-end"); key(256);
                check(!flag("confirmation") && detail() != null, "Chinese confirmation keeps the dossier usable");
                changeLanguage("en_us");
            });
            step("localized archive complete", 250, () -> {
                System.out.println("ARCHIVE_LOCALIZATION_UI_REGRESSION_PASS: " + assertions + " assertions");
                finished = true; Minecraft.getInstance().stop();
            });
            return;
        }
        if (Boolean.getBoolean("archive.ui.factions.only")) {
            step("enter archive", 700, () -> key(257));
            step("open folder", 1100, () -> key(257));
            step("Chinese faction resources", 2100, () -> {
                var minecraft = Minecraft.getInstance(); minecraft.getLanguageManager().setSelected("zh_cn");
                minecraft.options.languageCode = "zh_cn"; reloadSettledFrames = 0; reload = minecraft.reloadResourcePacks();
            });
            appendDetail("changed:form_latex_gnoll_taur", "faction-gnoll-zh");
            step("Gnoll regional display", 200, () -> {
                var models = (ArchiveOriginModels)value(screen, "models");
                var fields = models.fields(detail());
                check(fields.get(0).value().getString().equals("热带草原变体族群"), "Gnoll faction label uses Savanna");
                check(fields.get(1).value().getString().equals("沼泽变体族群"), "Gnoll opposing faction label uses Swamp");
            });
            appendDetail("changed:form_latex_red_panda", "faction-red-panda-zh");
            step("Jungle regional display", 200, () -> {
                var fields = ((ArchiveOriginModels)value(screen, "models")).fields(detail());
                check(fields.get(0).value().getString().equals("丛林变体族群"), "Red Panda faction label uses Jungle");
                check(fields.get(1).value().getString().equals("针叶林变体族群"), "Red Panda opposing faction label uses Taiga");
                System.out.println("ARCHIVE_FACTIONS_UI_REGRESSION_PASS: " + assertions + " assertions");
                finished = true; Minecraft.getInstance().stop();
            });
            return;
        }
        if (Boolean.getBoolean("archive.ui.random.only")) {
            step("enter archive", 700, () -> key(257));
            step("open folder", 1100, () -> key(257));
            step("full-random raffle slip", 2100, () -> {
                randomSlipColor = paperPixel(-655, 1003);
                capture("random-slip-en"); paperClick(-540, 915, 0);
                check(flag("confirmation") && ((Number)value(screen, "randomChoice")).intValue() == 0, "Raffle slip opens full-random confirmation");
            });
            step("random confirmation", 250, () -> { capture("random-confirm-en"); key(256); });
            for (int category = 1; category <= 5; category++) {
                final int group = category;
                step("random category " + group, 200, () -> tab(group));
                if (group == 1) step("slip behind turning sheet", 350, () -> curlCapture("random-slip-turn-covered"));
                step("category-first random card " + group, 1150, () -> {
                    check(paperPixel(-655, 1003) != randomSlipColor, "Category sheets occlude the cover slip " + group);
                    paperClick(-540, 915, 0);
                    check(!flag("confirmation"), "Covered cover slip cannot be clicked " + group);
                    capture("random-category-" + group); paperClick(150, 218, 0);
                    check(flag("confirmation") && ((Number)value(screen, "randomChoice")).intValue() == group,
                            "First record chooses only category " + group);
                    key(256);
                });
            }
            step("ordinary species retained", 150, () -> {
                paperClick(383, 218, 0); check(value(screen, "turn") != null, "Second card still opens the first real species");
            });
            step("ordinary species detail", 1150, () -> {
                check(detail() != null && detail().equals(categoryEntries(5).get(0)), "Random card does not replace a real species");
                check(paperPixel(-655, 1003) != randomSlipColor, "Species dossiers also cover the slip");
                paperClick(-540, 915, 0); check(!flag("confirmation"), "A dossier cannot click through to the slip"); key(256);
            });
            step("Chinese random resources", 1200, () -> {
                var minecraft = Minecraft.getInstance(); minecraft.getLanguageManager().setSelected("zh_cn");
                minecraft.options.languageCode = "zh_cn"; reloadSettledFrames = 0; reload = minecraft.reloadResourcePacks();
            });
            step("Chinese random card", 700, () -> { capture("random-category-zh"); tab(0); });
            step("Chinese raffle slip", 1150, () -> {
                check(paperPixel(-655, 1003) == randomSlipColor, "Returning home reveals the same attached slip");
                capture("random-slip-zh"); paperClick(-540, 915, 0);
            });
            step("Chinese random confirmation", 250, () -> {
                capture("random-confirm-zh");
                check(flag("confirmation"), "Chinese random confirmation remains interactive");
                key(256); key(256);
            });
            step("attached slip closing with cover", 160, () -> capture("random-slip-close-hinge"));
            step("reopen cover", 1600, () -> { check(state("view").equals("DESKTOP"), "Folder closes after returning home"); key(257); });
            step("slip stays on reopened cover", 2100, () -> {
                check(paperPixel(-655, 1003) == randomSlipColor, "Closing and reopening preserves the cover slip");
                capture("random-slip-reopened");
                System.out.println("ARCHIVE_RANDOM_UI_REGRESSION_PASS: " + assertions + " assertions");
                finished = true; Minecraft.getInstance().stop();
            });
            return;
        }
        if (Boolean.getBoolean("archive.ui.welcome.only")) {
            step("English welcome preview", 1400, () -> capture("welcome-synergy-truetransfur-en"));
            step("Chinese welcome resources", 100, () -> {
                Minecraft minecraft = Minecraft.getInstance(); minecraft.getLanguageManager().setSelected("zh_cn");
                minecraft.options.languageCode = "zh_cn"; reloadSettledFrames = 0;
                reload = minecraft.reloadResourcePacks();
            });
            step("Chinese welcome preview", 1400, () -> capture("welcome-synergy-truetransfur-zh"));
            step("smaller GUI welcome", 100, () -> {
                Minecraft minecraft = Minecraft.getInstance(); minecraft.options.guiScale().set(3); minecraft.resizeDisplay();
            });
            step("smaller GUI preview", 700, () -> capture("welcome-synergy-truetransfur-zh-small"));
            step("welcome preview complete", 1000, () -> {
                System.out.println("ARCHIVE_WELCOME_PREVIEW_PASS"); finished = true; Minecraft.getInstance().stop();
            });
            return;
        }
        // Only public mouse/key methods navigate; all reflection here is read-only.
        step("welcome text reveal", 300, () -> capture("00-welcome-reveal"));
        step("welcome", 900, () -> {
            capture("00-welcome");
            Minecraft minecraft = Minecraft.getInstance();
            check(minecraft.getLanguageManager().getSelected().equals("en_us"), "Initial gallery uses English");
            for (var entry : NativeOriginCatalog.entries(registries())) {
                Component name = ArchiveOriginModels.name(entry); float size = 18;
                while (size > 12 && minecraft.font.split(name, (int)(179 * 9 / size)).size() > 2) size -= .5f;
                check(minecraft.font.split(name, (int)(179 * 9 / size)).size() <= 2,
                        "Full English roster name fits two lines: " + name.getString());
            }
        });
        step("enter archive and reject repeated navigation", 50, () -> {
            key(257); Object motion = value(screen, "motion"); long began = ((Number)value(screen, "motionBegan")).longValue();
            for (int i = 0; i < 8; i++) key(257);
            check(value(screen, "motion") == motion && ((Number)value(screen, "motionBegan")).longValue() == began,
                    "Repeated keys do not restart arrival animation");
        });
        step("arrival midframe", 360, () -> capture("01-arrival"));
        step("desktop", 900, () -> {
            check(state("view").equals("DESKTOP") && value(screen, "motion") == null, "Arrival settles on desktop");
            capture("02-desktop");
        });
        step("open folder", 100, () -> key(257));
        step("open-folder travel", 430, () -> capture("03-open-travel"));
        step("open-folder hinge", 500, () -> capture("04-open-hinge"));
        step("human priority dossier", 750, () -> {
            check(state("view").equals("FILE") && value(screen, "motion") == null && pageValue("category") == 0, "Folder opens to human priority");
            capture("05-human");
        });
        step("human doll inputs", 100, () -> {
            var point = paperPoint(250, 320);
            screen.mouseClicked(point.x(), point.y(), 0);
            screen.mouseDragged(point.x() + 16, point.y() + 8, 0, 16, 8);
            screen.mouseReleased(point.x() + 16, point.y() + 8, 0);
            screen.mouseScrolled(point.x(), point.y(), 2);
            paperClick(250, 320, 1);
            check(((Number)value(screen, "yaw")).floatValue() != 0 && ((Number)value(screen, "pitch")).floatValue() != 0,
                    "Dragging changes both doll angles");
            check(((Number)value(screen, "zoom")).floatValue() > 1 && flag("previewCrouch"), "Wheel zoom and right-click crouch");
        });
        step("human crouched preview", 180, () -> capture("06-human-crouch"));
        step("human confirmation", 100, () -> { paperClick(380, 1040, 0); check(flag("confirmation"), "Human confirmation opens without sending packet"); });
        step("human confirmation image", 100, () -> capture("07-human-confirm"));
        step("dismiss confirmation", 100, () -> { key(256); check(!flag("confirmation"), "Escape dismisses confirmation"); });
        for (int category = 1; category <= 5; category++) {
            final int target = category;
            step("switch category " + target, 100, () -> {
                tab(target); Object turn = value(screen, "turn");
                check(turn != null, "Category turn starts: " + target);
                tab((target + 1) % 6); key(258); key(256);
                check(value(screen, "turn") == turn, "Inputs discarded while category sheet turns: " + target);
            });
            if (category == 1) {
                step("category turn near-start", 170, () -> curlCapture("08-category-curl-start"));
                step("category turn middle", 210, () -> curlCapture("09-category-curl-mid"));
            }
            step("roster " + target, 1000, () -> {
                check(pageValue("category") == target && value(screen, "turn") == null, "Category completes: " + target);
                capture("roster-" + target);
            });
            if (category == 3) {
                step("first page forward arrow", 100, () -> {
                    firstLeftColor = paperPixel(-600, 400);
                    paperClick(738, 1080, 0);
                    check(value(screen, "turn") != null, "Lowered forward arrow starts first-page turn");
                });
                step("first-page left stock remains", 170, () -> {
                    curlCapture("first-page-early-curl");
                    check(paperPixel(-600, 400) == firstLeftColor, "First-page curl retains colored left divider until moving paper covers it");
                });
                step("previous-page arrow", 850, () -> {
                    check(pageValue("number") == 1 && paperPixel(-600, 400) != firstLeftColor,
                            "Turned white sheet rests on left after forward turn completes");
                    paperClick(32, 1080, 0);
                    check(value(screen, "turn") != null, "Lowered previous arrow has a matching clickable area");
                });
                step("return to first roster page", 1000, () -> check(pageValue("number") == 0, "Previous arrow returns to first roster page"));
            }
        }
        step("page turn uses cursor wheel", 100, () -> {
            if (categoryEntries(5).size() > 12) {
                var point = paperPoint(380, 550); screen.mouseScrolled(point.x(), point.y(), -1);
                check(value(screen, "turn") != null, "Mouse wheel begins roster paging");
            }
        });
        step("second roster page", 1000, () -> {
            check(pageValue("number") == Math.min(1, categoryEntries(5).size() / 12), "Roster wheel advances one page");
            rememberedAquatic = pageValue("number");
            capture("roster-aquatic-page2");
        });
        step("change away from remembered page", 100, () -> tab(2));
        step("change back to remembered page", 1100, () -> tab(5));
        step("category page memory", 1100, () -> {
            check(pageValue("number") == rememberedAquatic && ((int[])value(screen, "savedPages"))[5] == rememberedAquatic,
                    "Category retains remembered roster page");
        });
        appendDetail("changed:form_dark_latex_wolf_pup", "pup");
        appendDetail("changed:form_latex_shark_feral", "feral-shark");
        appendDetail("changed:form_dark_latex_double_yufeng", "double-yufeng");
        appendDetail("changed:form_pure_white_latex_cerberus", "cerberus");
        appendDetail("changed:form_latex_snake", "snake");
        appendDetail("changed:form_pooltoy_wolf", "pooltoy");
        step("low-angle paper doll", 100, () -> {
            var point = paperPoint(250, 320);
            screen.mouseClicked(point.x(), point.y(), 0);
            screen.mouseDragged(point.x(), point.y() - 100, 0, 0, -100);
            screen.mouseReleased(point.x(), point.y() - 100, 0);
        });
        step("low-angle preview and confirmation", 200, () -> {
            capture("pooltoy-low-angle"); paperClick(380, 1040, 0);
            check(flag("confirmation"), "Angled creature confirmation opens");
        });
        step("confirmation above angled doll", 200, () -> { capture("pooltoy-low-angle-confirmation"); key(256); });
        appendDetail("changed:form_latex_snake", "snake-after-depth");
        step("GUI scale change", 100, () -> {
            Minecraft minecraft = Minecraft.getInstance(); minecraft.options.guiScale().set(3); minecraft.resizeDisplay();
            check(detail() != null && detail().form().toString().equals("changed:form_latex_snake"), "GUI resize retains selected dossier");
        });
        step("GUI scale screenshot", 250, () -> capture("snake-gui-scale3"));
        step("small window", 100, () -> Minecraft.getInstance().getWindow().setWindowed(854, 480));
        step("small window screenshot", 600, () -> {
            check(detail() != null && detail().form().toString().equals("changed:form_latex_snake"), "Window resize retains selected dossier");
            capture("snake-small-854x480");
        });
        step("restore window and GUI scale", 100, () -> {
            Minecraft minecraft = Minecraft.getInstance(); minecraft.getWindow().setWindowed(1280, 720);
            minecraft.options.guiScale().set(2); minecraft.resizeDisplay();
        });
        step("unavailable backend", 100, () -> {
            screen.receive(new NativeOriginStatePacket(0, UUID.randomUUID(), false, false, Component.empty(), NativeOriginCatalog.entries(registries())));
            paperClick(380, 1040, 0); check(!flag("confirmation"), "Native confirm disabled when TrueTransfur unavailable");
        });
        step("unavailable screenshot", 180, () -> capture("backend-unavailable"));
        step("no habitat error", 100, () -> screen.receive(new NativeOriginStatePacket(2, UUID.randomUUID(), true, false,
                Component.translatable("origin.changed_synergy.no_habitat"), NativeOriginCatalog.entries(registries()))));
        step("no habitat screenshot", 180, () -> capture("no-habitat-fallback"));
        step("server search status", 100, () -> {
            screen.receive(new NativeOriginStatePacket(1, UUID.randomUUID(), true, false, Component.translatable("origin.changed_synergy.searching"), NativeOriginCatalog.entries(registries())));
            Object paper = value(screen, "paper"); key(258); key(256); paperClick(250, 320, 1);
            check(value(screen, "paper").equals(paper), "Server-search phase blocks navigation");
        });
        step("server search screenshot", 200, () -> capture("server-search"));
        step("resource reload", 100, () -> {
            screen.receive(new NativeOriginStatePacket(0, UUID.randomUUID(), true, false, Component.empty(), NativeOriginCatalog.entries(registries())));
            reloadSettledFrames = 0;
            reload = Minecraft.getInstance().reloadResourcePacks();
        });
        step("after resource reload", 900, () -> {
            check(!reload.isCompletedExceptionally(), "Resource reload completes");
            check(detail() != null, "Resource reload retains dossier"); capture("after-resource-reload");
        });
        step("Chinese resource reload", 100, () -> {
            Minecraft minecraft = Minecraft.getInstance(); minecraft.getLanguageManager().setSelected("zh_cn");
            minecraft.options.languageCode = "zh_cn";
            reloadSettledFrames = 0;
            reload = minecraft.reloadResourcePacks();
        });
        step("Chinese dossier", 900, () -> {
            check(!reload.isCompletedExceptionally() && Component.translatable("origin.changed_synergy.archive.category.dark").getString().equals("黑胶"),
                    "Chinese UI loads translated archive category");
            capture("zh-snake-detail"); key(256);
        });
        for (int category = 0; category <= 5; category++) {
            final int target = category;
            step("Chinese category " + target, 1100, () -> tab(target));
            step("Chinese category screenshot " + target, 1100, () -> {
                check(pageValue("category") == target && value(screen, "turn") == null, "Chinese category visible: " + target);
                capture("zh-category-" + target);
            });
        }
        appendDetail("changed:form_dark_latex_wolf_pup", "zh-pup");
        step("prepare closing regression", 100, () -> key(256));
        step("variant closing category", 1100, () -> tab(3));
        step("variant second sheet", 1100, () -> {
            check(categoryEntries(3).size() > 12, "Closing regression has multiple variant sheets");
            seekRosterPage(1);
        });
        step("left fan pointer", 1100, () -> hoverPaper = new ArchiveGeometry.Point(-654, ArchiveGeometry.PAPER_H - 40));
        step("left protruding fan occludes binding", 450, () -> {
            check(((Number)value(screen, "fanLeft")).doubleValue() > .95
                    && ((Number)value(screen, "fanRight")).doubleValue() < .05, "Left pointer opens previous-sheet fan");
            check(!((List<?>)value(value(screen, "papers"), "liftedPaper")).isEmpty(), "Left fan registers lifted paper above binding");
            capture("zh-fan-left-binding"); hoverPaper = new ArchiveGeometry.Point(684, ArchiveGeometry.PAPER_H - 40);
        });
        step("right protruding fan occludes binding", 450, () -> {
            check(((Number)value(screen, "fanRight")).doubleValue() > .95
                    && ((Number)value(screen, "fanLeft")).doubleValue() < .05, "Right pointer opens next-sheet fan");
            check(!((List<?>)value(value(screen, "papers"), "liftedPaper")).isEmpty(), "Right fan registers lifted paper above binding");
            capture("zh-fan-right-binding"); hoverPaper = new ArchiveGeometry.Point(380, 600);
        });
        step("fans return to resting binding", 450, () -> {
            check(((Number)value(screen, "fanLeft")).doubleValue() < .05
                    && ((Number)value(screen, "fanRight")).doubleValue() < .05, "Pointer away collapses both sheet fans");
            check(((List<?>)value(value(screen, "papers"), "liftedPaper")).isEmpty(), "Resting sheets add no lifted paper over binding");
            capture("zh-fan-resting-binding"); hoverPaper = null;
        });
        step("escape gathers printed pages", 1100, () -> {
            check(pageValue("category") == 3 && pageValue("number") == 1 && detail() == null,
                    "Escape closes from variant page 2");
            capture("zh-close-variant-page2"); key(256);
            Object turn = value(screen, "turn"), destination = value(turn, "to");
            check(flag("closeAfterTurn") && ((Number)value(turn, "direction")).intValue() == -1
                    && ((Number)value(destination, "category")).intValue() == 0
                    && ((Number)value(destination, "number")).intValue() == 0,
                    "Close queues reverse gather to human page 0 before cover");
        });
        step("gather curl and locked repeated input", 350, () -> {
            curlCapture("zh-close-gather-curl-mid"); Object turn = value(screen, "turn");
            for (int i = 0; i < 5; i++) { key(256); key(257); key(258); paperClick(950, 500, 0); }
            check(value(screen, "turn") == turn && flag("closeAfterTurn") && value(screen, "motion") == null,
                    "Repeated inputs preserve queued gather without stale reopen or disconnect");
        });
        step("opaque cover midframe", 850, () -> {
            check(state("motion").equals("CLOSE") && value(screen, "turn") == null && pageValue("category") == 0
                    && !flag("closeAfterTurn") && ((Number)value(screen, "open")).doubleValue() > 0
                    && ((Number)value(screen, "open")).doubleValue() < 1,
                    "Gather completes before cover closes above printed human sheet");
            capture("zh-close-cover-mid"); Object motion = value(screen, "motion");
            long began = ((Number)value(screen, "motionBegan")).longValue();
            key(256); key(257); paperClick(950, 500, 0);
            check(value(screen, "motion") == motion && ((Number)value(screen, "motionBegan")).longValue() == began,
                    "Cover closing discards repeated inputs");
        });
        step("closed folder settles", 1300, () -> {
            check(state("view").equals("DESKTOP") && value(screen, "motion") == null && value(screen, "turn") == null
                    && !flag("closeAfterTurn"), "Queued close settles fully without stale intent");
            capture("zh-close-fully-closed"); key(257);
        });
        step("reopen after gather", 1600, () -> {
            check(state("view").equals("FILE") && pageValue("category") == 0 && pageValue("number") == 0
                    && value(screen, "motion") == null && !flag("closeAfterTurn") && !flag("confirmation"),
                    "Reopen returns to human priority without queued close");
            capture("zh-close-reopened"); tab(3);
        });
        step("outside click closes remembered second sheet", 1100, () -> {
            check(pageValue("category") == 3 && pageValue("number") == 1, "Closing preserves category page memory");
            paperClick(950, 500, 0);
            check(flag("closeAfterTurn") && value(screen, "turn") != null, "Outside click queues the same reverse gather");
        });
        step("outside close curl", 250, () -> curlCapture("zh-outside-close-gather-mid"));
        step("resize during pending gather", 100, () -> {
            check(flag("closeAfterTurn"), "Resize exercised while close is genuinely pending");
            Minecraft minecraft = Minecraft.getInstance(); minecraft.options.guiScale().set(3); minecraft.resizeDisplay();
            check(pageValue("category") == 0 && value(screen, "turn") == null && !flag("closeAfterTurn")
                    && state("motion").equals("CLOSE"), "Resize preserves pending close by completing gather before cover");
        });
        step("resized closing cover", 250, () -> capture("zh-close-resize-cover-mid"));
        step("resized close settled", 1300, () -> {
            check(state("view").equals("DESKTOP") && value(screen, "motion") == null && !flag("closeAfterTurn"),
                    "Resize during pending close reaches desktop");
            Minecraft minecraft = Minecraft.getInstance(); minecraft.options.guiScale().set(2); minecraft.resizeDisplay(); key(257);
        });
        step("reopen after resize", 1600, () -> {
            check(state("view").equals("FILE") && pageValue("category") == 0 && value(screen, "turn") == null
                    && value(screen, "motion") == null && !flag("closeAfterTurn"), "Resized close can reopen without stale state");
            capture("zh-close-resize-reopened");
        });
        step("rotated zoomed doll behind cover", 100, () -> {
            var point = paperPoint(250, 320);
            screen.mouseClicked(point.x(), point.y(), 0);
            screen.mouseDragged(point.x() + 30, point.y() - 10, 0, 30, -10);
            screen.mouseReleased(point.x() + 30, point.y() - 10, 0);
            screen.mouseScrolled(point.x(), point.y(), 2);
            check(((Number)value(screen, "yaw")).floatValue() != 0 && ((Number)value(screen, "pitch")).floatValue() != 0
                    && ((Number)value(screen, "zoom")).floatValue() > 1, "Cover lettering is exercised above a rotated zoomed doll");
        });
        step("close directly above adjusted doll", 200, () -> { capture("zh-cover-doll-before-close"); key(256); });
        step("front cover lettering over doll", 500, () -> {
            check(state("motion").equals("CLOSE") && ArchivePaperRenderer.coverInFront(((Number)value(screen, "open")).doubleValue())
                    && ((Number)value(screen, "zoom")).floatValue() > 1, "Front cover closes above preserved zoomed doll");
            capture("zh-cover-lettering-front-doll");
        });
        step("fully closed lettering above doll", 250, () -> capture("zh-cover-lettering-closed-doll"));
        step("direct close settles", 1200, () -> {
            check(state("view").equals("DESKTOP") && value(screen, "motion") == null, "Direct human cover close settles");
        });
        step("model attributes and renderer state", 100, ArchiveUiRegression::checkModels);
        step("optional re-selection Escape", 100, () -> {
            screen.receive(new NativeOriginStatePacket(0, UUID.randomUUID(), true, true, Component.empty(), NativeOriginCatalog.entries(registries())));
            key(256);
            check(Minecraft.getInstance().getConnection().getConnection().isConnected()
                    && ((NativeOriginStatePacket)value(screen, "state")).phase() == 1,
                    "Optional Escape requests cancellation without disconnecting the player");
            net.parkabird.changedsynergy.client.NativeOriginClient.receive(new NativeOriginStatePacket(3, UUID.randomUUID(), true, true,
                    Component.translatable("origin.changed_synergy.selection_cancelled"), List.of()));
            check(Minecraft.getInstance().screen == null && Minecraft.getInstance().level != null,
                    "Cancellation acknowledgement returns to the game");
        });
        step("lifecycle", 250, () -> {
            Minecraft minecraft = Minecraft.getInstance();
            var models = value(screen, "models"); var papers = value(screen, "papers");
            minecraft.setScreen(null);
            check(!ArchivePaperRenderer.isCapturing(), "Capture state cleared on removal");
            check(((java.util.Map<?,?>)value(models, "previews")).isEmpty() && value(papers, "snapshot") == null, "Screen removal destroys model and paper GPU caches");
            System.out.println("ARCHIVE_UI_REGRESSION_PASS: " + assertions + " assertions");
            finished = true;
            minecraft.stop();
        });
    }
    @SuppressWarnings("unchecked") private static List<NativeOriginCatalog.Entry> categoryEntries(int category) {
        return ((List<List<NativeOriginCatalog.Entry>>)value(screen, "categories")).get(category);
    }
    private static int number(String field) { return ((Number)value(screen, field)).intValue(); }
    private static void changeLanguage(String language) {
        var minecraft = Minecraft.getInstance(); minecraft.getLanguageManager().setSelected(language);
        minecraft.options.languageCode = language; reloadSettledFrames = 0; reload = minecraft.reloadResourcePacks();
    }
    private static net.minecraft.core.RegistryAccess registries() {
        return Minecraft.getInstance().getSingleplayerServer().registryAccess();
    }
    private static int targetCategory(String form) {
        return ArchiveOriginModels.categoryOf(NativeOriginCatalog.find(registries(), net.minecraft.resources.ResourceLocation.parse(form)));
    }
    private static int targetIndex(String form) {
        int index = categoryEntries(targetCategory(form)).indexOf(NativeOriginCatalog.find(registries(), net.minecraft.resources.ResourceLocation.parse(form)));
        return index < 0 ? -1 : index + 1;
    }
    private static void appendDetail(String form, String image) {
        step("prepare " + image + " category", 100, () -> {
            if (detail() != null) key(256);
        });
        step("open " + image + " category", 1000, () -> tab(targetCategory(form)));
        step("find " + image + " roster page", 1100, () -> seekPage(form));
        step("open " + image + " dossier", 1100, () -> {
            int index = targetIndex(form), cell = index % 12;
            check(index >= 0 && pageValue("number") == index / 12, "Target roster page: " + form);
            if (image.equals("double-yufeng") || image.equals("cerberus")) capture(image + "-main-head-roster");
            paperClick(44 + cell % 3 * 233 + 107, 122 + cell / 3 * 236 + 96, 0);
            check(value(screen, "turn") != null, "Detail page flip begins: " + form);
        });
        step(image + " detail curl", 350, () -> curlCapture(image + "-curl"));
        step(image + " dossier", 700, () -> {
            check(detail() != null && detail().form().toString().equals(form), "Correct model dossier: " + form); capture(image + "-detail");
        });
        step(image + " right-click crouch", 100, () -> {
            paperClick(250, 320, 1); check(flag("previewCrouch"), "Crouch input works for " + image);
            var point = paperPoint(250, 320); screen.mouseScrolled(point.x(), point.y(), 2);
        });
        step(image + " crouched image", 250, () -> capture(image + "-crouch"));
    }
    private static void seekPage(String form) {
        int index = targetIndex(form); check(index >= 0, "Catalog target exists: " + form);
        seekRosterPage(index / 12);
    }
    private static void seekRosterPage(int target) {
        int current = pageValue("number");
        if (target == current) return;
        key(target > current ? 262 : 263);
        steps.addFirst(new Step("continue finding roster page " + target, 1100, () -> seekRosterPage(target)));
    }

    private static void checkModels() {
        Minecraft minecraft = Minecraft.getInstance();
        var models = new ArchiveOriginModels();
        var entries = NativeOriginCatalog.entries(registries());
        check(entries.size() >= 65, "Catalog includes original species");
        check(ArchiveOriginModels.categoryOf(null) == 0, "Human priority category");
        check(ArchiveOriginModels.name(null).getString().equals(minecraft.player.getGameProfile().getName()), "Human page uses player ID");
        check(models.fields(null).stream().anyMatch(field -> field.label().getString().equals(Component.translatable("attribute.name.generic.max_health").getString())
                && field.value().getString().equals("20")), "Human health uses base player attributes");
        for (var entry : entries) check(models.fields(entry).size() >= 15, "Real model statistics: " + entry.form());
        var originals = new java.util.IdentityHashMap<net.minecraft.world.entity.LivingEntity, EntityState>();
        ((java.util.Map<?,?>)value(models, "previews")).values().forEach(preview -> {
            var entity = (net.minecraft.world.entity.LivingEntity)preview;
            originals.put(entity, EntityState.of(entity));
        });
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        boolean clipped = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        int[] oldBox = new int[4]; GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, oldBox);
        RenderSystem.enableScissor(18, 19, 600, 450);
        int[] expected = new int[4]; GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, expected);
        var camera = minecraft.getEntityRenderDispatcher().cameraOrientation().get(new org.joml.Quaternionf());
        boolean hitboxes = minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes();
        for (String match : List.of("form_dark_latex_wolf_pup", "form_latex_shark_feral", "form_latex_snake")) {
            var entry = entries.stream().filter(candidate -> candidate.form().getPath().contains(match)).findFirst().orElseThrow();
            models.renderPortrait(graphics, entry, 10, 10, 100, 120);
            models.renderDoll(graphics, entry, 130, 10, 220, 220, 30, 10, 1.2f, true);
            int[] actual = new int[4]; GL11.glGetIntegerv(GL11.GL_SCISSOR_BOX, actual);
            check(GL11.glIsEnabled(GL11.GL_SCISSOR_TEST) && java.util.Arrays.equals(expected, actual), "Preview restores nested clipping: " + match);
            check(camera.equals(minecraft.getEntityRenderDispatcher().cameraOrientation()), "Preview restores camera: " + match);
            check(hitboxes == minecraft.getEntityRenderDispatcher().shouldRenderHitBoxes(), "Preview restores hitboxes: " + match);
        }
        // These are screen-owned entities; drawing never inserts them into the live level.
        for (var entry : entries) models.renderPortrait(graphics, entry, 360, 10, 100, 120);
        var previews = (java.util.Map<?,?>)value(models, "previews");
        for (Object preview : previews.values()) {
            var entity = (net.minecraft.world.entity.LivingEntity)preview;
            check(EntityState.of(entity).equals(originals.get(entity)), "Preview restores entity state: " + entity.getType());
            check(minecraft.level.getEntity(entity.getId()) != entity, "Preview stays outside live world: " + entity.getType());
        }
        if (clipped) RenderSystem.enableScissor(oldBox[0], oldBox[1], oldBox[2], oldBox[3]); else RenderSystem.disableScissor();
        check(((java.util.Set<?>)value(models, "failed")).isEmpty(), "All model previews successful");
        RenderState before = RenderState.capture();
        var sheet = new ArchivePaperRenderer();
        sheet.capture(minecraft, captured -> {
            captured.fill(0, 0, 774, 1095, 0xFFFFFFFF);
            models.renderDoll(captured, entries.get(0), 44, 135, 415, 427, 20, 10, 1, true);
        });
        RenderState after = RenderState.capture();
        if (!before.equals(after)) System.err.println("ARCHIVE_RENDER_STATE_DIFF: before=" + before + "; after=" + after);
        check(before.equals(after) && !ArchivePaperRenderer.isCapturing(), "Paper capture restores framebuffer, viewport, clipping, projection and shader state");
        sheet.close();
        check(before.equals(RenderState.capture()), "Paper disposal restores framebuffer bindings");
        checkPreviewDepth(models);
        models.clear();
        check(((java.util.Map<?,?>)value(models, "previews")).isEmpty(), "Model resources clear");
    }
    private static void checkPreviewDepth(ArchiveOriginModels models) {
        Minecraft minecraft = Minecraft.getInstance();
        var entry = NativeOriginCatalog.find(registries(), net.minecraft.resources.ResourceLocation.parse("changed:form_pooltoy_wolf"));
        var paper = new ArchivePaperRenderer();
        try (var near = previewImage(paper, models, entry, 0, false);
             var far = previewImage(paper, models, entry, -2000, false);
             var overlay = previewImage(paper, models, entry, 0, true)) {
            int different = 0, body = 0, blockedOverlay = 0;
            int background = near.getPixelRGBA(50, 140), foreground = overlay.getPixelRGBA(50, 140);
            for (int x = 44; x < 459; x++) for (int y = 135; y < 562; y++) {
                if (near.getPixelRGBA(x, y) != far.getPixelRGBA(x, y)) different++;
                if (near.getPixelRGBA(x, y) != background) body++;
                if (overlay.getPixelRGBA(x, y) != foreground) blockedOverlay++;
            }
            check(body > 1000, "Low-angle pooltoy body is actually rendered");
            check(different == 0, "Flat background depth never cuts an angled paper doll");
            check(blockedOverlay == 0, "GUI overlay completely covers the model without model-depth holes");
        } finally { paper.close(); }
        var graphics = new GuiGraphics(minecraft, minecraft.renderBuffers().bufferSource());
        boolean oldMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        double oldClear = GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE);
        try {
            RenderSystem.depthMask(false); RenderSystem.clearDepth(.37);
            models.renderDoll(graphics, entry, 10, 10, 220, 220, 35, -50, 1, false);
            check(!GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK) && Math.abs(GL11.glGetDouble(GL11.GL_DEPTH_CLEAR_VALUE) - .37) < .00001,
                    "Preview depth isolation restores the caller's depth mask and clear value");
        } finally { RenderSystem.depthMask(oldMask); RenderSystem.clearDepth(oldClear); }
    }
    private static com.mojang.blaze3d.platform.NativeImage previewImage(ArchivePaperRenderer paper, ArchiveOriginModels models,
            NativeOriginCatalog.Entry entry, int backgroundDepth, boolean overlay) {
        Minecraft minecraft = Minecraft.getInstance();
        paper.capture(minecraft, graphics -> {
            graphics.pose().pushPose(); graphics.pose().translate(0, 0, backgroundDepth);
            graphics.fill(44, 135, 459, 562, 0xFFE6E4DE); graphics.pose().popPose();
            models.renderDoll(graphics, entry, 44, 135, 415, 427, 35, -50, 1, false);
            if (overlay) graphics.fill(44, 135, 459, 562, 0xFFFDFBF5);
        });
        return Screenshot.takeScreenshot((com.mojang.blaze3d.pipeline.RenderTarget)value(paper, "snapshot"));
    }
    private static void fail(Throwable failure) {
        finished = true;
        System.err.println("ARCHIVE_UI_REGRESSION_FAIL: " + failure);
        failure.printStackTrace();
        Minecraft.getInstance().stop();
    }
}
