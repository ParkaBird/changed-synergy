package net.parkabird.changedsynergy.client;

import java.util.concurrent.CompletableFuture;
import net.ltxprogrammer.changed.entity.ChangedEntity;
import net.ltxprogrammer.changed.init.ChangedAbilities;
import net.ltxprogrammer.changed.process.ProcessTransfur;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.parkabird.changedsynergy.ai.BondedSuitService;
import net.parkabird.changedsynergy.entity.MuntjacJumpService;
import net.parkabird.changedsynergy.init.ChangedSynergyEntities;
import net.parkabird.changedsynergy.init.ChangedSynergyGameRules;

/** Actual input, client physics, server movement packets and the rendered horse HUD. */
@Mod.EventBusSubscriber(modid = "changed_synergy", value = Dist.CLIENT)
public final class MuntjacJumpClientRegression {
    private static int stage, age, scenario, checks, releaseAge;
    private static CompletableFuture<Void> task;
    private static ChangedEntity wrapper;
    private static double peak, normalPeak;
    private static volatile double serverPeak;
    private static boolean releaseRequested, released, captured;
    private static boolean creativeVerification;
    private static long began = System.nanoTime();

    @SubscribeEvent public static void tick(TickEvent.ClientTickEvent event) {
        if (stage == 99) return;
        var mc = Minecraft.getInstance();
        try {
            mc.setWindowActive(true);
            mc.options.pauseOnLostFocus = false;
            if (System.nanoTime() - began > 170_000_000_000L) throw new AssertionError("client timeout stage=" + stage);
            if (event.phase == TickEvent.Phase.START) {
                if (mc.player != null) {
                    boolean jump = stage == 4 && age % 20 < 2 || stage == 6 && !releaseRequested
                            || (stage == 12 || stage == 14) && (age == 1 || age == 4);
                    mc.options.keyJump.setDown(jump);
                }
                return;
            }
            if (stage == 0 && mc.screen instanceof TitleScreen && mc.getOverlay() == null) {
                check(mc.gameDirectory.getName().startsWith("author-feedback-client-"), "private test directory");
                org.lwjgl.glfw.GLFW.glfwHideWindow(mc.getWindow().getWindow());
                mc.options.renderDistance().set(2); mc.options.simulationDistance().set(2);
                var rules = new GameRules();
                rules.getRule(ChangedSynergyGameRules.NATIVE_ORIGINS).set(false, null);
                rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
                mc.createWorldOpenFlows().createFreshLevel("muntjac-jump", new LevelSettings("muntjac-jump", GameType.SURVIVAL,
                        false, Difficulty.PEACEFUL, true, rules, WorldDataConfiguration.DEFAULT), new WorldOptions(31415, false, false),
                        r -> r.registryOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).createWorldDimensions());
                stage = 1;
            }
            if (mc.player == null || mc.level == null || mc.getOverlay() != null || mc.player.tickCount < 30) return;
            if (stage == 1) {
                task = CompletableFuture.runAsync(() -> prepare(mc), mc.getSingleplayerServer());
                stage = 2; age = 0;
            } else if (stage == 2) {
                if (!task.isDone()) return;
                task.join();
                if (!mc.player.onGround() || Math.abs(mc.player.getY() - 80) > .05 || !MuntjacJumpService.refresh(mc.player)) {
                    if (++age > 200) throw new AssertionError("native/suit sync timeout " + scenario);
                    return;
                }
                mc.setScreen(null); peak = serverPeak = 0; age = 0;
                stage = scenario == 0 ? 4 : 6;
                releaseRequested = released = false;
            } else if (stage == 4) {
                peak = Math.max(peak, mc.player.getY() - 80);
                check(!MuntjacJumpService.state(mc.player).charging(), "short ordinary taps do not charge");
                if (++age >= 60 && mc.player.onGround()) {
                    normalPeak = peak;
                    check(normalPeak > .5, "ordinary physical jump occurred");
                    stage = 6; age = 0; peak = serverPeak = 0;
                }
            } else if (stage == 6) {
                var state = MuntjacJumpService.state(mc.player);
                if (!releaseRequested && age >= 2) check(Math.abs(mc.player.getY() - 80) < .05, "long press stays grounded until release");
                if (!releaseRequested && state.charging() && state.charge(mc.player.tickCount) >= .999F) releaseRequested = true;
                if (releaseRequested && !state.charging() && !released) { released = true; releaseAge = age; }
                if (released) peak = Math.max(peak, mc.player.getY() - 80);
                if (++age > 160) throw new AssertionError("charge/landing timed out " + scenario);
                if (released && age - releaseAge > 25 && mc.player.onGround()) {
                    check(peak > normalPeak + 1.2, "charged physical jump is higher, scenario " + scenario + ": " + peak);
                    check(serverPeak > normalPeak + 1.2, "server observed charged jump, scenario " + scenario + ": " + serverPeak);
                    check(Math.abs(peak - serverPeak) < .65, "predicted/server height agrees");
                    check(!state.charging() && state.multiplier(mc.player.tickCount) == 1, "charge consumed after release");
                    System.out.println("MUNTJAC_JUMP_PHYSICS scenario=" + scenario + " normalHeight=" + normalPeak + " chargedHeight=" + peak + " serverHeight=" + serverPeak);
                    if (creativeVerification) {
                        check(captured, "actual charging HUD was rendered"); stage = 99;
                        System.out.println("MUNTJAC_CLIENT_REGRESSION_PASS: " + checks + " native/suit charging, creative double-tap flight start/stop, creative charge retention, server motion and HUD checks");
                        mc.stop();
                    } else if (++scenario < 4) stage = 1;
                    else stage = 10;
                }
            } else if (stage == 10) {
                scenario=0;
                task=CompletableFuture.runAsync(()->{prepare(mc);mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()).setGameMode(GameType.CREATIVE);},mc.getSingleplayerServer());
                stage=11;age=0;
            } else if (stage == 11) {
                if(!task.isDone())return;task.join();
                if(!mc.player.getAbilities().mayfly || !mc.player.onGround() || !MuntjacJumpService.refresh(mc.player)) {
                    if(++age>120)throw new AssertionError("creative form sync");return;
                }
                mc.setScreen(null);stage=12;age=0;
            } else if (stage == 12) {
                if(++age==12){check(mc.player.getAbilities().flying,"creative double-tap starts flight");stage=13;age=0;}
            } else if (stage == 13) {
                if(++age>=20){stage=14;age=0;}
            } else if (stage == 14) {
                if(++age==12){check(!mc.player.getAbilities().flying,"creative double-tap stops flight");stage=15;age=0;}
            } else if (stage == 15) {
                if(++age>160)throw new AssertionError("creative landing");
                if(age>=20 && mc.player.onGround()) {
                    creativeVerification=true;stage=6;age=0;peak=serverPeak=0;releaseRequested=released=false;
                }
            }
        } catch (Throwable failure) {
            stage = 99; System.err.println("MUNTJAC_CLIENT_REGRESSION_FAIL"); failure.printStackTrace(); mc.stop();
        }
    }

    private static void prepare(Minecraft mc) {
        var server = mc.getSingleplayerServer(); var level = server.overworld();
        var player = server.getPlayerList().getPlayer(mc.player.getUUID());
        if (wrapper != null) { wrapper.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get()).releaseEntity(false); wrapper.discard(); wrapper = null; }
        ProcessTransfur.removePlayerTransfurVariant(player);
        player.setGameMode(GameType.SURVIVAL); player.setHealth(20); player.getFoodData().setFoodLevel(20);
        for (int x = 3; x <= 13; x++) for (int z = 3; z <= 13; z++)
            level.setBlock(new BlockPos(x, 79, z), Blocks.STONE.defaultBlockState(), 18);
        player.teleportTo(8.5, 80, 8.5);
        var type = scenario % 2 == 0 ? ChangedSynergyEntities.LATEX_MUNTJAC.get() : ChangedSynergyEntities.LATEX_MUNTJAC_FEMALE.get();
        var body = type.create(level);
        if (scenario < 2) ProcessTransfur.setPlayerTransfurVariant(player, body.getSelfVariant(), null, 1, false);
        else {
            body.moveTo(8.5, 80, 8.5); body.setNoAi(true); level.addFreshEntity(body);
            BondedSuitService.ensureGrabAbility(body);
            var grab = body.getAbilityInstance(ChangedAbilities.GRAB_ENTITY_ABILITY.get());
            check(grab.suitEntity(player), "integrated native suit wraps human");
            ((net.ltxprogrammer.changed.entity.LivingEntityDataExtension)player).setGrabbedBy(body);
            grab.grabbedHasControl = true; wrapper = body;
            BondedSuitService.syncOwnerSuitState(body, player, true);
        }
    }

    @SubscribeEvent public static void serverTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof net.minecraft.server.level.ServerPlayer && (stage == 4 || stage == 6))
            serverPeak = Math.max(serverPeak, event.player.getY() - 80);
    }

    @SubscribeEvent public static void rendered(RenderGuiEvent.Post event) {
        var mc = Minecraft.getInstance();
        if (captured || stage != 6 || mc.player == null || !MuntjacJumpService.charging(mc.player)
                || MuntjacJumpService.state(mc.player).charge(mc.player.tickCount) < .9F) return;
        net.minecraft.client.Screenshot.grab(mc.gameDirectory, "muntjac-charge-hud.png", mc.getMainRenderTarget(), message -> {});
        captured = true;
    }
    private static void check(boolean ok, String message) { if (!ok) throw new AssertionError(message); checks++; }
}
