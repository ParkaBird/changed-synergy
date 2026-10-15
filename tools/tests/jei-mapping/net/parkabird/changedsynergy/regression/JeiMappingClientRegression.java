package net.parkabird.changedsynergy.regression;

import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipFile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
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
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;

/** Real release JEI, real world entry and rendered vanilla/JEI screens. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class JeiMappingClientRegression {
    private static int stage, ticks;
    private static boolean finished;
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
                var settings = new LevelSettings("jei-regression", GameType.CREATIVE, false,
                        Difficulty.NORMAL, true, rules, WorldDataConfiguration.DEFAULT);
                mc.createWorldOpenFlows().createFreshLevel("jei-regression", settings,
                        new WorldOptions(73451L, false, false), registry -> registry
                                .registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT)
                                .createWorldDimensions());
                stage = 1;
            }
            if (mc.level == null || mc.player == null || !mc.hasSingleplayerServer()) return;
            if (++ticks < 40) return;
            ticks = 0;
            if (stage == 1) {
                checkInterfaceImplementations();
                mc.setScreen(new InventoryScreen(mc.player));
            } else if (stage == 2) {
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
            } else if (stage >= 5 || stage == 4) {
                String version = ModList.get().getModContainerById("jei")
                        .map(mod -> mod.getModInfo().getVersion().toString()).orElse("absent");
                System.out.println("JEI_MAPPING_CLIENT_REGRESSION_PASS: JEI=" + version
                        + "; real world entry, inventory, crafting, container and recipe screens");
                finished = true;
                mc.stop();
                return;
            }
            System.out.println("JEI_MAPPING_SCREEN_PASS: stage=" + stage);
            stage++;
        } catch (Throwable failure) {
            System.err.println("JEI_MAPPING_CLIENT_REGRESSION_FAIL");
            failure.printStackTrace();
            finished = true;
            mc.stop();
        }
    }

    private static void checkInterfaceImplementations() throws Exception {
        if (!ModList.get().isLoaded("jei")) return;
        Class<?> contract = Class.forName("mezz.jei.common.input.IMouseOverable");
        int found = 0;
        Path jar = ModList.get().getModFileById("jei").getFile().getFilePath();
        try (var zip = new ZipFile(jar.toFile())) {
            for (var entry : zip.stream().filter(e -> e.getName().endsWith(".class")).toList()) {
                ClassNode node = new ClassNode();
                new ClassReader(zip.getInputStream(entry)).accept(node, ClassReader.SKIP_CODE);
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
        System.out.println("JEI_MAPPING_INTERFACE_PASS: " + found + " actual release implementations");
    }
}
