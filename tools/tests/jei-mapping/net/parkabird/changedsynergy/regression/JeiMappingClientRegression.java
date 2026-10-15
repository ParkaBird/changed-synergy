package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.CraftingScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.InvokeDynamicInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.Handle;
import org.lwjgl.glfw.GLFW;

/** Real release JEI, real world entry and rendered vanilla/JEI screens. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class JeiMappingClientRegression {
    private static int stage, ticks;
    private static boolean finished;
    private static net.minecraft.world.phys.Vec3 walkStart;
    private static final long START = System.nanoTime();

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("changed_synergy.jeiRegression") || finished
                || event.phase != TickEvent.Phase.END) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() - START > 240_000_000_000L)
                throw new AssertionError("JEI client regression timed out");
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                Path directory = mc.gameDirectory.toPath().toAbsolutePath().normalize();
                if (!directory.getParent().getFileName().toString().equals(".codex-tmp")
                        || !directory.getFileName().toString().startsWith("author-feedback-client-"))
                    throw new AssertionError("Not an isolated regression game directory");
                mc.options.pauseOnLostFocus = false;
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                var rules = new GameRules();
                rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                var settings = new LevelSettings("jei-regression", GameType.SURVIVAL, false,
                        Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("jei-regression", settings,
                        new WorldOptions(73451L, false, false), registry -> registry
                                .registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                                .createWorldDimensions());
                stage = 1;
            }
            if (mc.level == null || mc.player == null || !mc.hasSingleplayerServer()) return;
            if (++ticks < (stage == 7 ? 120 : 40)) return;
            ticks = 0;
            if (stage > 1 && mc.screen != null) exerciseInput(mc);
            if (stage == 1) {
                checkInterfaceImplementations();
                var serverChecks = new java.util.concurrent.CompletableFuture<Void>();
                mc.getSingleplayerServer().execute(() -> {
                    try { TradeDoorRegression.run(mc.getSingleplayerServer()); serverChecks.complete(null); }
                    catch (Throwable failure) { serverChecks.completeExceptionally(failure); }
                });
                serverChecks.get(45, java.util.concurrent.TimeUnit.SECONDS);
                mc.setScreen(new InventoryScreen(mc.player));
            } else if (stage == 2) {
                checkItemArea(mc);
                mc.setScreen(new CraftingScreen(new CraftingMenu(1, mc.player.getInventory()),
                        mc.player.getInventory(), Component.literal("JEI crafting regression")));
            } else if (stage == 3) {
                mc.setScreen(new ContainerScreen(ChestMenu.threeRows(2, mc.player.getInventory()),
                        mc.player.getInventory(), Component.literal("JEI container regression")));
            } else if (stage == 4 && ModList.get().isLoaded("jei")) {
                Object runtime = Class.forName("mezz.jei.common.Internal").getMethod("getJeiRuntime").invoke(null);
                Object recipes = Class.forName("mezz.jei.api.runtime.IJeiRuntime")
                        .getMethod("getRecipesGui").invoke(runtime);
                Object crafting = Class.forName("mezz.jei.api.constants.RecipeTypes").getField("CRAFTING").get(null);
                Class.forName("mezz.jei.api.runtime.IRecipesGui").getMethod("showTypes", List.class)
                        .invoke(recipes, List.of(crafting));
                if (!mc.screen.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"))
                    throw new AssertionError("JEI recipe screen did not open");
                checkBothNames(mc, runtime, recipes);
            } else if (stage == 4 || stage == 5) {
                mc.setScreen(new ChatScreen(""));
                stage = 5;
            } else if (stage == 6) {
                mc.setScreen(null);
                walkStart = mc.player.position();
                mc.options.keyUp.setDown(true);
            } else if (stage == 7) {
                mc.options.keyUp.setDown(false);
                if (mc.player.position().distanceToSqr(walkStart) < 1)
                    throw new AssertionError("Movement input did not move the player");
                String version = ModList.get().getModContainerById("jei")
                        .map(mod -> mod.getModInfo().getVersion().toString()).orElse("absent");
                System.out.println("JEI_MAPPING_CLIENT_REGRESSION_PASS: JEI=" + version
                        + "; world entry, walking, chat, keyboard, mouse, scrolling, inventory, crafting, container"
                        + (ModList.get().isLoaded("jei") ? ", JEI recipes" : ""));
                finished = true;
                mc.stop();
                return;
            }
            System.out.println("JEI_MAPPING_SCREEN_PASS: stage=" + stage);
            stage++;
        } catch (Throwable failure) {
            System.err.println("JEI_MAPPING_CLIENT_REGRESSION_FAIL");
            failure.printStackTrace();
            mc.options.keyUp.setDown(false);
            finished = true;
            mc.stop();
        }
    }

    private static void checkInterfaceImplementations() throws Exception {
        if (!ModList.get().isLoaded("jei")) return;
        Class<?> contract = Class.forName("mezz.jei.common.input.IMouseOverable");
        int found = 0;
        Set<String> checkedCalls = new HashSet<>();
        Map<String, ClassNode> classes = new HashMap<>();
        Path jar = ModList.get().getModFileById("jei").getFile().getFilePath();
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : zip.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(zip.getInputStream(entry)).accept(node, 0);
                classes.put(node.name, node);
                if (node.methods.stream().anyMatch(m -> java.util.stream.StreamSupport.stream(
                        m.instructions.spliterator(), false).anyMatch(i -> i instanceof InvokeDynamicInsnNode f
                                && f.desc.endsWith(")Lmezz/jei/common/input/IMouseOverable;"))))
                    Class.forName(node.name.replace('/', '.'), false, JeiMappingClientRegression.class.getClassLoader());
                if ((node.access & (Modifier.ABSTRACT | Modifier.INTERFACE)) != 0
                        || node.methods.stream().noneMatch(m -> m.name.equals("m_5953_") && m.desc.equals("(DD)Z"))) continue;
                Class<?> type = Class.forName(node.name.replace('/', '.'), false,
                        JeiMappingClientRegression.class.getClassLoader());
                if (!contract.isAssignableFrom(type)) continue;
                var implementation = type.getMethod("isMouseOver", double.class, double.class);
                if (Modifier.isAbstract(implementation.getModifiers()))
                    throw new AssertionError("Missing concrete interface implementation: " + node.name);
                found++;
            }
        }
        String version = ModList.get().getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString();
        if (version.equals("15.70.0.221") && found != 10)
            throw new AssertionError("Expected all 10 affected JEI implementations, found " + found);
        // Read actual Mixin exports instead of reflectively resolving optional
        // config-GUI signatures whose dependencies need not be installed.
        Path exports = Minecraft.getInstance().gameDirectory.toPath().resolve(".mixin.out/class");
        for (String name : List.copyOf(classes.keySet())) {
            Path file = exports.resolve(name + ".class");
            if (!Files.isRegularFile(file)) continue;
            ClassNode transformed = new ClassNode();
            new ClassReader(Files.readAllBytes(file)).accept(transformed, 0);
            classes.put(name, transformed);
        }
        if (classes.get("mezz/jei/common/input/IMouseOverable").methods.stream()
                .noneMatch(m -> m.name.equals("m_5953_") && m.desc.equals("(DD)Z")))
            throw new AssertionError("Actual exported JEI interface alias missing");
        int factories = 0;
        for (ClassNode node : List.copyOf(classes.values())) {
            for (var method : node.methods) for (var instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode call)
                    checkJeiMember(classes, call.owner, call.name, call.desc, false, checkedCalls);
                if (instruction instanceof FieldInsnNode field)
                    checkJeiMember(classes, field.owner, field.name, field.desc, true, checkedCalls);
                if (instruction instanceof InvokeDynamicInsnNode dynamic) {
                    if (dynamic.desc.endsWith(")Lmezz/jei/common/input/IMouseOverable;")) {
                        if (!dynamic.name.equals("isMouseOver"))
                            throw new AssertionError("Invalid JEI lambda SAM: " + node.name + '.' + dynamic.name);
                        factories++;
                    }
                    for (Object argument : dynamic.bsmArgs) if (argument instanceof Handle handle)
                        checkJeiMember(classes, handle.getOwner(), handle.getName(), handle.getDesc(), handle.getTag() <= 4, checkedCalls);
                }
            }
        }
        System.out.println("JEI_MAPPING_INTERFACE_PASS: " + found + " actual release implementations");
        System.out.println("JEI_INTERNAL_MEMBER_LINKAGE_PASS: " + checkedCalls.size() + " unique method/field references");
        if (version.equals("15.70.0.221") && factories != 31)
            throw new AssertionError("Expected 31 real JEI mouse lambda factories, found " + factories);
        System.out.println("JEI_LAMBDA_SAM_PASS: " + factories + " actual release factories");
    }

    private static void checkItemArea(Minecraft mc) throws Exception {
        if (!ModList.get().isLoaded("jei")) return;
        Class<?> internal = Class.forName("mezz.jei.common.Internal");
        Object runtime = internal.getMethod("getJeiRuntime").invoke(null);
        Object overlay = Class.forName("mezz.jei.api.runtime.IJeiRuntime")
                .getMethod("getIngredientListOverlay").invoke(runtime);
        var source = Class.forName("mezz.jei.gui.input.IRecipeFocusSource");
        var hover = source.getMethod("getIngredientUnderMouse", double.class, double.class);
        double x = -1, y = -1;
        for (int px = 0; px < mc.screen.width && x < 0; px += 5)
            for (int py = 15; py < mc.screen.height - 25; py += 5) {
                try (var stream = (java.util.stream.Stream<?>)hover.invoke(overlay, (double)px, (double)py)) {
                    if (stream.findFirst().isPresent()) { x = px; y = py; break; }
                }
            }
        if (x < 0) throw new AssertionError("No visible JEI ingredient slot");
        Object toggle = internal.getMethod("getClientToggleState").invoke(null);
        var setCheat = Class.forName("mezz.jei.common.config.IClientToggleState")
                .getMethod("setCheatItemsEnabled", boolean.class);
        setCheat.invoke(toggle, true);
        try {
            mc.player.containerMenu.setCarried(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND, 7));
            ForgeHooksClient.onScreenMouseClickedPre(mc.screen, x, y, 0);
            MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseDragged.Pre(mc.screen, x + 1, y + 1, 0, 1, 1));
            ForgeHooksClient.onScreenMouseReleasedPre(mc.screen, x, y, 0);
            if (!mc.player.containerMenu.getCarried().isEmpty())
                throw new AssertionError("JEI item-area delete did not consume the carried stack");
            ForgeHooksClient.onScreenMouseClickedPre(mc.screen, x, y, 0);
            ForgeHooksClient.onScreenMouseReleasedPre(mc.screen, x, y, 0);
            System.out.println("JEI_ITEM_AREA_INTERACTION_PASS: carried item, click, drag, release and empty-cursor click");
        } finally { mc.player.containerMenu.setCarried(net.minecraft.world.item.ItemStack.EMPTY); setCheat.invoke(toggle, false); }
    }

    private static void checkJeiMember(Map<String, ClassNode> classes, String owner, String name, String descriptor,
            boolean field, Set<String> checked) throws Exception {
        if (!owner.startsWith("mezz/jei/") || !checked.add(owner + '.' + name + descriptor)) return;
        int result = resolves(classes, owner, name, descriptor, field, new HashSet<>());
        if (result == 1) return;
        if (result == -1) {
            System.out.println("JEI_OPTIONAL_EXTERNAL_PARENT_UNVERIFIED: " + owner + '.' + name + descriptor);
            return;
        }
        throw new AssertionError("Unresolved JEI binary reference: " + owner + '.' + name + descriptor);
    }

    private static int resolves(Map<String, ClassNode> classes, String owner, String name, String descriptor,
            boolean field, Set<String> seen) throws Exception {
        if (owner == null || !seen.add(owner)) return 0;
        ClassNode node = classes.get(owner);
        if (node == null) {
            try (var input = JeiMappingClientRegression.class.getClassLoader().getResourceAsStream(owner + ".class")) {
                if (input == null) return -1;
                node = new ClassNode();
                new ClassReader(input).accept(node, ClassReader.SKIP_CODE);
                classes.put(owner, node);
            }
        }
        if (field && node.fields.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))) return 1;
        if (!field && node.methods.stream().anyMatch(m -> m.name.equals(name) && m.desc.equals(descriptor))) return 1;
        if (name.equals("<init>")) return 0;
        int result = resolves(classes, node.superName, name, descriptor, field, seen);
        if (result == 1) return 1;
        for (String parent : node.interfaces) {
            int inherited = resolves(classes, parent, name, descriptor, field, seen);
            if (inherited == 1) return 1;
            if (inherited == -1) result = -1;
        }
        return result;
    }

    private static void checkBothNames(Minecraft mc, Object runtime, Object recipes) throws Exception {
        Class<?> contract = Class.forName("mezz.jei.common.input.IMouseOverable");
        var normal = contract.getMethod("isMouseOver", double.class, double.class);
        var mapped = contract.getMethod("m_5953_", double.class, double.class);
        Object overlay = Class.forName("mezz.jei.api.runtime.IJeiRuntime")
                .getMethod("getIngredientListOverlay").invoke(runtime);
        int checked = 0;
        for (Object target : List.of(overlay, recipes)) for (double[] point : new double[][] {
                {5, 5}, {mc.screen.width - 10, 50}, {mc.screen.width - 90, mc.screen.height - 15}}) {
            // Older JEI screens do not all implement the new shared contract.
            if (!contract.isInstance(target)) continue;
            if (!normal.invoke(target, point[0], point[1]).equals(mapped.invoke(target, point[0], point[1])))
                throw new AssertionError("Mouse hit-test aliases disagree: " + target.getClass());
            checked++;
        }
        if (ModList.get().getModContainerById("jei").orElseThrow().getModInfo().getVersion().toString()
                .equals("15.70.0.221") && checked != 6)
            throw new AssertionError("New JEI overlay and recipe objects must both implement the contract");
        System.out.println("JEI_BIDIRECTIONAL_MAPPING_PASS: " + checked + " real-object coordinate comparisons");
    }

    private static void exerciseInput(Minecraft mc) {
        var screen = mc.screen;
        if (!ForgeHooksClient.onScreenKeyPressedPre(screen, GLFW.GLFW_KEY_W, 0, 0)) {
            if (!screen.keyPressed(GLFW.GLFW_KEY_W, 0, 0))
                ForgeHooksClient.onScreenKeyPressedPost(screen, GLFW.GLFW_KEY_W, 0, 0);
        }
        ForgeHooksClient.onScreenKeyReleasedPre(screen, GLFW.GLFW_KEY_W, 0, 0);
        if (!ForgeHooksClient.onScreenCharTypedPre(screen, 'a', 0)) {
            screen.charTyped('a', 0);
            ForgeHooksClient.onScreenCharTypedPost(screen, 'a', 0);
        }
        for (double[] point : new double[][] {{5, 5}, {screen.width - 10, screen.height - 15}}) {
            screen.mouseMoved(point[0], point[1]);
            ForgeHooksClient.onScreenMouseClickedPre(screen, point[0], point[1], 0);
            ForgeHooksClient.onScreenMouseReleasedPre(screen, point[0], point[1], 0);
            MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseScrolled.Pre(screen, point[0], point[1], 1));
            MinecraftForge.EVENT_BUS.post(new ScreenEvent.MouseScrolled.Pre(screen, point[0], point[1], -1));
        }
        System.out.println("JEI_INPUT_EVENTS_PASS: " + screen.getClass().getSimpleName());
    }
}
